package com.arvatar.vortex.service;

import com.arvatar.vortex.models.AsrPcdJob;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import voxel.assets.v1.AssetServiceOuterClass.*;
import voxel.common.v1.Types;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

import java.util.List;
import java.util.Map;

@Service
public class AssetService {

    private final MinIOS3Client objectStoreClient = new MinIOS3Client();
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> asyncCommands;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(AssetService.class);

    public AssetService(@Value("${redis.uri:redis://localhost:6379}") String redisUri) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = connectWithRetry(redisClient);
        this.asyncCommands = connection.async();
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

    private String publishJobToStream(AsrPcdJob job){
        String asrJobRedisStream = "asr_jobs";
        try {
            String payload = objectMapper.writeValueAsString(job);
            return awaitXAdd(asrJobRedisStream, payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize job", e);
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

    public ListPointCloudsResponse listPointClouds(ListPointCloudsRequest request) {
        List<String> availableVisemes = null;
        try{
            availableVisemes = objectStoreClient.listAvailableGuruAssets(request.getGuruId());
        }catch (Exception e){
            logger.error("Failed to list available point clouds for guru " + request.getGuruId(), e);
        }
        JsonNode availableVisemesJson = availableVisemes != null ? objectMapper.valueToTree(availableVisemes) : objectMapper.createObjectNode();
        ListPointCloudsResponse.Builder responseBuilder = ListPointCloudsResponse.newBuilder();
        voxel.common.v1.Types.PointCloudMetadata.Builder pointCloudMetadata = voxel.common.v1.Types.PointCloudMetadata.newBuilder()
                .setGuruId(request.getGuruId())
                .setVariant("neutral")
                .setDescription(availableVisemesJson.toString())
                .setHasMesh(false)
                .setUpdatedAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());
        responseBuilder.addPointClouds(pointCloudMetadata.build());
        return responseBuilder.build();
    }

    public GetPointCloudResponse getPointCloud(GetPointCloudRequest request) {
        // TODO: remove this method all together
        
        GetPointCloudResponse.Builder responseBuilder = GetPointCloudResponse.newBuilder();
        
        // Add mock point cloud asset
        PointCloudAsset.Builder pointCloudAsset = PointCloudAsset.newBuilder();
        
        // Set metadata
        voxel.common.v1.Types.PointCloudMetadata.Builder metadata = voxel.common.v1.Types.PointCloudMetadata.newBuilder()
                .setGuruId(request.getGuruId())
                .setVariant(request.getVariant())
                .setDescription("Point cloud for " + request.getVariant() + " expression")
                .setHasMesh(request.getIncludeMesh())
                .setUpdatedAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());
        
        pointCloudAsset.setMetadata(metadata.build());
        
        // Set point cloud data
        voxel.common.v1.Types.Pcd.Builder pcd = voxel.common.v1.Types.Pcd.newBuilder()
                .setFormat("pcd")
                .setPayload(com.google.protobuf.ByteString.copyFromUtf8("mock_point_cloud_data"))
                .setUnits("meters");
        
        pointCloudAsset.setPointCloud(pcd.build());
        
        if (request.getIncludeMesh()) {
            voxel.common.v1.Types.Mesh.Builder mesh = voxel.common.v1.Types.Mesh.newBuilder()
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
        String guru_id = request.getGuruId();
        String s3VideoKey = objectStoreClient.putVideo(guru_id, video.getPayload().toByteArray());
        AsrPcdJob job = new AsrPcdJob(guru_id, s3VideoKey);
        objectStoreClient.updateJob(job);
        publishJobToStream(job);
        boolean success = s3VideoKey != null;
        UploadGuruVideoResponse.Builder responseBuilder = UploadGuruVideoResponse.newBuilder();
        responseBuilder.setSuccess(success);
        responseBuilder.setMessage(success ? "Video upload received successfully" : "Video upload failed");
        responseBuilder.setPointCloudVariant("neutral");
        responseBuilder.setProcessedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
                .build());
        return responseBuilder.build();
    }
}
