package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.ASRJob;
import com.arvatar.vortex.dto.MinIOS3Client;
import org.springframework.stereotype.Service;
import voxel.assets.v1.AssetServiceOuterClass.*;
import voxel.common.v1.Types;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class AssetService {
    private final MinIOS3Client minIOS3Client = new MinIOS3Client();
    private final AutomaticTranscriptionService automaticTranscriptionService;

    public AssetService(AutomaticTranscriptionService automaticTranscriptionService) {
        this.automaticTranscriptionService = automaticTranscriptionService;
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

    public UploadGuruVideoResponse uploadGuruVideo(@org.jetbrains.annotations.NotNull UploadGuruVideoRequest request) {
        Types.Video video = request.getVideo();
        String guru_id = request.getGuruId();
        String published_raw_video;
        UploadGuruVideoResponse.Builder responseBuilder = UploadGuruVideoResponse.newBuilder();
        try {
            published_raw_video = this.minIOS3Client.putVideo(guru_id, video);
            boolean success = published_raw_video != null;

            if (success) {
                ASRJob videoJob = new ASRJob();
                videoJob.video_id = published_raw_video;
                videoJob.guru_id = guru_id;
                videoJob.status = "queued";
                videoJob.job_id = UUID.randomUUID().toString();
                videoJob.queued_at = Instant.now().toString();
                String txn_id = this.automaticTranscriptionService.enlistASRjob(videoJob);
            }
            responseBuilder.setSuccess(success);
            responseBuilder.setMessage(success ? "Training asset(s) upload received successfully" : "Training asset(s) upload failed");
            responseBuilder.setPointCloudVariant("neutral");
            responseBuilder.setProcessedAt(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(System.currentTimeMillis() / 1000)
                    .build());
        }catch(Exception e) {
            responseBuilder.setSuccess(false);
                responseBuilder.setMessage(e.getMessage());
                responseBuilder.setPointCloudVariant(e.getClass().getSimpleName());
                responseBuilder.setProcessedAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());
        }
        return responseBuilder.build();
    }
}
