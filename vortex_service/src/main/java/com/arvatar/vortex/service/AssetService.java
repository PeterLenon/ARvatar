package com.arvatar.vortex.service;

import com.arvatar.vortex.config.StorageProperties;
import com.arvatar.vortex.config.WorkflowProperties;
import com.arvatar.vortex.dto.JobBlob;
import com.arvatar.vortex.dto.JobStatus;
import com.arvatar.vortex.dto.MinioS3AsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import voxel.assets.v1.AssetServiceOuterClass.*;
import voxel.common.v1.Types;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class AssetService {
    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private final MinioS3AsyncClient minioClient;
    private final StorageProperties storageProperties;
    private final WorkflowProperties workflowProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public AssetService(MinioS3AsyncClient minioClient,
                        StorageProperties storageProperties,
                        WorkflowProperties workflowProperties,
                        ObjectMapper objectMapper,
                        StringRedisTemplate redisTemplate) {
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
        this.workflowProperties = workflowProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * List available point clouds for a guru
     */
    public ListPointCloudsResponse listPointClouds(ListPointCloudsRequest request) {
        ListPointCloudsResponse.Builder responseBuilder = ListPointCloudsResponse.newBuilder();
        Types.PointCloudMetadata.Builder pointCloudMetadata = Types.PointCloudMetadata.newBuilder()
                .setGuruId(request.getGuruId())
                .setVariant("neutral")
                .setDescription("Default neutral expression point cloud")
                .setHasMesh(true)
                .setUpdatedAt(Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());

        responseBuilder.addPointClouds(pointCloudMetadata.build());
        return responseBuilder.build();
    }

    /**
     * Get a specific point cloud
     */
    public GetPointCloudResponse getPointCloud(GetPointCloudRequest request) {
        GetPointCloudResponse.Builder responseBuilder = GetPointCloudResponse.newBuilder();
        PointCloudAsset.Builder pointCloudAsset = PointCloudAsset.newBuilder();

        Types.PointCloudMetadata.Builder metadata = Types.PointCloudMetadata.newBuilder()
                .setGuruId(request.getGuruId())
                .setVariant(request.getVariant())
                .setDescription("Point cloud for " + request.getVariant() + " expression")
                .setHasMesh(request.getIncludeMesh())
                .setUpdatedAt(Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());

        pointCloudAsset.setMetadata(metadata.build());
        Types.Pcd.Builder pcd = Types.Pcd.newBuilder()
                .setFormat("pcd")
                .setPayload(com.google.protobuf.ByteString.copyFromUtf8("mock_point_cloud_data"))
                .setUnits("meters");

        pointCloudAsset.setPointCloud(pcd.build());

        if (request.getIncludeMesh()) {
            Types.Mesh.Builder mesh = Types.Mesh.newBuilder()
                    .setFormat("ply")
                    .setPayload(com.google.protobuf.ByteString.copyFromUtf8("mock_mesh_data"))
                    .setUnits("meters");
            pointCloudAsset.setMesh(mesh.build());
        }

        responseBuilder.setAsset(pointCloudAsset.build());
        return responseBuilder.build();
    }

    public UploadGuruVideoResponse uploadGuruVideo(UploadGuruVideoRequest request) {
        Types.Video video = request.getVideo();
        String guruId = request.getGuruId();
        byte[] videoBytes = video.getPayload().toByteArray();
        Instant now = Instant.now();
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(now);
        String format = StringUtils.hasText(video.getFormat()) ? video.getFormat() : "mp4";
        String videoKey = String.format("%s/%s.%s", guruId, timestamp, format);
        String videoBucket = storageProperties.getVideoBucket();
        String videoMimeType = StringUtils.hasText(video.getMimeType()) ? video.getMimeType() : "video/mp4";

        try {
            minioClient.putObject(videoBucket, videoKey, videoBytes, videoMimeType).join();
            log.info("Stored guru video for {} at {}/{}", guruId, videoBucket, videoKey);
        } catch (Exception e) {
            log.error("Failed to upload video for guru {}", guruId, e);
            return UploadGuruVideoResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to store video: " + e.getMessage())
                    .build();
        }

        JobBlob jobBlob = new JobBlob();
        jobBlob.setGuruId(guruId);
        jobBlob.setStatus(JobStatus.QUEUED);
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("bucket", videoBucket);
        videoInfo.put("objectKey", videoKey);
        videoInfo.put("mimeType", videoMimeType);
        videoInfo.put("sizeBytes", videoBytes.length);
        jobBlob.setVideo(videoInfo);
        jobBlob.getMetadata().put("submittedAt", now.toString());
        jobBlob.getMetadata().put("imageCount", request.getImagesCount());
        jobBlob.getMetadata().put("workflow", "guru-video");

        String jobObjectKey = String.format("%s/%s.json", guruId, jobBlob.getJobId());
        jobBlob.getMetadata().put("jobObjectKey", jobObjectKey);

        try {
            minioClient.putJson(storageProperties.getJobBucket(), jobObjectKey, jobBlob).join();
            log.info("Persisted job blob {} in bucket {}", jobBlob.getJobId(), storageProperties.getJobBucket());
        } catch (Exception e) {
            log.error("Failed to persist job blob {}", jobBlob.getJobId(), e);
            return UploadGuruVideoResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to persist job blob: " + e.getMessage())
                    .build();
        }

        try {
            String payload = objectMapper.writeValueAsString(jobBlob);
            Map<String, String> fields = new HashMap<>();
            fields.put("jobId", jobBlob.getJobId());
            fields.put("payload", payload);
            RecordId recordId = redisTemplate.opsForStream().add(
                    StreamRecords.mapBacked(fields).withStreamKey(workflowProperties.getAsrStream()));
            log.info("Queued job {} for ASR processing ({})", jobBlob.getJobId(), recordId);
        } catch (Exception e) {
            log.error("Failed to enqueue job {} for ASR processing", jobBlob.getJobId(), e);
            return UploadGuruVideoResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to enqueue job for ASR: " + e.getMessage())
                    .build();
        }

        UploadGuruVideoResponse.Builder responseBuilder = UploadGuruVideoResponse.newBuilder();
        responseBuilder.setSuccess(true);
        responseBuilder.setMessage("Video queued for processing; jobId=" + jobBlob.getJobId());
        responseBuilder.setPointCloudVariant("neutral");
        responseBuilder.setProcessedAt(Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build());
        return responseBuilder.build();
    }
}
