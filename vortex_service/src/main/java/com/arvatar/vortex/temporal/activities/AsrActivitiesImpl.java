package com.arvatar.vortex.temporal.activities;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import com.arvatar.vortex.models.AsrPcdJob;
import com.arvatar.vortex.models.JobStatus;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.arvatar.vortex.service.DatabaseWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@Component
public class AsrActivitiesImpl implements AsrActivities {
    public static class ChunkWithEmbedding {
        public final String chunk;
        public final float[] embedding;
        public ChunkWithEmbedding(String chunk, float[] embedding) {
            this.chunk = chunk;
            this.embedding = embedding;
        }
    }

    private final ObjectMapper objectMapper;
    private final MinIOS3Client objectStoreClient;
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> asyncCommands;
    private final Logger logger = LoggerFactory.getLogger(AsrActivitiesImpl.class);
    private final DatabaseWriter databaseWriter;

    public AsrActivitiesImpl(ObjectProvider<DatabaseWriter> databaseWriterProvider) {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectStoreClient = new MinIOS3Client();
        this.redisClient = RedisClient.create("redis://localhost:6379");
        this.connection = redisClient.connect();
        this.asyncCommands = connection.async();
        this.databaseWriter = databaseWriterProvider.getIfAvailable();
        if (this.databaseWriter == null) {
            logger.warn("Database writer is not configured; embeddings will not be persisted.");
        }
    }

    @Override
    public void executeAsrJob(AsrPcdJob job) {
        try {
            job.status = JobStatus.ASR_STARTED;
            objectStoreClient.updateJob(job);
            byte[] videoPayload = objectStoreClient.getVideo(job.videoKey);
            JsonNode json = transcribeVisemes(job.guruId, videoPayload, job.guruId, job.videoKey);
            job.asrResultJsonString = json.toString();
            job.status = JobStatus.ASR_COMPLETED;
            objectStoreClient.updateJob(job);
            String txnId = publishJobToStream(job);
            logger.info("Transcription job completed for guruId: {} pcd job txn_id: {}", job.guruId, txnId);
        } catch (Exception e) {
            job.status = JobStatus.ASR_FAILED;
            objectStoreClient.updateJob(job);
            logger.error("ASR job failed for guruId: {} pcd job {}", job.guruId, job.jobId, e);
            throw new RuntimeException("ASR job failed", e);
        }
    }

    private JsonNode transcribeVisemes(String jobId, byte[] videoPayload, String guruId, String videoKey) throws IOException {
        Path tempDir = Files.createTempDirectory("asr" + jobId);
        Path videoFile = tempDir.resolve(jobId + ".mp4");
        Path audioFile = tempDir.resolve(jobId + ".wav");
        Path jsonFile = tempDir.resolve(jobId + ".json");
        try {
            Files.write(videoFile, videoPayload);
            runOrThrow(new ProcessBuilder(
                    "ffmpeg", "-y", "-i", videoFile.toString(),
                    "-vn", "-ac", "1", "-ar", "16000", "-acodec", "pcm_s16le", audioFile.toString()
            ).redirectErrorStream(true));
            runOrThrow(new ProcessBuilder(
                    "rhubarb", "-f", "json", "-o", jsonFile.toString(), audioFile.toString()
            ));
            String transcript = transcribeAudio(audioFile);
            List<ChunkWithEmbedding> chunksWithEmbeddings = getEmbeddingsVectors(transcript);
            writeToPgVectorDatabase(guruId, videoKey, chunksWithEmbeddings);
            try (InputStream reader = Files.newInputStream(jsonFile)) {
                return objectMapper.readTree(reader);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try { Files.deleteIfExists(videoFile); } catch (Exception ignore) {}
            try { Files.deleteIfExists(audioFile); } catch (Exception ignore) {}
            try { Files.deleteIfExists(jsonFile); } catch (Exception ignore) {}
            try { Files.deleteIfExists(tempDir); } catch (Exception ignore) {}
        }
    }

    private void runOrThrow(ProcessBuilder pb) throws Exception {
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            while (r.readLine() != null) {}
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", pb.command()));
        }
    }

    private String publishJobToStream(AsrPcdJob job) throws ExecutionException, InterruptedException, IOException {
        String pcdJobRedisStream = "pcd_jobs";
        String payload = objectMapper.writeValueAsString(job);
        return asyncCommands.xadd(pcdJobRedisStream, Map.of("job", payload)).get();
    }

    private String transcribeAudio(Path audioFile) throws IOException {
        try {
            Criteria<Path, String> criteria = Criteria.builder()
                    .setTypes(Path.class, String.class)
                    .optEngine("Pytorch")
                    .optModelUrls("djl://ai.djl.huggingface/speech-recognition/openai/whisper-small")
                    .build();
            try (ZooModel<Path, String> model = ModelZoo.loadModel(criteria);
                 Predictor<Path, String> predictor = model.newPredictor()) {
                return predictor.predict(audioFile);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<ChunkWithEmbedding> getEmbeddingsVectors(String transcript){
        List<ChunkWithEmbedding> chunksWithEmbeddings = new ArrayList<>();
        try{
            String[] chunks = transcript.split("(?<=\\G.{500})");
            Criteria<String, float[]> embedCriteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelUrls("djl://ai.djl.huggingface/text-embedding/sentence-transformers/all-MiniLM-L6-v2")
                    .optEngine("Pytorch")
                    .build();
            try (ZooModel<String, float[]> embedModel = ModelZoo.loadModel(embedCriteria);
                 Predictor<String, float[]> embedPredictor = embedModel.newPredictor()) {
                for (String chunk : chunks) {
                    float[] embedding = embedPredictor.predict(chunk);
                    chunksWithEmbeddings.add(new ChunkWithEmbedding(chunk, embedding));
                }
            }
            return chunksWithEmbeddings;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    private void writeToPgVectorDatabase(String guruId, String videoKey, List<ChunkWithEmbedding> chunksWithEmbeddings) {
        if (databaseWriter == null) {
            logger.debug("Skipping pgvector persistence for guruId {} because no database writer is configured.", guruId);
            return;
        }
        try {
            databaseWriter.insertPersonIfNotExists(guruId, guruId);
            logger.info("Ensured person record exists for guruId: {}", guruId);
            UUID videoId = databaseWriter.insertVideo(guruId, "ASR Generated Video", videoKey);
            logger.info("Created video record for videoKey: {} with videoId: {}", videoKey, videoId);
            for (ChunkWithEmbedding chunkWithEmbedding : chunksWithEmbeddings) {
                UUID chunkId = databaseWriter.insertChunk(
                    guruId, 
                    videoId, 
                    chunkWithEmbedding.chunk, 
                    true,
                    chunkWithEmbedding.embedding
                );
                logger.debug("Inserted chunk with ID: {} for guruId: {}", chunkId, guruId);
            }
            logger.info("Successfully stored {} chunks with embeddings for guruId: {}", chunksWithEmbeddings.size(), guruId);
        } catch (SQLException e) {
            logger.error("Failed to write embeddings to database for guruId: {}", guruId, e);
            throw new RuntimeException("Database write failed", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            connection.close();
        } finally {
            redisClient.shutdown();
        }
    }
}

