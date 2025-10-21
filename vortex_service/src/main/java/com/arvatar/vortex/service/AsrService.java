package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.dto.JobStatus;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Files;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AsrService {
    private final MinIOS3Client objectStoreClient;
    private RedisAsyncCommands<String, String> asyncCommands;
    private final RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private final ObjectMapper objectMapper;
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(AsrService.class);
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();

    public AsrService() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectStoreClient = new MinIOS3Client();
        redisClient = RedisClient.create("redis://localhost:6379");
        connection = redisClient.connect();
        asyncCommands = connection.async();
    }

    @PostConstruct
    public void start() {
        workerExecutor.submit(this::run);
    }

    private void run(){
        String asrJobRedisStream = "asr_jobs";
        String asrJobRedisStreamGroup = "asr_jobs_workers";
        String asrJobRedisStreamConsumer = "asr_jobs_consumer";
        while(!Thread.currentThread().isInterrupted()) {
            try {
                List<StreamMessage<String, String>> jobs = asyncCommands.xreadgroup(
                        Consumer.from(asrJobRedisStreamGroup, asrJobRedisStreamConsumer),
                        XReadArgs.Builder.block(Duration.ofSeconds(5)),
                        XReadArgs.StreamOffset.lastConsumed(asrJobRedisStream)
                ).get();
                if (jobs == null || jobs.isEmpty()) {
                    continue;
                }
                for (StreamMessage<String, String> message : jobs) {
                    String messageId = message.getId();
                    Map<String, String> jobEntry = message.getBody();
                        String job = jobEntry.get("job");
                        AsrPcdJob asrPcdJob = objectMapper.readValue(job, AsrPcdJob.class);
                        asrPcdJob.status = JobStatus.ASR_STARTED;
                    try {
                        objectStoreClient.updateJob(asrPcdJob);
                        String guruId = asrPcdJob.guruId;
                        String videoKey = asrPcdJob.videoKey;
                        byte[] videoPayload = objectStoreClient.getVideo(videoKey);
                        JsonNode json = transcribe(guruId, videoPayload);
                        asrPcdJob.asrResultJsonString = json.toString();
                        asrPcdJob.status = JobStatus.ASR_COMPLETED;
                        objectStoreClient.updateJob(asrPcdJob);
                        String txn_id = publishJobToStream(asrPcdJob);
                        asyncCommands.xack(asrJobRedisStream, asrJobRedisStreamGroup, messageId).get();
                        asyncCommands.xdel(asrJobRedisStream, messageId).get();
                        logger.info("Transcription job completed for guruId: {} pcd job txn_id: {}", guruId, txn_id);
                    } catch (Exception processingException) {
                        asrPcdJob.status = JobStatus.ASR_FAILED;
                        objectStoreClient.updateJob(asrPcdJob);
                        asyncCommands.xack(asrJobRedisStream, asrJobRedisStreamGroup, messageId).get();
                        asyncCommands.xdel(asrJobRedisStream, messageId).get();
                        logger.error("ASR job failed for guruId: {} pcd job {}, with exception {}", asrPcdJob.guruId, asrPcdJob.jobId, processingException.getMessage());
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                logger.error("ASR stream processing failed, attempting to recover", e);
                rebuildRedisConnection();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdownNow();
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private synchronized void rebuildRedisConnection() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception closeException) {
            logger.warn("Failed to close existing Redis connection during rebuild", closeException);
        }
        try {
            connection = redisClient.connect();
            asyncCommands = connection.async();
        } catch (Exception connectionException) {
            logger.error("Failed to rebuild Redis connection", connectionException);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private JsonNode transcribe(String jobId, byte[] videoPayload) throws IOException {
        Path tempDir = Files.createTempDirectory("asr"+ jobId);
        Path videoFile = tempDir.resolve(jobId + ".mp4");
        Path audioFile = tempDir.resolve(jobId + ".wav");
        Path jsonFile = tempDir.resolve(jobId + ".json");
        try {
            Files.write(videoFile, videoPayload);
            runOrThrow(
                    new ProcessBuilder(
                    "ffmpeg", "-y", "-i", videoFile.toString(),
                            "-vn", "-ac", "1", "-ar", "16000", "-acodec", "pcm_s16le", audioFile.toString()
                    ).redirectErrorStream(true)
            );
            runOrThrow(
                    new ProcessBuilder(
                            "rhubarb", "-f", "json", "-o", jsonFile.toString(), audioFile.toString()
                    )
            );
            try(InputStream reader = Files.newInputStream(jsonFile)){
                return objectMapper.readTree(reader);
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }finally {
            try { Files.deleteIfExists(videoFile); } catch (Exception ignore) {}
            try { Files.deleteIfExists(audioFile);   } catch (Exception ignore) {}
            try { Files.deleteIfExists(jsonFile);  } catch (Exception ignore) {}
            try { Files.deleteIfExists(tempDir);    } catch (Exception ignore) {}
        }
    }

    private static void runOrThrow(ProcessBuilder pb) throws Exception {
    Process p = pb.start();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      while (r.readLine() != null) {}
    }
    int code = p.waitFor();
    if (code != 0) throw new RuntimeException("Command failed: " + String.join(" ", pb.command()));
  }

  private String publishJobToStream(AsrPcdJob job){
        String pcdJobRedisStream = "pcd_jobs";
        try {
            String payload = objectMapper.writeValueAsString(job);
            return awaitXAdd(pcdJobRedisStream, payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize PCD job", e);
        }
    }

    private String awaitXAdd(String stream, String payload) {
        try {
            return asyncCommands.xadd(stream, Map.of("job", payload)).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing job to Redis", ie);
        } catch (java.util.concurrent.ExecutionException ee) {
            throw new RuntimeException("Failed to publish job to Redis", ee.getCause());
        }
    }
}
