#include <grpcpp/grpcpp.h>
#include <iostream>
#include "voxel/common/v1/types.pb.h"
#include "voxel/asset/v1/asset_service.grpc.pb.h"
#include "voxel/dialogue/v1/dialogue_service.grpc.pb.h"

int main(int argc, char** argv) {
  const std::string target = (argc > 1) ? argv[1] : "localhost:50051";

  // Create channel
  auto channel = grpc::CreateChannel(target, grpc::InsecureChannelCredentials());

  // Stubs (note variable names avoid shadowing namespaces)
  auto asset_stub    = voxel::assets::v1::AssetService::NewStub(channel);
  auto dialogue_stub = voxel::dialogue::v1::DialogueService::NewStub(channel);

  // Build sample messages to verify models compile/link with the refactored protos
  voxel::assets::v1::GetPointCloudRequest asset_request;
  asset_request.set_guru_id("demo-guru");
  asset_request.set_variant("neutral");
  asset_request.set_include_mesh(true);

  voxel::dialogue::v1::AskRequest ask_request;
  ask_request.set_guru_id("demo-guru");
  ask_request.set_user_query("Hello Guru!");
  ask_request.add_context_tags("intro");
  ask_request.set_return_audio(false);
  ask_request.set_return_lipsync(true);

  voxel::common::v1::GuruProfile profile;
  profile.set_guru_id("demo-guru");
  profile.set_display_name("Demo Guru");

  std::cout << "Device client compiled. Channel to " << target << " created.\n";
  return 0;
}
