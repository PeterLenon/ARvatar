package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.dto.MinIOS3Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.springframework.stereotype.Service;
import voxel.assets.v1.AssetServiceOuterClass.*;
import voxel.common.v1.Types;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Map;

@Service
public class AssetService {

    private final MinIOS3Client objectStoreClient = new MinIOS3Client();
    private RedisClient redisClient = RedisClient.create("redis://localhost:6379");
    private StatefulRedisConnection<String, String> connection = redisClient.connect();
    private RedisAsyncCommands<String, String> asyncCommands = connection.async();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String publishJobToStream(AsrPcdJob job){
        String asrJobRedisStream = "asr_jobs";
        try {
            String payload = objectMapper.writeValueAsString(job);
            return String.valueOf(asyncCommands.xadd(asrJobRedisStream, Map.of("job", payload)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize job", e);
        }
    }

    /**
     * List available point clouds for a guru
     */
    public ListPointCloudsResponse listPointClouds(ListPointCloudsRequest request) {
        // TODO: Implement actual logic to fetch point clouds from storage
        // This is a skeleton implementation
        
        ListPointCloudsResponse.Builder responseBuilder = ListPointCloudsResponse.newBuilder();
        
        // Add some mock data for now using common types
        voxel.common.v1.Types.PointCloudMetadata.Builder pointCloudMetadata = voxel.common.v1.Types.PointCloudMetadata.newBuilder()
                .setGuruId(request.getGuruId())
                .setVariant("neutral")
                .setDescription("Default neutral expression point cloud")
                .setHasMesh(true)
                .setUpdatedAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());
        
        responseBuilder.addPointClouds(pointCloudMetadata.build());
        
        return responseBuilder.build();
    }

    /**
     * Get a specific point cloud
     */
    public GetPointCloudResponse getPointCloud(GetPointCloudRequest request) {
        // TODO: Implement actual logic to fetch point cloud binary data
        // This is a skeleton implementation
        
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
