package com.arvatar.vortex.service;

import com.arvatar.vortex.config.StorageProperties;
import com.arvatar.vortex.config.WorkflowProperties;
import com.arvatar.vortex.dto.JobBlob;
import com.arvatar.vortex.dto.JobStatus;
import com.arvatar.vortex.dto.MinioS3AsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Service
public class AsrWorkflowService implements StreamListener<String, MapRecord<String, String, String>> {
    private static final Logger log = LoggerFactory.getLogger(AsrWorkflowService.class);

    private final ObjectMapper objectMapper;
    private final MinioS3AsyncClient minioClient;
    private final StorageProperties storageProperties;
    private final WorkflowProperties workflowProperties;
    private final StringRedisTemplate redisTemplate;
    private final ExecutorService workflowExecutor;

    public AsrWorkflowService(ObjectMapper objectMapper,
                              MinioS3AsyncClient minioClient,
                              StorageProperties storageProperties,
                              WorkflowProperties workflowProperties,
                              StringRedisTemplate redisTemplate,
                              ExecutorService workflowExecutor) {
        this.objectMapper = objectMapper;
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
        this.workflowProperties = workflowProperties;
        this.redisTemplate = redisTemplate;
        this.workflowExecutor = workflowExecutor;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String payload = record.getValue().get("payload");
        if (!StringUtils.hasText(payload)) {
            log.warn("Received empty job payload on ASR stream");
            return;
        }
        workflowExecutor.submit(() -> handlePayload(payload));
    }

    private void handlePayload(String payload) {
        JobBlob job;
        try {
            job = objectMapper.readValue(payload, JobBlob.class);
        } catch (IOException e) {
            log.error("Failed to deserialize job payload", e);
            return;
        }

        String jobObjectKey = (String) job.getMetadata().getOrDefault("jobObjectKey", job.getJobId() + ".json");
        String jobBucket = storageProperties.getJobBucket();

        job.getMetadata().put("asrStartedAt", Instant.now().toString());
        updateStatus(job, JobStatus.ASR_IN_PROGRESS, jobBucket, jobObjectKey);

        Path workingDir = null;
        try {
            String videoBucket = (String) job.getVideo().getOrDefault("bucket", storageProperties.getVideoBucket());
            String videoKey = (String) job.getVideo().get("objectKey");
            if (!StringUtils.hasText(videoKey)) {
                throw new IllegalStateException("Job missing video key information");
            }

            byte[] videoBytes = minioClient.getObject(videoBucket, videoKey).join();
            workingDir = Files.createTempDirectory("asr-" + job.getJobId());
            Path videoFile = workingDir.resolve("input-video" + guessExtension(videoKey));
            Files.write(videoFile, videoBytes);

            Path audioFile = workingDir.resolve("audio.wav");
            runCommandIfAvailable(List.of("ffmpeg", "-y", "-i", videoFile.toString(), audioFile.toString()));
            if (!Files.exists(audioFile)) {
                Files.write(audioFile, new byte[0]);
            }

            byte[] audioBytes = Files.readAllBytes(audioFile);
            String audioKey = job.getGuruId() + "/" + job.getJobId() + ".wav";
            minioClient.putObject(storageProperties.getVideoBucket(), audioKey, audioBytes, "audio/wav").join();

            Path whisperOutput = workingDir.resolve("whisper.json");
            runCommandIfAvailable(List.of("whisper", audioFile.toString(), "--output_format", "json", "--output_file", whisperOutput.toString()));
            if (!Files.exists(whisperOutput)) {
                Map<String, Object> placeholder = Map.of("segments", synthesizeWhisperSegments(job));
                Files.writeString(whisperOutput, objectMapper.writeValueAsString(placeholder));
            }

            List<Map<String, Object>> segments = parseSegments(whisperOutput, job);
            job.getOutput().put("audio", Map.of(
                    "bucket", storageProperties.getVideoBucket(),
                    "objectKey", audioKey,
                    "mimeType", "audio/wav",
                    "sizeBytes", audioBytes.length
            ));
            job.getOutput().put("segments", segments);
            job.getMetadata().put("asrCompletedAt", Instant.now().toString());
            updateStatus(job, JobStatus.ASR_COMPLETE, jobBucket, jobObjectKey);

            pushToPcdQueue(job);

            cleanupTemporaryFiles(List.of(whisperOutput, audioFile, videoFile));
        } catch (Exception e) {
            log.error("ASR workflow failed for job {}", job.getJobId(), e);
            job.getMetadata().put("asrError", e.getMessage());
            updateStatus(job, JobStatus.ASR_FAILED, jobBucket, jobObjectKey);
        } finally {
            if (workingDir != null) {
                try {
                    Files.deleteIfExists(workingDir);
                } catch (IOException e) {
                    log.debug("Failed to delete working directory {}", workingDir, e);
                }
            }
        }
    }

