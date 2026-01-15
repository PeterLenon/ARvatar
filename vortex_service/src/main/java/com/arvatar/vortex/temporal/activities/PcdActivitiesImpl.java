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

    /**
     * Checks if CUDA is available on the system for GPU-accelerated point cloud generation.
     * This is informational only - we will attempt CPU-based dense reconstruction regardless.
     * 
     * @return true if CUDA is available, false otherwise
     */
    private boolean isCudaAvailable() {
        try {
            ProcessBuilder nvidiaSmiBuilder = new ProcessBuilder("nvidia-smi", "--query-gpu=name", "--format=csv,noheader");
            nvidiaSmiBuilder.redirectErrorStream(true);
            Process nvidiaSmiProcess = nvidiaSmiBuilder.start();
            int exitCode = nvidiaSmiProcess.waitFor();
            
            if (exitCode == 0) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(nvidiaSmiProcess.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // nvidia-smi not available or failed
        }
        return false;
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

    /**
     * Groups mouthCues by viseme value (A, B, C, etc.) to create one PCD per unique viseme.
     * All occurrences of the same viseme are combined to extract frames from all time segments,
     * which are then used to create a single PCD for that viseme type.
     * 
     * @param transcription JSON containing mouthCues array
     * @return Map of viseme ID to list of time boundaries for that viseme
     */
    private Map<String, List<JsonNode>> getVisemeBounds(JsonNode transcription) {
        JsonNode mouthCues = transcription.get("mouthCues");
        Map<String, List<JsonNode>> visemeAudioTrackMap = new HashMap<>();
        int totalMouthCues = 0;
        for (JsonNode viseme : mouthCues) {
            totalMouthCues++;
            String visemeId = viseme.get("value").asText();
            visemeAudioTrackMap.computeIfAbsent(visemeId, key -> new ArrayList<>());
            ObjectNode node = objectMapper.createObjectNode();
            node.put("start", viseme.get("start").floatValue());
            node.put("end", viseme.get("end").floatValue());
            visemeAudioTrackMap.get(visemeId).add(node);
        }
        logger.info("Processed {} total mouthCues, grouped into {} unique viseme types: {}", 
            totalMouthCues, visemeAudioTrackMap.size(), visemeAudioTrackMap.keySet());
        for (Map.Entry<String, List<JsonNode>> entry : visemeAudioTrackMap.entrySet()) {
            logger.info("Viseme {} has {} occurrences", entry.getKey(), entry.getValue().size());
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
                int skippedCount = 0;
                for (JsonNode boundary : boundaries) {
                    float start = boundary.get("start").floatValue();
                    float end = boundary.get("end").floatValue();
                    double duration = end - start;
                    if (duration < 0.025) {
                        skippedCount++;
                        continue;
                    }

                    String fileName = visemeId + "_" + index++ + ".mp4";
                    Path snipFile = videoDir.resolve(fileName);

                    ProcessBuilder processBuilder = new ProcessBuilder(
                            "ffmpeg", "-y", "-i", videoFile.toAbsolutePath().toString(), "-ss", String.valueOf(start),
                            "-t", String.valueOf(duration), "-c", "copy", snipFile.toString()
                    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    Process process = processBuilder.start();
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        visemeSnippetFileMap.computeIfAbsent(visemeId, key -> new ArrayList<>()).add(snipFile);
                    } else {
                        logger.warn("Failed to create snippet for viseme {} at time {}-{}", visemeId, start, end);
                    }
                }
                if (skippedCount > 0) {
                    logger.info("Skipped {} short duration snippet(s) (<0.05s) for viseme {}", skippedCount, visemeId);
                }
                int snippetCount = visemeSnippetFileMap.getOrDefault(visemeId, new ArrayList<>()).size();
                if (snippetCount == 0) {
                    logger.warn("Viseme {} has no valid snippets after filtering. All {} occurrence(s) were too short.", 
                        visemeId, boundaries.size());
                } else {
                    logger.info("Created {} snippet(s) for viseme {}", snippetCount, visemeId);
                }
            }
            logger.info("Created snippets for {} unique viseme(s) out of {} total viseme types", 
                visemeSnippetFileMap.size(), visemeAudioTrackMap.size());
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
            logger.info("Extracting frames for viseme {} from {} snippet(s)", visemeId, snippetFiles.size());
            int snippetIndex = 0;
            int successfulExtractions = 0;
            for (Path file : snippetFiles) {
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "ffmpeg", "-y", "-i", file.toAbsolutePath().toString(), "-vf", "fps=10", "-q:v", "2",
                        visemeFrameDir + "/snippet" + snippetIndex + "_frame_%04d.jpg"
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    successfulExtractions++;
                    final int currentSnippetIndex = snippetIndex;
                    try {
                        long frameCount = Files.list(visemeFrameDir)
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().startsWith("snippet" + currentSnippetIndex + "_frame_"))
                            .count();
                        logger.debug("Extracted {} frame(s) from snippet {} for viseme {}", frameCount, currentSnippetIndex, visemeId);
                    } catch (IOException e) {
                        logger.debug("Could not count frames for snippet {} of viseme {}", currentSnippetIndex, visemeId);
                    }
                } else {
                    logger.warn("Frame extraction from snippet {} for viseme {} failed with exit code {}", 
                        snippetIndex, visemeId, exitCode);
                }
                snippetIndex++;
            }
            if (successfulExtractions == 0) {
                logger.error("Failed to extract frames from any snippet for viseme {}", visemeId);
            } else {
                try {
                    long totalFrames = Files.list(visemeFrameDir)
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
                        })
                        .count();
                    logger.info("Extracted {} total frame(s) for viseme {} from {} successful snippet(s)", 
                        totalFrames, visemeId, successfulExtractions);
                } catch (IOException e) {
                    logger.warn("Could not count total frames for viseme {}", visemeId, e);
                }
            }
            visemeFrameDirMap.put(visemeId, visemeFrameDir);
        }
        logger.info("Frame extraction completed for {} viseme(s)", visemeFrameDirMap.size());
        return visemeFrameDirMap;
    }

    private void createAndPublishPcdFromFrames(String guruId, Map<String, Path> visemeFrameDirMap) throws IOException, InterruptedException {
        Path workspaceBaseDir = Paths.get(System.getProperty("java.io.tmpdir"), guruId);
        try {
            Files.createDirectories(workspaceBaseDir);
            List<String> extractedVisemeIds = new ArrayList<>();
            List<String> skippedVisemeIds = new ArrayList<>();
            logger.info("Starting PCD creation for {} unique viseme(s): {}", 
                visemeFrameDirMap.size(), visemeFrameDirMap.keySet());
            
            // Check CUDA availability for informational purposes
            boolean cudaAvailable = isCudaAvailable();
            if (cudaAvailable) {
                logger.info("CUDA detected - Dense reconstruction will use GPU acceleration");
            } else {
                logger.info("CUDA not available - Dense reconstruction will use CPU (slower but will produce detailed point clouds)");
            }
            
            for (Map.Entry<String, Path> entry : visemeFrameDirMap.entrySet()) {
                String visemeId = entry.getKey();
                Path visemeFrameDir = entry.getValue();
                Path visemeWorkspaceDir = workspaceBaseDir.resolve(visemeId);
                Files.createDirectories(visemeWorkspaceDir);
                
                String workspacePath = visemeWorkspaceDir.toAbsolutePath().toString();
                logger.info("Processing viseme {} - Running colmap with workspace: {}", visemeId, workspacePath);

                long imageCount = 0;
                try {
                    imageCount = Files.list(visemeFrameDir)
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
                        })
                        .count();
                } catch (IOException e) {
                    logger.warn("Could not count images in {} for viseme {}", visemeFrameDir, visemeId, e);
                }
                
                if (imageCount < 3) {
                    logger.warn("Skipping viseme {}: Not enough images ({}) for Colmap reconstruction. " +
                        "Colmap requires at least 3 images. This viseme may have had too few or too short mouthCue occurrences.", 
                        visemeId, imageCount);
                    skippedVisemeIds.add(visemeId);
                    continue;
                }
                logger.info("Viseme {} has {} images, proceeding with PCD creation", visemeId, imageCount);
                logger.info("Step 1: Running sparse reconstruction with {} images...", imageCount);
                ProcessBuilder sparseBuilder = new ProcessBuilder(
                        "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
                        "colmap", "automatic_reconstructor",
                        "--workspace_path", workspacePath,
                        "--image_path", visemeFrameDir.toAbsolutePath().toString()
                );
                sparseBuilder.environment().put("QT_QPA_PLATFORM", "offscreen");
                sparseBuilder.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                sparseBuilder.environment().put("GALLIUM_DRIVER", "llvmpipe");
                java.io.File sparseLogFile = new java.io.File(visemeWorkspaceDir.toFile(), "sparse_reconstruction.log");
                sparseBuilder.redirectErrorStream(true);
                sparseBuilder.redirectOutput(sparseLogFile);
                Process sparseProcess = sparseBuilder.start();
                int sparseExitCode = sparseProcess.waitFor();
                if (sparseExitCode != 0) {
                    String errorDetails = "";
                    try {
                        errorDetails = new String(Files.readAllBytes(sparseLogFile.toPath()));
                        logger.error("Colmap sparse reconstruction log:\n{}", errorDetails);
                    } catch (IOException e) {
                        logger.warn("Could not read sparse reconstruction log", e);
                    }
                    logger.error("Colmap sparse reconstruction failed for viseme {} with exit code: {}", visemeId, sparseExitCode);
                    throw new RuntimeException("Colmap sparse reconstruction failed with exit code: " + sparseExitCode);
                }
                logger.info("Step 2: Validating sparse reconstruction output...");
                Path sparseModelPath = visemeWorkspaceDir.resolve("sparse").resolve("0");
                if (!Files.exists(sparseModelPath)) {
                    logger.error("Sparse model directory does not exist: {}", sparseModelPath);
                    continue;
                }
                Path camerasBin = sparseModelPath.resolve("cameras.bin");
                Path imagesBin = sparseModelPath.resolve("images.bin");
                Path points3DBin = sparseModelPath.resolve("points3D.bin");
                
                boolean hasRequiredFiles = Files.exists(camerasBin) && Files.exists(imagesBin) && Files.exists(points3DBin);
                if (!hasRequiredFiles) {
                    logger.warn("Sparse model missing required files. Found: cameras.bin={}, images.bin={}, points3D.bin={}", 
                        Files.exists(camerasBin), Files.exists(imagesBin), Files.exists(points3DBin));
                    logger.warn("Sparse reconstruction may have failed. Attempting to export sparse point cloud as fallback...");
                    
                    // Fallback to sparse export
                    Path sparsePlyPath = visemeWorkspaceDir.resolve("sparse_points.ply");
                    ProcessBuilder exportBuilder = new ProcessBuilder(
                            "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
                            "colmap", "model_converter",
                            "--input_path", sparseModelPath.toAbsolutePath().toString(),
                            "--output_path", sparsePlyPath.toAbsolutePath().toString(),
                            "--output_type", "PLY"
                    );
                    exportBuilder.environment().put("QT_QPA_PLATFORM", "offscreen");
                    exportBuilder.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                    exportBuilder.environment().put("GALLIUM_DRIVER", "llvmpipe");
                    exportBuilder.inheritIO();
                    Process exportProcess = exportBuilder.start();
                    int exportExitCode = exportProcess.waitFor();
                    
                    if (exportExitCode == 0 && Files.exists(sparsePlyPath)) {
                        Path s3FinalPcFile = workspaceBaseDir.resolve(visemeId + ".ply");
                        Files.move(sparsePlyPath, s3FinalPcFile, StandardCopyOption.REPLACE_EXISTING);
                        logger.info("Successfully exported sparse point cloud as fallback for viseme {}", visemeId);
                        extractedVisemeIds.add(visemeId);
                        objectStoreClient.updateGuruAssetInventory(guruId, s3FinalPcFile);
                        continue;
                    } else {
                        logger.error("Sparse reconstruction incomplete and sparse export also failed for viseme {}.", visemeId);
                        continue;
                    }
                }
                
                logger.info("Step 2: Running image undistortion...");
                Path densePath = visemeWorkspaceDir.resolve("dense");
                ProcessBuilder undistortBuilder = new ProcessBuilder(
                        "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
                        "colmap", "image_undistorter",
                        "--image_path", visemeFrameDir.toAbsolutePath().toString(),
                        "--input_path", sparseModelPath.toAbsolutePath().toString(),
                        "--output_path", densePath.toAbsolutePath().toString()
                );
                undistortBuilder.environment().put("QT_QPA_PLATFORM", "offscreen");
                undistortBuilder.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                undistortBuilder.environment().put("GALLIUM_DRIVER", "llvmpipe");

                java.io.File undistortLogFile = new java.io.File(visemeWorkspaceDir.toFile(), "image_undistortion.log");
                undistortBuilder.redirectErrorStream(true);
                undistortBuilder.redirectOutput(undistortLogFile);
                
                Process undistortProcess = undistortBuilder.start();
                int undistortExitCode = undistortProcess.waitFor();
                
                if (undistortExitCode != 0) {
                    String errorDetails = "";
                    try {
                        if (Files.exists(undistortLogFile.toPath())) {
                            errorDetails = new String(Files.readAllBytes(undistortLogFile.toPath()));
                            logger.error("Colmap image undistortion log:\n{}", errorDetails);
                        }
                    } catch (IOException e) {
                        logger.warn("Could not read undistortion log", e);
                    }

                    if (undistortExitCode == 134) {
                        logger.error("Colmap image undistortion crashed with segmentation fault (exit code 134) for viseme {}. " +
                            "This may indicate: invalid sparse model, memory issues, or corrupted images.", visemeId);
                        logger.warn("Attempting fallback to sparse point cloud export...");

                        Path sparsePlyPath = visemeWorkspaceDir.resolve("sparse_points.ply");
                        ProcessBuilder exportBuilder = new ProcessBuilder(
                                "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
                                "colmap", "model_converter",
                                "--input_path", sparseModelPath.toAbsolutePath().toString(),
                                "--output_path", sparsePlyPath.toAbsolutePath().toString(),
                                "--output_type", "PLY"
                        );
                        exportBuilder.environment().put("QT_QPA_PLATFORM", "offscreen");
                        exportBuilder.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                        exportBuilder.environment().put("GALLIUM_DRIVER", "llvmpipe");
                        exportBuilder.inheritIO();
                        Process exportProcess = exportBuilder.start();
                        int exportExitCode = exportProcess.waitFor();
                        
                        if (exportExitCode == 0 && Files.exists(sparsePlyPath)) {
                            Path s3FinalPcFile = workspaceBaseDir.resolve(visemeId + ".ply");
                            Files.move(sparsePlyPath, s3FinalPcFile, StandardCopyOption.REPLACE_EXISTING);
                            logger.info("Successfully exported sparse point cloud as fallback after undistortion crash for viseme {}", visemeId);
                            extractedVisemeIds.add(visemeId);
                            objectStoreClient.updateGuruAssetInventory(guruId, s3FinalPcFile);
                            continue;
                        }
                    }
                    
                    logger.error("Colmap image undistortion failed for viseme {} with exit code: {}", visemeId, undistortExitCode);
                    continue;
                }

                // Step 3: Patch match stereo - attempt CPU-based dense reconstruction
                String executionMode = cudaAvailable ? "GPU (CUDA)" : "CPU";
                logger.info("Step 3: Running patch match stereo using {} (this may take a while on CPU)...", executionMode);
                Path dense0Path = densePath.resolve("0");
                ProcessBuilder stereoBuilder = new ProcessBuilder(
                        "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
                        "colmap", "patch_match_stereo",
                        "--workspace_path", dense0Path.toAbsolutePath().toString()
                );
                stereoBuilder.environment().put("QT_QPA_PLATFORM", "offscreen");
                // Keep software rendering flags for CPU mode, remove them if CUDA is available to allow GPU usage
                if (cudaAvailable) {
                    // Remove software rendering flags to allow GPU usage
                    stereoBuilder.environment().remove("LIBGL_ALWAYS_SOFTWARE");
                    stereoBuilder.environment().remove("GALLIUM_DRIVER");
                } else {
                    // Keep software rendering for CPU mode
                    stereoBuilder.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                    stereoBuilder.environment().put("GALLIUM_DRIVER", "llvmpipe");
                }
                
                // Redirect output to log file for CPU mode (since it will take longer)
                java.io.File stereoLogFile = new java.io.File(visemeWorkspaceDir.toFile(), "patch_match_stereo.log");
                stereoBuilder.redirectErrorStream(true);
                stereoBuilder.redirectOutput(stereoLogFile);
                
                logger.info("Starting patch_match_stereo for viseme {} (mode: {})...", visemeId, executionMode);
                if (!cudaAvailable) {
                    logger.info("CPU-based dense reconstruction in progress - this step can take 30 minutes to several hours depending on image count and resolution");
                }
                
                Process stereoProcess = stereoBuilder.start();
                int stereoExitCode = stereoProcess.waitFor();
                
                if (stereoExitCode != 0) {
                    String errorDetails = "";
                    try {
                        if (Files.exists(stereoLogFile.toPath())) {
                            errorDetails = new String(Files.readAllBytes(stereoLogFile.toPath()));
                            logger.error("Colmap patch_match_stereo log (last 1000 chars):\n{}", 
                                errorDetails.length() > 1000 ? errorDetails.substring(errorDetails.length() - 1000) : errorDetails);
                        }
                    } catch (IOException e) {
                        logger.warn("Could not read patch_match_stereo log", e);
                    }
                    
                    logger.error("Patch match stereo failed for viseme {} with exit code: {} (mode: {})", 
                        visemeId, stereoExitCode, executionMode);
                    logger.warn("Falling back to sparse point cloud export...");
                    
                    // Fallback: Export sparse reconstruction as PLY
                    Path sparsePlyPath = visemeWorkspaceDir.resolve("sparse_points.ply");
                    ProcessBuilder exportBuilder = new ProcessBuilder(
                            "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
                            "colmap", "model_converter",
                            "--input_path", sparseModelPath.toAbsolutePath().toString(),
                            "--output_path", sparsePlyPath.toAbsolutePath().toString(),
                            "--output_type", "PLY"
                    );
                    exportBuilder.environment().put("QT_QPA_PLATFORM", "offscreen");
                    exportBuilder.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                    exportBuilder.environment().put("GALLIUM_DRIVER", "llvmpipe");
                    exportBuilder.inheritIO();
                    Process exportProcess = exportBuilder.start();
                    int exportExitCode = exportProcess.waitFor();
                    
                    if (exportExitCode == 0 && Files.exists(sparsePlyPath)) {
                        logger.info("Successfully exported sparse point cloud as fallback");
                        Path s3FinalPcFile = workspaceBaseDir.resolve(visemeId + ".ply");
                        Files.move(sparsePlyPath, s3FinalPcFile, StandardCopyOption.REPLACE_EXISTING);
                        logger.info("Successfully created PCD file from sparse reconstruction for viseme {}: {}", visemeId, s3FinalPcFile);
                        extractedVisemeIds.add(visemeId);
                        objectStoreClient.updateGuruAssetInventory(guruId, s3FinalPcFile);
                        continue; // Skip to next viseme
                    } else {
                        logger.error("Both dense reconstruction and sparse export failed for viseme {}", visemeId);
                        continue;
                    }
                } else {
                    logger.info("Patch match stereo completed successfully for viseme {} (mode: {})", visemeId, executionMode);
                }
                
                // Step 4: Stereo fusion (creates the fused point cloud)
                logger.info("Step 4: Running stereo fusion...");
                ProcessBuilder fusionBuilder = new ProcessBuilder(
                        "xvfb-run", "-a", "-s", "-screen 0 1024x768x24",
                        "colmap", "stereo_fusion",
                        "--workspace_path", dense0Path.toAbsolutePath().toString(),
                        "--workspace_format", "COLMAP",
                        "--input_type", "geometric",
                        "--output_path", dense0Path.resolve("fused.ply").toAbsolutePath().toString()
                );
                fusionBuilder.environment().put("QT_QPA_PLATFORM", "offscreen");
                fusionBuilder.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
                fusionBuilder.environment().put("GALLIUM_DRIVER", "llvmpipe");
                
                java.io.File fusionLogFile = new java.io.File(visemeWorkspaceDir.toFile(), "stereo_fusion.log");
                fusionBuilder.redirectErrorStream(true);
                fusionBuilder.redirectOutput(fusionLogFile);
                
                Process fusionProcess = fusionBuilder.start();
                int fusionExitCode = fusionProcess.waitFor();
                
                if (fusionExitCode != 0) {
                    String errorDetails = "";
                    try {
                        if (Files.exists(fusionLogFile.toPath())) {
                            errorDetails = new String(Files.readAllBytes(fusionLogFile.toPath()));
                            logger.error("Colmap stereo fusion log:\n{}", errorDetails);
                        }
                    } catch (IOException e) {
                        logger.warn("Could not read stereo fusion log", e);
                    }
                    logger.error("Colmap stereo fusion failed for viseme {} with exit code: {}", visemeId, fusionExitCode);
                    continue;
                }

                // Log workspace structure to help debug
                logger.info("Checking workspace structure for viseme {}: {}", visemeId, workspacePath);
                if (Files.exists(visemeWorkspaceDir)) {
                    try {
                        Files.walk(visemeWorkspaceDir, 3).forEach(path -> {
                            if (Files.isRegularFile(path)) {
                                logger.info("Found file: {}", path);
                            } else if (Files.isDirectory(path)) {
                                logger.info("Found directory: {}", path);
                            }
                        });
                    } catch (IOException e) {
                        logger.warn("Could not walk workspace directory", e);
                    }
                }

                // Try multiple possible locations for fused.ply
                // Priority: dense/0/fused.ply (where we create it) first
                Path finalFusedPlyFile = null;
                Path expectedFusedPath = visemeWorkspaceDir.resolve("dense").resolve("0").resolve("fused.ply");
                List<Path> possibleLocations = List.of(
                    expectedFusedPath,  // Primary location where we create it
                    visemeWorkspaceDir.resolve("dense").resolve("fused.ply"),
                    visemeWorkspaceDir.resolve("dense").resolve("stereo").resolve("fused.ply"),
                    visemeWorkspaceDir.resolve("fused.ply"),
                    visemeWorkspaceDir.resolve("sparse").resolve("fused.ply")
                );
                
                for (Path possiblePath : possibleLocations) {
                    if (Files.exists(possiblePath)) {
                        logger.info("Found fused.ply at: {}", possiblePath);
                        finalFusedPlyFile = possiblePath;
                        break;
                    }
                }
                
                // If still not found, search recursively
                if (finalFusedPlyFile == null) {
                    logger.warn("fused.ply not found in expected locations, searching recursively...");
                    try {
                        java.util.Optional<Path> foundFile = Files.walk(visemeWorkspaceDir)
                            .filter(path -> path.getFileName() != null && 
                                   path.getFileName().toString().equals("fused.ply"))
                            .findFirst();
                        if (foundFile.isPresent()) {
                            finalFusedPlyFile = foundFile.get();
                            logger.info("Found fused.ply recursively at: {}", finalFusedPlyFile);
                        } else {
                            logger.error("fused.ply not found anywhere in workspace");
                        }
                    } catch (IOException e) {
                        logger.error("Error searching for fused.ply", e);
                    }
                }
                
                if (finalFusedPlyFile == null || !Files.exists(finalFusedPlyFile)) {
                    throw new RuntimeException("Colmap output file (fused.ply) not found. " +
                        "Colmap may have completed but dense reconstruction failed. " +
                        "Check colmap logs above for details. Workspace: " + workspacePath);
                }

                Path s3FinalPcFile = workspaceBaseDir.resolve(visemeId + ".ply");
                Files.move(finalFusedPlyFile, s3FinalPcFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Successfully created dense PCD file for viseme {}: {}", visemeId, s3FinalPcFile);
                
                extractedVisemeIds.add(visemeId);
                objectStoreClient.updateGuruAssetInventory(guruId, s3FinalPcFile);
            }
            logger.info("PCD creation summary:");
            logger.info("  - Successfully created PCDs for {} viseme(s): {}", 
                extractedVisemeIds.size(), extractedVisemeIds);
            if (!skippedVisemeIds.isEmpty()) {
                logger.warn("  - Skipped {} viseme(s) due to insufficient frames (<3 images): {}", 
                    skippedVisemeIds.size(), skippedVisemeIds);
            }
            logger.info("  - Total visemes processed: {} out of {} that had frame directories", 
                extractedVisemeIds.size() + skippedVisemeIds.size(), visemeFrameDirMap.size());
            objectStoreClient.updateGuruAssetInventory(guruId, extractedVisemeIds);
        } catch (Exception e) {
            logger.error("Error in createAndPublishPcdFromFrames for guruId: {}", guruId, e);
        } finally {
            try {
                deleteRecursively(workspaceBaseDir);
            } catch (Exception ignore) {
                logger.warn("Failed to cleanup workspace directory: {}", workspaceBaseDir, ignore);
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

