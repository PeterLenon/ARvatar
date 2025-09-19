//
// Created by Peter Lenon Goshomi on 9/18/25.
//

#include "GrpcClient.h"
#include <string>
#include <grpcpp/create_channel.h>
#include <grpcpp/security/credentials.h>
#include "voxel/asset/v1/asset_service.grpc.pb.h"
#include "voxel/dialogue/v1/dialogue_service.grpc.pb.h"

GrpcClient::GrpcClient(const std::string& target_port) {
    this->communication_channel = grpc::CreateChannel(target_port, grpc::InsecureChannelCredentials());
    this->asset_service_server_Stub = voxel::assets::v1::AssetService::NewStub(this->communication_channel);
    this->dialogue_service_server_Stub = voxel::dialogue::v1::DialogueService::NewStub(this->communication_channel);
}

GrpcClient::~GrpcClient() {
    this->asset_service_server_Stub.reset();
    this->dialogue_service_server_Stub.reset();
    this->communication_channel.reset();
}

grpc::Status GrpcClient::fetch_point_cloud(grpc::ClientContext* context, const voxel::assets::v1::GetPointCloudRequest& request, voxel::assets::v1::GetPointCloudResponse* response) {
    return this->asset_service_server_Stub->GetPointCloud(context, request, response);
}
grpc::Status GrpcClient::list_point_clouds(grpc::ClientContext* context, const voxel::assets::v1::ListPointCloudsRequest& request, voxel::assets::v1::ListPointCloudsResponse* response) {
    return this->asset_service_server_Stub->ListPointClouds(context, request, response);
}

std::unique_ptr<::grpc::ClientAsyncResponseReader<voxel::assets::v1::GetPointCloudResponse>> GrpcClient::fetch_point_cloud_async(grpc::ClientContext* context, const voxel::assets::v1::GetPointCloudRequest& request, ::grpc::CompletionQueue* cq) {
    return this->asset_service_server_Stub->AsyncGetPointCloud(context, request, cq);
}
std::unique_ptr<::grpc::ClientAsyncResponseReader<voxel::assets::v1::ListPointCloudsResponse>> GrpcClient::list_point_clouds_async(grpc::ClientContext* context, const voxel::assets::v1::ListPointCloudsRequest& request, ::grpc::CompletionQueue* cq) {
    return this->asset_service_server_Stub->AsyncListPointClouds(context, request, cq);
}

std::unique_ptr<::grpc::ClientReader<voxel::dialogue::v1::AnswerChunk>> GrpcClient::fetch_answer(grpc::ClientContext* context, const voxel::dialogue::v1::AskRequest& request) {
    return this->dialogue_service_server_Stub->Ask(context, request);
}

std::unique_ptr<::grpc::ClientAsyncReader<voxel::dialogue::v1::AnswerChunk>> GrpcClient::fetch_answer_async(grpc::ClientContext* context, const voxel::dialogue::v1::AskRequest& request, ::grpc::CompletionQueue* cq, void* tag) {
    return this->dialogue_service_server_Stub->AsyncAsk(context, request, cq, tag);
}

grpc::Status GrpcClient::fetch_guru_profile(grpc::ClientContext* context, const voxel::dialogue::v1::GetGuruProfileRequest& request, voxel::dialogue::v1::GetGuruProfileResponse* response) {
    return this->dialogue_service_server_Stub->GetGuruProfile(context, request, response);
}
std::unique_ptr<::grpc::ClientAsyncResponseReader<voxel::dialogue::v1::GetGuruProfileResponse>> GrpcClient::fetch_guru_profile_async(grpc::ClientContext* context, const voxel::dialogue::v1::GetGuruProfileRequest& request, ::grpc::CompletionQueue* cq) {
    return this->dialogue_service_server_Stub->AsyncGetGuruProfile(context, request, cq);
}







