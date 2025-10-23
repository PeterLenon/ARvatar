package com.arvatar.vortex.temporal.activities;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.dto.JobStatus;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class AsrActivitiesImpl implements AsrActivities {

    private final ObjectMapper objectMapper;
    private final MinIOS3Client objectStoreClient;
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> asyncCommands;
    private final Logger logger = LoggerFactory.getLogger(AsrActivitiesImpl.class);

    public AsrActivitiesImpl() {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectStoreClient = new MinIOS3Client();
        this.redisClient = RedisClient.create("redis://localhost:6379");
        this.connection = redisClient.connect();
        this.asyncCommands = connection.async();
    }

    @Override
    public void executeAsrJob(AsrPcdJob job) {
        try {
            job.status = JobStatus.ASR_STARTED;
            objectStoreClient.updateJob(job);

            byte[] videoPayload = objectStoreClient.getVideo(job.videoKey);
            JsonNode json = transcribe(job.guruId, videoPayload);

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

    private JsonNode transcribe(String jobId, byte[] videoPayload) throws IOException {
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
            while (r.readLine() != null) {
                // consume stream
            }
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

    @PreDestroy
    public void shutdown() {
        try {
            connection.close();
        } finally {
            redisClient.shutdown();
        }
    }
}

