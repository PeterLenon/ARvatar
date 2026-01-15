package com.arvatar.vortex.temporal.activities;

import com.arvatar.vortex.models.AsrPcdJob;
import com.arvatar.vortex.models.JobStatus;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.arvatar.vortex.service.DatabaseWriter;
import com.arvatar.vortex.service.LLMService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.FileInputStream;
import java.io.BufferedInputStream;

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
    private final LLMService llmService;
    
    // Vosk model configuration - using lightweight English model
    private static final String VOSK_MODEL_NAME = "vosk-model-en-us-0.22";
    private static final String VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/" + VOSK_MODEL_NAME + ".zip";
    private static final String MODEL_CACHE_DIR = System.getProperty("user.home") + "/.cache/vosk/models";

    public AsrActivitiesImpl(ObjectProvider<DatabaseWriter> databaseWriterProvider, LLMService llmService, @Value("${redis.uri:redis://localhost:6379}") String redisUri) {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectStoreClient = new MinIOS3Client();
        this.redisClient = RedisClient.create(redisUri);
        this.connection = connectWithRetry(redisClient);
        this.asyncCommands = connection.async();
        this.databaseWriter = databaseWriterProvider.getIfAvailable();
        this.llmService = llmService;
        if (this.databaseWriter == null) {
            logger.warn("Database writer is not configured; embeddings will not be persisted.");
        }
    }

    private StatefulRedisConnection<String, String> connectWithRetry(RedisClient client) {
        int maxRetries = 10;
        long initialDelayMs = 1000;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Attempting to connect to Redis (attempt {}/{})", attempt, maxRetries);
                return client.connect();
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    logger.error("Failed to connect to Redis after {} attempts", maxRetries, e);
                    throw new RuntimeException("Unable to connect to Redis after " + maxRetries + " attempts", e);
                }
                long delayMs = initialDelayMs * attempt;
                logger.warn("Redis connection failed (attempt {}/{}), retrying in {} ms...", attempt, maxRetries, delayMs, e);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting to retry Redis connection", ie);
                }
            }
        }
        throw new RuntimeException("Failed to connect to Redis");
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
                    "ffmpeg", "-y", "-i", videoFile.toAbsolutePath().toString(),
                    "-vn", "-ac", "1", "-ar", "16000", "-acodec", "pcm_s16le", audioFile.toAbsolutePath().toString()
            ).redirectErrorStream(true));
            Path voskFilePath = downloadVoskModel();
            runOrThrow(new ProcessBuilder(
                    "rhubarb", "-f", "json", "-o", jsonFile.toAbsolutePath().toString(), audioFile.toAbsolutePath().toString()
            ));
            String transcript = transcribeAudio(audioFile, voskFilePath);
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
        
        // Capture both stdout and stderr
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        
        try (BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
             BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            
            // Read stdout in a separate thread to avoid blocking
            Thread stdoutThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = stdout.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    logger.debug("Error reading stdout", e);
                }
            });
            stdoutThread.start();
            
            // Read stderr
            String line;
            while ((line = stderr.readLine()) != null) {
                error.append(line).append("\n");
            }
            
            stdoutThread.join();
        }
        
        int code = p.waitFor();
        if (code != 0) {
            String command = String.join(" ", pb.command());
            String errorMessage = "Command failed: " + command;
            if (error.length() > 0) {
                errorMessage += "\nError output:\n" + error.toString();
            }
            if (output.length() > 0) {
                errorMessage += "\nStandard output:\n" + output.toString();
            }
            logger.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
    }

    private String publishJobToStream(AsrPcdJob job) throws ExecutionException, InterruptedException, IOException {
        String pcdJobRedisStream = "pcd_jobs";
        String payload = objectMapper.writeValueAsString(job);
        return asyncCommands.xadd(pcdJobRedisStream, Map.of("job", payload)).get();
    }

    private String transcribeAudio(Path audioFile, Path modelPath) throws IOException {
        try {
            logger.info("Loading Vosk model from: {}", modelPath);
            Model model = new Model(modelPath.toString());

            try (model; Recognizer recognizer = new Recognizer(model, 16000)) {
                StringBuilder transcript = new StringBuilder();
                try (FileInputStream fis = new FileInputStream(audioFile.toFile());
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    int nbytes;
                    byte[] b = new byte[4096];

                    while ((nbytes = bis.read(b)) >= 0) {
                        if (recognizer.acceptWaveForm(b, nbytes)) {
                            String result = recognizer.getResult();
                            if (result != null && !result.isEmpty()) {
                                JsonNode resultJson = objectMapper.readTree(result);
                                String text = resultJson.path("text").asText();
                                if (!text.isEmpty()) {
                                    if (transcript.length() > 0) {
                                        transcript.append(" ");
                                    }
                                    transcript.append(text);
                                }
                            }
                        } else {
                            String partialResult = recognizer.getPartialResult();
                            if (logger.isDebugEnabled() && partialResult != null && !partialResult.isEmpty()) {
                                JsonNode partialJson = objectMapper.readTree(partialResult);
                                String partialText = partialJson.path("partial").asText();
                                if (!partialText.isEmpty()) {
                                }
                            }
                        }
                    }

                    String finalResult = recognizer.getFinalResult();
                    if (finalResult != null && !finalResult.isEmpty()) {
                        JsonNode finalJson = objectMapper.readTree(finalResult);
                        String text = finalJson.path("text").asText();
                        if (!text.isEmpty()) {
                            if (transcript.length() > 0) {
                                transcript.append(" ");
                            }
                            transcript.append(text);
                        }
                    }
                }

                String transcription = transcript.toString().trim();
                logger.info("Vosk transcription completed. Length: {} characters", transcription.length());
                return transcription.isEmpty() ? "" : transcription;

            }
        } catch (Exception e) {
            logger.error("Failed to transcribe audio file with Vosk: {}", audioFile, e);
            throw new IOException("Audio transcription failed", e);
        }
    }

    private Path downloadVoskModel() throws IOException, InterruptedException {
        Path cacheDir = Paths.get(MODEL_CACHE_DIR, VOSK_MODEL_NAME);
        Files.createDirectories(cacheDir);

        Path amFile = cacheDir.resolve("am");
        Path confFile = cacheDir.resolve("conf");
        if (Files.exists(amFile) || Files.exists(confFile)) {
            logger.info("Using cached Vosk model from: {}", cacheDir);
            return cacheDir;
        }
        Path zipFile = cacheDir.resolve(VOSK_MODEL_NAME + ".zip");
        if (Files.exists(zipFile)) {
            // Validate ZIP file before extraction
            if (isValidZipFile(zipFile)) {
                logger.info("Found valid Vosk model zip file, extracting...");
                extractZipFile(zipFile, cacheDir);
                logger.info("Vosk model extracted successfully to: {}", cacheDir);
                return cacheDir;
            } else {
                logger.warn("Found corrupted Vosk model zip file, deleting and re-downloading...");
                Files.deleteIfExists(zipFile);
            }
        }
        logger.info("Downloading Vosk model from: {}", VOSK_MODEL_URL);
        downloadFile(VOSK_MODEL_URL, zipFile);
        long fileSize = Files.size(zipFile);
        logger.info("Downloaded Vosk model zip file: {} bytes ({} MB)", fileSize, fileSize / (1024 * 1024));
        
        // Validate downloaded ZIP file
        if (!isValidZipFile(zipFile)) {
            Files.deleteIfExists(zipFile);
            throw new IOException("Downloaded ZIP file is corrupted. Please try again.");
        }

        logger.info("Extracting Vosk model...");
        extractZipFile(zipFile, cacheDir);
        try {
            Files.deleteIfExists(zipFile);
        } catch (Exception e) {
            logger.warn("Could not delete zip file: {}", zipFile, e);
        }
        logger.info("Vosk model downloaded and extracted successfully to: {}", cacheDir);
        return cacheDir;
    }
    
    private boolean isValidZipFile(Path zipFile) {
        try {
            // Try to open the ZIP file - if it's corrupted, this will throw an exception
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile())) {
                // Try to read entries - if the file is incomplete, this will fail
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                // Just check if we can read at least one entry to validate the file
                if (entries.hasMoreElements()) {
                    return true;
                }
                // Empty ZIP is also valid (though unusual)
                return true;
            }
        } catch (java.util.zip.ZipException e) {
            logger.warn("ZIP file validation failed: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            logger.warn("Error validating ZIP file: {}", e.getMessage());
            return false;
        }
    }
    
    private void extractZipFile(Path zipFile, Path extractTo) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile())) {
            // Collect all entries first (Enumeration can only be used once)
            java.util.List<java.util.zip.ZipEntry> allEntries = new java.util.ArrayList<>();
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                allEntries.add(entries.nextElement());
            }
            
            // Find common prefix (root directory) if all entries share one
            String commonPrefix = null;
            
            // First, find a candidate prefix from the first entry that has one
            for (java.util.zip.ZipEntry entry : allEntries) {
                String name = entry.getName();
                
                // Skip empty root entries
                if (name.isEmpty() || name.equals("/")) {
                    continue;
                }
                
                // Find the first directory separator to identify potential root directory
                int firstSlash = name.indexOf('/');
                if (firstSlash > 0) {
                    commonPrefix = name.substring(0, firstSlash + 1);
                    break;
                }
            }
            
            // If we found a candidate prefix, verify all entries share it
            if (commonPrefix != null) {
                for (java.util.zip.ZipEntry entry : allEntries) {
                    String name = entry.getName();
                    
                    // Skip empty root entries
                    if (name.isEmpty() || name.equals("/")) {
                        continue;
                    }
                    
                    // If entry doesn't start with the prefix, there's no common prefix
                    if (!name.startsWith(commonPrefix)) {
                        commonPrefix = null;
                        break;
                    }
                }
            }
            
            // Extract files, stripping common prefix if found
            int fileCount = 0;
            for (java.util.zip.ZipEntry entry : allEntries) {
                String entryName = entry.getName();
                
                // Skip empty root entries
                if (entryName.isEmpty() || entryName.equals("/")) {
                    continue;
                }
                
                // Strip common prefix if it exists
                if (commonPrefix != null && entryName.startsWith(commonPrefix)) {
                    entryName = entryName.substring(commonPrefix.length());
                    // Skip if entry becomes empty after stripping
                    if (entryName.isEmpty()) {
                        continue;
                    }
                }
                
                Path entryPath = extractTo.resolve(entryName);
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                    continue;
                }
                Files.createDirectories(entryPath.getParent());
                try (InputStream is = zip.getInputStream(entry)) {
                    Files.copy(is, entryPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    fileCount++;
                    if (fileCount % 100 == 0) {
                        logger.debug("Extracted {} files...", fileCount);
                    }
                }
            }
            logger.info("Extracted {} files from ZIP archive", fileCount);
            if (commonPrefix != null) {
                logger.info("Stripped common prefix '{}' from ZIP entries", commonPrefix);
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("ZIP file is corrupted or incomplete: " + e.getMessage() + 
                    ". Please delete the file and try again.", e);
        }
    }

    private void downloadFile(String url, Path destination) throws IOException, InterruptedException {
        logger.info("Downloading {} to {}", url, destination);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(java.time.Duration.ofMinutes(5))
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofHours(5))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        
        if (response.statusCode() != 200) {
            try (InputStream errorStream = response.body()) {
                // Consume error response body
            }
            throw new IOException("Failed to download " + url + ": HTTP " + response.statusCode());
        }
        
        try (InputStream inputStream = response.body();
             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destination.toFile());
             java.io.BufferedOutputStream bufferedOutputStream = new java.io.BufferedOutputStream(outputStream, 8192)) {
            
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            long lastLogTime = System.currentTimeMillis();
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                bufferedOutputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLogTime > 10000) {
                    logger.info("Download progress: {} MB downloaded...", totalBytesRead / (1024 * 1024));
                    lastLogTime = currentTime;
                }
            }
            
            bufferedOutputStream.flush();
            logger.info("Download completed: {} MB total", totalBytesRead / (1024 * 1024));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IOException("Download timeout: The download took too long. Please check your internet connection.", e);
        } catch (java.net.SocketTimeoutException e) {
            throw new IOException("Connection timeout: Unable to connect to the server. Please check your internet connection.", e);
        }
    }

    private List<ChunkWithEmbedding> getEmbeddingsVectors(String transcript){
        List<ChunkWithEmbedding> chunksWithEmbeddings = new ArrayList<>();
        try{
            String[] chunks = transcript.split("(?<=\\G.{500})");
            for (String chunk : chunks) {
                float[] embedding = llmService.embedText(chunk);
                if(embedding != null) chunksWithEmbeddings.add(new ChunkWithEmbedding(chunk, embedding));
            }
            return chunksWithEmbeddings;
        }catch (Exception e){
            logger.error("Failed to generate embeddings for transcript chunks", e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    private void writeToPgVectorDatabase(String guruId, String videoKey, List<ChunkWithEmbedding> chunksWithEmbeddings) {
        if (databaseWriter == null) {
            logger.debug("Skipping pgvector persistence for guruId {} because no database writer is configured.", guruId);
            return;
        }
        try {
            databaseWriter.insertPersonIfNotExists(guruId, guruId);
            UUID videoId = databaseWriter.insertVideo(guruId, "ASR Generated Video", videoKey);
            logger.info("Created video record for videoKey: {} with videoId: {}", videoKey, videoId);
            for (ChunkWithEmbedding chunkWithEmbedding : chunksWithEmbeddings) {
                UUID chunkId = databaseWriter.insertChunk(
                    guruId, 
                    videoId, 
                    chunkWithEmbedding.chunk,
                    chunkWithEmbedding.embedding
                );
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

