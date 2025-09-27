package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.MinIOS3Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import voxel.assets.v1.AssetServiceOuterClass.*;
import voxel.common.v1.Types;
import java.util.ArrayList;

@Service
public class AssetService {
    private final MinIOS3Client minIOS3Client = new MinIOS3Client();

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
        ArrayList<Types.Image> images = new ArrayList<>();
        images.addAll(request.getImagesList());
        boolean published_raw_images = false;
        boolean published_raw_video = false;

        UploadGuruVideoResponse.Builder responseBuilder = UploadGuruVideoResponse.newBuilder();
        try {
            published_raw_video = this.minIOS3Client.putVideo(guru_id, video);
            published_raw_images = this.minIOS3Client.putImages(guru_id, images);
            if (published_raw_images || published_raw_video) {
                //todo: register this event to the worker nodes so that they can work these events
                responseBuilder.setSuccess(true);
                responseBuilder.setMessage("Training asset(s) upload received successfully");
                responseBuilder.setPointCloudVariant("neutral");
                responseBuilder.setProcessedAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());
            } else {
                //todo: add retry technology
                responseBuilder.setSuccess(false);
                responseBuilder.setMessage("Video upload received unsuccessfully");
                responseBuilder.setPointCloudVariant("neutral");
                responseBuilder.setProcessedAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build());
            }
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
