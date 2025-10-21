package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.dto.JobStatus;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PcdService {
    private final MinIOS3Client objectStoreClient;
    private RedisAsyncCommands<String, String> asyncCommands;
    private final ObjectMapper objectMapper;
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(PcdService.class);
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();

    PcdService(){
        objectMapper = new ObjectMapper();
        objectStoreClient = new MinIOS3Client();
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");
        StatefulRedisConnection<String, String> connection = redisClient.connect();
        asyncCommands = connection.async();
    }

    @PostConstruct
    public void start(){
        workerExecutor.submit(this::run);
    }

    private void run(){
        String pcdJobRedisStream = "pcd_jobs";
        String pcdJobRedisStreamGroup = "pcd_jobs_workers";
        String pcdJobRedisStreamConsumer = "pcd_jobs_consumer";
        while(!Thread.currentThread().isInterrupted()) {
            try {
                List<StreamMessage<String,String>> jobs = asyncCommands.xreadgroup(
                        Consumer.from(pcdJobRedisStreamGroup, pcdJobRedisStreamConsumer),
                        XReadArgs.Builder.block(Duration.ofSeconds(5)),
                        XReadArgs.StreamOffset.lastConsumed(pcdJobRedisStream)
                ).get();
                if(jobs == null || jobs.isEmpty()) continue;
                for(StreamMessage<String,String> message : jobs) {
                    String messageId = message.getId();
                    Map<String, String> jobEntry = message.getBody();
                    String job = jobEntry.get("job");
                    AsrPcdJob asrPcdJob = objectMapper.readValue(job, AsrPcdJob.class);
                    asrPcdJob.status = JobStatus.PCD_STARTED;
                    try {
                        objectStoreClient.updateJob(asrPcdJob);
                        JsonNode transcription = objectMapper.readTree(asrPcdJob.asrResultJsonString);
                        Map<String, List<JsonNode>> visemeAudioBoundariesMap = getVisemeBounds(transcription);
                        Map<String, List<Path>> visemeSnippetFileMap = visemeAudioSnipFileMap(asrPcdJob.guruId, objectStoreClient.getVideo(asrPcdJob.videoKey), visemeAudioBoundariesMap);
                        Map<String, Path> visemeExtractedFramesMap = extractFrameByFrameFromSnippet(asrPcdJob.guruId, visemeSnippetFileMap);
                        createAndPublishPcdFromFrames(asrPcdJob.guruId, visemeExtractedFramesMap);
                        asrPcdJob.status = JobStatus.PCD_COMPLETED;
                        objectStoreClient.updateJob(asrPcdJob);
                        asyncCommands.xack(pcdJobRedisStream, pcdJobRedisStreamGroup, messageId).get();
                        asyncCommands.xdel(pcdJobRedisStream, messageId).get();
                        logger.info("Pcd job completed for guruId: {} pcd job {}", asrPcdJob.guruId, asrPcdJob.jobId);
                    } catch (Exception processingException) {
                        asrPcdJob.status = JobStatus.PCD_FAILED;
                        objectStoreClient.updateJob(asrPcdJob);
                        asyncCommands.xack(pcdJobRedisStream, pcdJobRedisStreamGroup, messageId).get();
                        asyncCommands.xdel(pcdJobRedisStream, messageId).get();
                        logger.error("PCD job failed for guruId: {} pcd job {} with exception {}", asrPcdJob.guruId, asrPcdJob.jobId, processingException.getMessage());
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                logger.error("ASR stream processing failed, attempting to recover", e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdownNow();
    }

    private Map<String, List<JsonNode>> getVisemeBounds(JsonNode transcription){
        JsonNode mouthCues = transcription.get("mouthCues");
        Map<String, List<JsonNode>> visemeAudioTrackMap = new HashMap<>();
        for(JsonNode viseme: mouthCues){
            String visemeId = viseme.get("value").asText();
            if(!visemeAudioTrackMap.containsKey(visemeId)){
                visemeAudioTrackMap.put(visemeId, new ArrayList<>());
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("start", viseme.get("start").floatValue());
            node.put("end", viseme.get("end").floatValue());
            visemeAudioTrackMap.get(visemeId).add(node);
        }
        return visemeAudioTrackMap;
    }

    private Map<String, List<Path>> visemeAudioSnipFileMap(String guruId, byte [] payload, Map<String, List<JsonNode>> visemeAudioTrackMap) throws IOException, InterruptedException {
        Path videoDir = Files.createTempDirectory(guruId);
        Path videoFile = videoDir.resolve(Instant.now().toEpochMilli() +"mp4");
        Files.write(videoFile, payload);
        Map<String, List<Path>> visemeSnippetFileMap = new HashMap<>();
        try {
            for (Map.Entry<String, List<JsonNode>> entry : visemeAudioTrackMap.entrySet()) {
                String visemeId = entry.getKey();
                List<JsonNode> boundaries = entry.getValue();
                int index = 0;
                for (JsonNode boundary : boundaries) {
                    float start = boundary.get("start").floatValue();
                    float end = boundary.get("end").floatValue();
                    double duration = end - start;
                    if (duration < 0.05) continue;

                    String fileName = visemeId + "_" + index++ + ".mp4";
                    Path snipFile = videoDir.resolve(fileName);

                    ProcessBuilder processBuilder = new ProcessBuilder(
                            "ffmpeg", "-y", "-i", videoFile.toString(), "-ss", String.valueOf(start), "-t", String.valueOf(duration), "-c", "copy", snipFile.toString()
                    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    Process process = processBuilder.start();
                    process.waitFor();
                    if(!visemeSnippetFileMap.containsKey(visemeId)) visemeSnippetFileMap.put(visemeId, new ArrayList<>());
                    visemeSnippetFileMap.get(visemeId).add(snipFile);
                }
            }
            return visemeSnippetFileMap;
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private Map<String, Path> extractFrameByFrameFromSnippet(String guruId, Map<String, List<Path>> visemeSnippetFileMap) throws IOException, InterruptedException {
        Map<String, Path> visemeFrameDirMap = new HashMap<>();
        for(Map.Entry<String, List<Path>> entry : visemeSnippetFileMap.entrySet()){
            String visemeId = entry.getKey();
            List<Path> snippetFiles = entry.getValue();
            Path outputDir = Files.createTempDirectory(guruId + "_frames");
            Path visemeFrameDir = Files.createDirectories(outputDir.resolve(visemeId));
            int index = 0;
            for(Path file : snippetFiles){
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "ffmpeg", "-y", "-i", file.toString(), "-vf", "fps=5", "-q:v", "2",
                        visemeFrameDir + "/frame" + index++ + "_%04d.jpg"
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process process = processBuilder.start();
                process.waitFor();
            }
            visemeFrameDirMap.put(visemeId, visemeFrameDir);
        }
        return visemeFrameDirMap;
    }

    private void createAndPublishPcdFromFrames(String guruId, Map<String, Path> visemeFrameDirMap) throws IOException, InterruptedException {
        try {
            String finalFusedPlyFilePath = guruId + "/dense/fused.ply";
            for (Map.Entry<String, Path> entry : visemeFrameDirMap.entrySet()) {
                String visemeId = entry.getKey();
                Path visemeFrameDir = entry.getValue();
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "colmap", "automatic_reconstructor",
                        "--workspace_path", guruId,
                        "--image_path", visemeFrameDir.toString(),
                        "--dense", "true"
                );
                processBuilder.inheritIO();
                Process process = processBuilder.start();
                process.waitFor();
                Path finalFusedPlyFile = Paths.get(finalFusedPlyFilePath);
                Path s3FinalPcFile = finalFusedPlyFile.resolveSibling(visemeId + ".ply");
                Files.move(finalFusedPlyFile, s3FinalPcFile, StandardCopyOption.REPLACE_EXISTING);
                objectStoreClient.updateGuruAssetInventory(guruId, s3FinalPcFile);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try { Files.deleteIfExists(Paths.get(guruId)); } catch (Exception ignore) {}
        }
    }
}
