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


class GrpcClient {
    private:
        std::shared_ptr<grpc::Channel> communication_channel = nullptr;
        auto asset_service_server_Stub = nullptr;
        auto dialogue_service_server_Stub = nullptr;
    private:

    public:
    explicit GrpcClient(const std::string& target_port);
    ~GrpcClient();
    voxel::assets::v1::GetPointCloudResponse fetch_point_cloud(const voxel::assets::v1::GetPointCloudRequest&);
    voxel::assets::v1::ListPointCloudsResponse list_point_clouds(const voxel::assets::v1::ListPointCloudsRequest&);
    std::future<voxel::assets::v1::GetPointCloudResponse> fetch_point_cloud_async(const voxel::assets::v1::GetPointCloudRequest&);
    std::future<voxel::assets::v1::ListPointCloudsResponse> list_point_clouds_async(const voxel::assets::v1::ListPointCloudsRequest&);

    voxel::dialogue::v1::AnswerChunk fetch_answer(const voxel::dialogue::v1::AskRequest&);
    std::future<voxel::dialogue::v1::AnswerChunk> fetch_answer_async(const voxel::dialogue::v1::AskRequest&);

    voxel::dialogue::v1::GetGuruProfileResponse fetch_guru_profile(const voxel::dialogue::v1::GetGuruProfileRequest&);
    std::future<voxel::dialogue::v1::GetGuruProfileResponse> fetch_guru_profile_async(const voxel::dialogue::v1::GetGuruProfileRequest&);
};


#endif //VORTEXDEVICE_GRPCCLIENT_H