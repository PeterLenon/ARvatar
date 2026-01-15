package com.arvatar.vortex.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import voxel.assets.v1.AssetServiceGrpc;
import voxel.assets.v1.AssetServiceOuterClass.*;
import com.arvatar.vortex.service.AssetService;

@GrpcService
public class AssetGrpcService extends AssetServiceGrpc.AssetServiceImplBase {

    @Autowired
    private AssetService assetService;

    @Override
    public void listPointClouds(ListPointCloudsRequest request, 
                               StreamObserver<ListPointCloudsResponse> responseObserver) {
        try {
            ListPointCloudsResponse response = assetService.listPointClouds(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getPointCloud(GetPointCloudRequest request, 
                             StreamObserver<GetPointCloudResponse> responseObserver) {
        try {
            GetPointCloudResponse response = assetService.getPointCloud(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void uploadGuruVideo(UploadGuruVideoRequest request, 
                               StreamObserver<UploadGuruVideoResponse> responseObserver) {
        try {
            UploadGuruVideoResponse response = assetService.uploadGuruVideo(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            System.out.println("Error: " + e);
            responseObserver.onError(e);
        }
    }
}
