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

voxel::assets::v1::GetPointCloudResponse GrpcClient::fetch_point_cloud(const voxel::assets::v1::GetPointCloudRequest &request) {
    return this->asset_service_server_Stub.GetPointCloud(request);
}

voxel::assets::v1::ListPointCloudsResponse GrpcClient::list_point_clouds(const voxel::assets::v1::ListPointCloudsRequest &request) {
    return this->asset_service_server_Stub.ListPointClouds(request);
}

std::future<voxel::assets::v1::GetPointCloudResponse> GrpcClient::fetch_point_cloud_async(const voxel::assets::v1::GetPointCloudRequest &request) {
    return this->asset_service_server_Stub.AsyncGetPointCloud(request);
}

std::future<voxel::assets::v1::ListPointCloudsResponse> GrpcClient::list_point_clouds_async(const voxel::assets::v1::ListPointCloudsRequest &request) {
    return this->asset_service_server_Stub.AsyncListPointClouds(request);
}

voxel::dialogue::v1::AnswerChunk GrpcClient::fetch_answer(const voxel::dialogue::v1::AskRequest &request) {
    return this->dialogue_service_server_Stub.Ask(request);
}
std::future<voxel::dialogue::v1::AnswerChunk> GrpcClient::fetch_answer_async(const voxel::dialogue::v1::AskRequest &request) {
    return this->dialogue_service_server_Stub.AsyncAsk(request);
}

voxel::dialogue::v1::GetGuruProfileResponse GrpcClient::fetch_guru_profile(const voxel::dialogue::v1::GetGuruProfileRequest &request) {
    return this->dialogue_service_server_Stub.GetGuruProfile(request);
}
std::future<voxel::dialogue::v1::GetGuruProfileResponse> GrpcClient::fetch_guru_profile_async(const voxel::dialogue::v1::GetGuruProfileRequest &request) {
    return this->dialogue_service_server_Stub.AsyncGetGuruProfile(request);
}






