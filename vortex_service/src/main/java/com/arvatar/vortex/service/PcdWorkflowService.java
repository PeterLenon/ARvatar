package com.arvatar.vortex.service;

import com.arvatar.vortex.config.StorageProperties;
import com.arvatar.vortex.dto.JobBlob;
import com.arvatar.vortex.dto.JobStatus;
import com.arvatar.vortex.dto.MinioS3AsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Service
public class PcdWorkflowService implements StreamListener<String, MapRecord<String, String, String>> {
    private static final Logger log = LoggerFactory.getLogger(PcdWorkflowService.class);

    private final ObjectMapper objectMapper;
    private final MinioS3AsyncClient minioClient;
    private final StorageProperties storageProperties;
    private final ExecutorService workflowExecutor;

    public PcdWorkflowService(ObjectMapper objectMapper,
                              MinioS3AsyncClient minioClient,
                              StorageProperties storageProperties,
                              ExecutorService workflowExecutor) {
        this.objectMapper = objectMapper;
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
        this.workflowExecutor = workflowExecutor;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String payload = record.getValue().get("payload");
        if (!StringUtils.hasText(payload)) {
            log.warn("Received empty job payload on PCD stream");
            return;
        }
        workflowExecutor.submit(() -> handlePayload(payload));
    }

    private void handlePayload(String payload) {
        JobBlob job;
        try {
            job = objectMapper.readValue(payload, JobBlob.class);
        } catch (IOException e) {
            log.error("Failed to deserialize job payload for PCD workflow", e);
            return;
        }

        String jobObjectKey = (String) job.getMetadata().getOrDefault("jobObjectKey", job.getJobId() + ".json");
        String jobBucket = storageProperties.getJobBucket();

        job.getMetadata().put("pcdStartedAt", Instant.now().toString());
        updateStatus(job, JobStatus.PCD_IN_PROGRESS, jobBucket, jobObjectKey);

        try {
            String videoBucket = (String) job.getVideo().getOrDefault("bucket", storageProperties.getVideoBucket());
            String videoKey = (String) job.getVideo().get("objectKey");
            if (!StringUtils.hasText(videoKey)) {
                throw new IllegalStateException("Job missing video key information");
            }

            byte[] videoBytes = minioClient.getObject(videoBucket, videoKey).join();
            Path tempVideo = Files.createTempFile("pcd-video-", ".mp4");
            Files.write(tempVideo, videoBytes);

            List<Map<String, Object>> segments = extractSegments(job);
            List<Map<String, Object>> pcdFrames = new ArrayList<>();
            double frameTime = 0.0;
            for (Map<String, Object> segment : segments) {
                Object visemePayload = segment.getOrDefault("visemes", List.of());
                if (!(visemePayload instanceof List)) {
                    continue;
                }
                List<?> visemeList = (List<?>) visemePayload;
                for (Object entry : visemeList) {
                    if (!(entry instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> rawViseme = (Map<?, ?>) entry;
                    Map<String, Object> frame = new HashMap<>();
                    frame.put("timestamp", frameTime);
                    frame.put("viseme", rawViseme.get("viseme"));
                    frame.put("phoneme", rawViseme.get("phoneme"));
                    frame.put("frameIndex", (int) Math.round(frameTime * 30));
                    pcdFrames.add(frame);
                    frameTime += 1.0 / 30.0;
                }
            }

            String pcdKey = job.getGuruId() + "/" + job.getJobId() + "-pcd.json";
            Map<String, Object> payloadMap = Map.of(
                    "frames", pcdFrames,
                    "generatedAt", Instant.now().toString());
            minioClient.putJson(storageProperties.getJobBucket(), pcdKey, payloadMap).join();
            job.getOutput().put("pcd", Map.of(
                    "bucket", storageProperties.getJobBucket(),
                    "objectKey", pcdKey,
                    "frameCount", pcdFrames.size()));
            job.getMetadata().put("pcdCompletedAt", Instant.now().toString());
            updateStatus(job, JobStatus.COMPLETE, jobBucket, jobObjectKey);

            Files.deleteIfExists(tempVideo);
        } catch (Exception e) {
            log.error("PCD workflow failed for job {}", job.getJobId(), e);
            job.getMetadata().put("pcdError", e.getMessage());
            updateStatus(job, JobStatus.PCD_FAILED, jobBucket, jobObjectKey);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSegments(JobBlob job) {
        Object segments = job.getOutput().get("segments");
        if (segments instanceof List<?>) {
            return (List<Map<String, Object>>) segments;
        }
        return new ArrayList<>();
    }
}
