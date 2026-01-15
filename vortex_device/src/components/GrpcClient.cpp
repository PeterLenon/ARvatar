//
// Created by Peter Lenon Goshomi on 9/18/25.
//

#include "GrpcClient.h"
#include <string>
#include <grpcpp/create_channel.h>
#include <grpcpp/security/credentials.h>
#include <grpcpp/channel.h>
#include <chrono>
#include "voxel/asset/v1/asset_service.grpc.pb.h"
#include "voxel/dialogue/v1/dialogue_service.grpc.pb.h"

GrpcClient::GrpcClient(const std::string& target_port) {
    std::string target_address;
    if (target_port.find(':') != std::string::npos) {
        target_address = target_port;
    } else {
        target_address = "localhost:9091:" + target_port;
    }
    
    this->communication_channel = grpc::CreateChannel(target_address, grpc::InsecureChannelCredentials());
    this->asset_service_server_Stub = voxel::assets::v1::AssetService::NewStub(this->communication_channel);
    this->dialogue_service_server_Stub = voxel::dialogue::v1::DialogueService::NewStub(this->communication_channel);
}

GrpcClient::~GrpcClient() {
    this->asset_service_server_Stub.reset();
    this->dialogue_service_server_Stub.reset();
    this->communication_channel.reset();
}

grpc::Status GrpcClient::upload_guru_video(const voxel::assets::v1::UploadGuruVideoRequest& request, ::voxel::assets::v1::UploadGuruVideoResponse* response) {
    grpc::ClientContext context;
    auto deadline = std::chrono::system_clock::now() + std::chrono::seconds(60);
    context.set_deadline(deadline);
    return this->asset_service_server_Stub->UploadGuruVideo(&context, request, response);
}

grpc::Status GrpcClient::fetch_point_cloud(const voxel::assets::v1::GetPointCloudRequest& request, voxel::assets::v1::GetPointCloudResponse* response) {
    grpc::ClientContext context;
    return this->asset_service_server_Stub->GetPointCloud(&context, request, response);
}
grpc::Status GrpcClient::list_point_clouds(const voxel::assets::v1::ListPointCloudsRequest& request, voxel::assets::v1::ListPointCloudsResponse* response) {
    grpc::ClientContext context;
    return this->asset_service_server_Stub->ListPointClouds(&context, request, response);
}

std::unique_ptr<::grpc::ClientAsyncResponseReader<voxel::assets::v1::GetPointCloudResponse>> GrpcClient::fetch_point_cloud_async(const voxel::assets::v1::GetPointCloudRequest& request, ::grpc::CompletionQueue* cq) {
    grpc::ClientContext* context = new grpc::ClientContext();
    return this->asset_service_server_Stub->AsyncGetPointCloud(context, request, cq);
}
std::unique_ptr<::grpc::ClientAsyncResponseReader<voxel::assets::v1::ListPointCloudsResponse>> GrpcClient::list_point_clouds_async(const voxel::assets::v1::ListPointCloudsRequest& request, ::grpc::CompletionQueue* cq) {
    grpc::ClientContext* context = new grpc::ClientContext();
    return this->asset_service_server_Stub->AsyncListPointClouds(context, request, cq);
}

std::unique_ptr<::grpc::ClientReader<voxel::dialogue::v1::AnswerChunk>> GrpcClient::fetch_answer(const voxel::dialogue::v1::AskRequest& request) {
    grpc::ClientContext* context = new grpc::ClientContext();
    return this->dialogue_service_server_Stub->Ask(context, request);
}
std::unique_ptr<::grpc::ClientAsyncReader<voxel::dialogue::v1::AnswerChunk>> GrpcClient::fetch_answer_async(const voxel::dialogue::v1::AskRequest& request, ::grpc::CompletionQueue* cq, void* tag) {
    grpc::ClientContext* context = new grpc::ClientContext();
    return this->dialogue_service_server_Stub->AsyncAsk(context, request, cq, tag);
}

grpc::Status GrpcClient::fetch_guru_profile(const voxel::dialogue::v1::GetGuruProfileRequest& request, voxel::dialogue::v1::GetGuruProfileResponse* response) {
    grpc::ClientContext context;
    return this->dialogue_service_server_Stub->GetGuruProfile(&context, request, response);
}
std::unique_ptr<::grpc::ClientAsyncResponseReader<voxel::dialogue::v1::GetGuruProfileResponse>> GrpcClient::fetch_guru_profile_async(const voxel::dialogue::v1::GetGuruProfileRequest& request, ::grpc::CompletionQueue* cq) {
    grpc::ClientContext* context = new grpc::ClientContext();
    return this->dialogue_service_server_Stub->AsyncGetGuruProfile(context, request, cq);
}