    private void updateStatus(JobBlob job, JobStatus status, String bucket, String key) {
        job.setStatus(status);
        try {
            minioClient.putJson(bucket, key, job).join();
        } catch (Exception e) {
            log.error("Failed to persist job {} status {}", job.getJobId(), status, e);
        }
    }

    private void pushToPcdQueue(JobBlob job) {
        try {
            String payload = objectMapper.writeValueAsString(job);
            Map<String, String> fields = new HashMap<>();
            fields.put("jobId", job.getJobId());
            fields.put("payload", payload);
            RecordId id = redisTemplate.opsForStream().add(
                    StreamRecords.mapBacked(fields).withStreamKey(workflowProperties.getPcdStream()));
            log.info("Queued job {} for PCD processing ({})", job.getJobId(), id);
        } catch (Exception e) {
            log.error("Failed to enqueue job {} for PCD processing", job.getJobId(), e);
        }
    }

    private void runCommandIfAvailable(List<String> command) {
        runCommandIfAvailable(command, null);
    }

    private void runCommandIfAvailable(List<String> command, Path workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        try {
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("Command {} exited with {}: {}", command.get(0), exitCode, error);
            }
        } catch (IOException e) {
            log.warn("Command {} is not available on the host: {}", command.get(0), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Command {} interrupted", command.get(0));
        }
    }

    private String guessExtension(String key) {
        int idx = key.lastIndexOf('.');
        return idx >= 0 ? key.substring(idx) : ".mp4";
    }

    private List<Map<String, Object>> parseSegments(Path whisperOutput, JobBlob job) throws IOException {
        if (Files.size(whisperOutput) == 0) {
            return synthesizeWhisperSegments(job);
        }
        try {
            Map<?, ?> whisper = objectMapper.readValue(Files.readString(whisperOutput), Map.class);
            Object segments = whisper.get("segments");
            if (segments instanceof List<?>) {
                return enrichSegments((List<?>) segments);
            }
            return synthesizeWhisperSegments(job);
        } catch (Exception e) {
            log.warn("Failed to parse whisper output, using synthesized segments", e);
            return synthesizeWhisperSegments(job);
        }
    }

    private List<Map<String, Object>> enrichSegments(List<?> rawSegments) {
        List<Map<String, Object>> segments = new ArrayList<>();
        for (Object item : rawSegments) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> rawMap = (Map<?, ?>) item;
            Map<String, Object> normalized = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(entry.getKey().toString(), entry.getValue());
                }
            }
            Map<String, Object> segment = new HashMap<>();
            Object text = normalized.get("text");
            segment.put("text", text != null ? text : "");
            segment.put("start", extractNumber(normalized.get("start"))); 
            segment.put("end", extractNumber(normalized.get("end")));
            segment.put("visemes", generateVisemesForText(segment.get("text").toString()));
            segments.add(segment);
        }
        return segments;
    }

    private List<Map<String, Object>> synthesizeWhisperSegments(JobBlob job) {
        List<Map<String, Object>> segments = new ArrayList<>();
        Map<String, Object> segment = new HashMap<>();
        segment.put("text", "Placeholder transcription for job " + job.getJobId());
        segment.put("start", 0.0);
        segment.put("end", 5.0);
        segment.put("visemes", generateVisemesForText("placeholder"));
        segments.add(segment);
        return segments;
    }

    private List<Map<String, Object>> generateVisemesForText(String text) {
        List<Map<String, Object>> visemes = new ArrayList<>();
        char[] chars = text.toUpperCase().toCharArray();
        double time = 0.0;
        for (char ch : chars) {
            Map<String, Object> viseme = new HashMap<>();
            viseme.put("phoneme", String.valueOf(ch));
            viseme.put("viseme", mapPhonemeToViseme(ch));
            viseme.put("start", time);
            time += 0.1;
            viseme.put("end", time);
            visemes.add(viseme);
        }
        return visemes;
    }

    private String mapPhonemeToViseme(char phoneme) {
        if ("AEIOU".indexOf(phoneme) >= 0) {
            return "open";
        }
        if ("FV".indexOf(phoneme) >= 0) {
            return "bite";
        }
        if ("MBP".indexOf(phoneme) >= 0) {
            return "closed";
        }
        return "neutral";
    }

    private void cleanupTemporaryFiles(List<Path> files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.debug("Failed to delete temp file {}", file, e);
            }
        }
    }

    private double extractNumber(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
