#include <grpcpp/grpcpp.h>
#include <iostream>
#include <vector>
#include <cstdint>
#include "voxel/common/v1/types.pb.h"
#include "voxel/ingest/v1/ingest_service.grpc.pb.h"
#include "voxel/recon/v1/recon_service.grpc.pb.h"

int main(int argc, char** argv) {
  const std::string target = (argc > 1) ? argv[1] : "localhost:50051";

  // Create channel
  auto channel = grpc::CreateChannel(target, grpc::InsecureChannelCredentials());

  // Stubs (note variable names avoid shadowing namespaces)
  auto ingest_stub = voxel::ingest::v1::IngestService::NewStub(channel);
  auto recon_stub  = voxel::recon::v1::ReconService::NewStub(channel);

  // Build a sample message to verify models compile/link
  voxel::common::v1::FrameChunk chunk;
  chunk.set_codec("RAW");
  chunk.set_width(640);
  chunk.set_height(480);
  chunk.set_timestamp_ms(0);
  static const char kDummy[] = "dummy";
  chunk.set_data(kDummy, sizeof(kDummy)-1);

  std::cout << "Device client compiled. Channel to " << target << " created.\n";
  return 0;
}
