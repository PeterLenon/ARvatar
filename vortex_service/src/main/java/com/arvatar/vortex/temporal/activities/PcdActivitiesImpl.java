package com.arvatar.vortex.temporal.activities;

import com.arvatar.vortex.models.AsrPcdJob;
import com.arvatar.vortex.models.JobStatus;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PcdActivitiesImpl implements PcdActivities {

    private final MinIOS3Client objectStoreClient;
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(PcdActivitiesImpl.class);

    public PcdActivitiesImpl() {
        this.objectStoreClient = new MinIOS3Client();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void executePcdJob(AsrPcdJob job) {
        job.status = JobStatus.PCD_STARTED;
        objectStoreClient.updateJob(job);
        try {
            JsonNode transcription = objectMapper.readTree(job.asrResultJsonString);
            Map<String, List<JsonNode>> visemeAudioBoundariesMap = getVisemeBounds(transcription);
            Map<String, List<Path>> visemeSnippetFileMap = visemeAudioSnipFileMap(job.guruId,
                    objectStoreClient.getVideo(job.videoKey), visemeAudioBoundariesMap);
            Map<String, Path> visemeExtractedFramesMap = extractFrameByFrameFromSnippet(job.guruId, visemeSnippetFileMap);
            createAndPublishPcdFromFrames(job.guruId, visemeExtractedFramesMap);
            job.status = JobStatus.PCD_COMPLETED;
            objectStoreClient.updateJob(job);
            logger.info("PCD job completed for guruId: {} pcd job {}", job.guruId, job.jobId);
        } catch (Exception e) {
            job.status = JobStatus.PCD_FAILED;
            objectStoreClient.updateJob(job);
            logger.error("PCD job failed for guruId: {} pcd job {}", job.guruId, job.jobId, e);
            throw new RuntimeException("PCD job failed", e);
        }
    }

    private Map<String, List<JsonNode>> getVisemeBounds(JsonNode transcription) {
        JsonNode mouthCues = transcription.get("mouthCues");
        Map<String, List<JsonNode>> visemeAudioTrackMap = new HashMap<>();
        for (JsonNode viseme : mouthCues) {
            String visemeId = viseme.get("value").asText();
            visemeAudioTrackMap.computeIfAbsent(visemeId, key -> new ArrayList<>());
            ObjectNode node = objectMapper.createObjectNode();
            node.put("start", viseme.get("start").floatValue());
            node.put("end", viseme.get("end").floatValue());
            visemeAudioTrackMap.get(visemeId).add(node);
        }
        return visemeAudioTrackMap;
    }

    private Map<String, List<Path>> visemeAudioSnipFileMap(String guruId, byte[] payload,
                                                            Map<String, List<JsonNode>> visemeAudioTrackMap)
            throws IOException, InterruptedException {
        Path videoDir = Files.createTempDirectory(guruId);
        Path videoFile = videoDir.resolve(Instant.now().toEpochMilli() + "mp4");
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
                    if (duration < 0.05) {
                        continue;
                    }

                    String fileName = visemeId + "_" + index++ + ".mp4";
                    Path snipFile = videoDir.resolve(fileName);

                    ProcessBuilder processBuilder = new ProcessBuilder(
                            "ffmpeg", "-y", "-i", videoFile.toString(), "-ss", String.valueOf(start),
                            "-t", String.valueOf(duration), "-c", "copy", snipFile.toString()
                    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    Process process = processBuilder.start();
                    process.waitFor();
                    visemeSnippetFileMap.computeIfAbsent(visemeId, key -> new ArrayList<>()).add(snipFile);
                }
            }
            return visemeSnippetFileMap;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Path> extractFrameByFrameFromSnippet(String guruId,
                                                             Map<String, List<Path>> visemeSnippetFileMap)
            throws IOException, InterruptedException {
        Map<String, Path> visemeFrameDirMap = new HashMap<>();
        for (Map.Entry<String, List<Path>> entry : visemeSnippetFileMap.entrySet()) {
            String visemeId = entry.getKey();
            List<Path> snippetFiles = entry.getValue();
            Path outputDir = Files.createTempDirectory(guruId + "_frames");
            Path visemeFrameDir = Files.createDirectories(outputDir.resolve(visemeId));
            int index = 0;
            for (Path file : snippetFiles) {
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

    private void createAndPublishPcdFromFrames(String guruId, Map<String, Path> visemeFrameDirMap)
            throws IOException, InterruptedException {
        try {
            String finalFusedPlyFilePath = guruId + "/dense/fused.ply";
            List<String> extractedVisemeIds = new ArrayList<>();
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
                extractedVisemeIds.add(visemeId);
                objectStoreClient.updateGuruAssetInventory(guruId, s3FinalPcFile);
            }
            objectStoreClient.updateGuruAssetInventory(guruId, extractedVisemeIds);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                deleteRecursively(Paths.get(guruId));
            } catch (Exception ignore) {
                // ignore cleanup failures
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

