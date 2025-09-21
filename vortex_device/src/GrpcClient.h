//
// Created by Peter Lenon Goshomi on 9/18/25.
//

#ifndef VORTEXDEVICE_GRPCCLIENT_H
#define VORTEXDEVICE_GRPCCLIENT_H
#include <future>
#include <string>
#include <grpcpp/channel.h>
#include "voxel/asset/v1/asset_service.pb.h"
#include "voxel/dialogue/v1/dialogue_service.pb.h"
#include "voxel/asset/v1/asset_service.grpc.pb.h"
#include "voxel/dialogue/v1/dialogue_service.grpc.pb.h"


class GrpcClient {
    private:
        std::shared_ptr<grpc::Channel> communication_channel = nullptr;
        std::unique_ptr<voxel::assets::v1::AssetService::Stub> asset_service_server_Stub = nullptr;
        std::unique_ptr<voxel::dialogue::v1::DialogueService::Stub> dialogue_service_server_Stub = nullptr;
        grpc::ClientContext context = grpc::ClientContext();

    public:
    explicit GrpcClient(const std::string& target_port);
    ~GrpcClient();
    ::grpc::Status fetch_point_cloud(const voxel::assets::v1::GetPointCloudRequest& request, voxel::assets::v1::GetPointCloudResponse* response);
    ::grpc::Status list_point_clouds(const voxel::assets::v1::ListPointCloudsRequest& request, voxel::assets::v1::ListPointCloudsResponse* response);
    std::unique_ptr< ::grpc::ClientAsyncResponseReader<voxel::assets::v1::GetPointCloudResponse> > fetch_point_cloud_async(const voxel::assets::v1::GetPointCloudRequest& request, ::grpc::CompletionQueue* cq);
    std::unique_ptr< ::grpc::ClientAsyncResponseReader<voxel::assets::v1::ListPointCloudsResponse> > list_point_clouds_async(const voxel::assets::v1::ListPointCloudsRequest& request, ::grpc::CompletionQueue* cq);

    std::unique_ptr< ::grpc::ClientReader<voxel::dialogue::v1::AnswerChunk> > fetch_answer(const voxel::dialogue::v1::AskRequest& request);
    std::unique_ptr< ::grpc::ClientAsyncReader<voxel::dialogue::v1::AnswerChunk> > fetch_answer_async(const voxel::dialogue::v1::AskRequest& request, ::grpc::CompletionQueue* cq, void* tag);

    ::grpc::Status fetch_guru_profile(const voxel::dialogue::v1::GetGuruProfileRequest& request, voxel::dialogue::v1::GetGuruProfileResponse* response);
    std::unique_ptr< ::grpc::ClientAsyncResponseReader<voxel::dialogue::v1::GetGuruProfileResponse> > fetch_guru_profile_async(const voxel::dialogue::v1::GetGuruProfileRequest& request, ::grpc::CompletionQueue* cq);
};


#endif //VORTEXDEVICE_GRPCCLIENT_H