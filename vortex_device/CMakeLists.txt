cmake_minimum_required(VERSION 3.20)
project(VortexDevice LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# If using vcpkg, configure with:
#   -DCMAKE_TOOLCHAIN_FILE="$HOME/vcpkg/scripts/buildsystems/vcpkg.cmake"
find_package(Protobuf CONFIG REQUIRED)
list(APPEND CMAKE_MODULE_PATH "${Protobuf_DIR}")
include(ProtobufGenerate)

find_package(gRPC CONFIG REQUIRED)

# ---- Paths ----
set(PROTO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/proto)
set(GEN_DIR    ${CMAKE_CURRENT_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${GEN_DIR})

# ---- Proto list ----
set(PROTOS
        ${PROTO_ROOT}/voxel/common/v1/types.proto
        ${PROTO_ROOT}/voxel/ingest/v1/ingest_service.proto
        ${PROTO_ROOT}/voxel/recon/v1/recon_service.proto
)

# ---- Messages (.pb.*) ----
# Create the target first, then have protobuf_generate append sources to it.
add_library(voxel_proto STATIC
        src/VoxelRenderer.cpp)
target_include_directories(voxel_proto PUBLIC ${GEN_DIR} ${GEN_DIR}/proto)
target_link_libraries(voxel_proto PUBLIC protobuf::libprotobuf)

# Ensure protoc searches in <repo>/proto for imports like "voxel/common/v1/types.proto"
protobuf_generate(
        LANGUAGE cpp
        TARGET         voxel_proto
        PROTOC_OUT_DIR ${GEN_DIR}
        IMPORT_DIRS    ${PROTO_ROOT}
        PROTOS         ${PROTOS}
)

# ---- gRPC stubs (.grpc.pb.*) ----
get_target_property(PROTOC_EXE protobuf::protoc LOCATION)
get_target_property(GRPC_PLUGIN gRPC::grpc_cpp_plugin LOCATION)

add_custom_command(
        OUTPUT
        ${GEN_DIR}/voxel/ingest/v1/ingest_service.grpc.pb.cc
        ${GEN_DIR}/voxel/ingest/v1/ingest_service.grpc.pb.h
        ${GEN_DIR}/voxel/recon/v1/recon_service.grpc.pb.cc
        ${GEN_DIR}/voxel/recon/v1/recon_service.grpc.pb.h
        COMMAND ${PROTOC_EXE}
        --proto_path=${PROTO_ROOT}
        --grpc_out=${GEN_DIR}
        --plugin=protoc-gen-grpc=${GRPC_PLUGIN}
        ${PROTO_ROOT}/voxel/ingest/v1/ingest_service.proto
        ${PROTO_ROOT}/voxel/recon/v1/recon_service.proto
        DEPENDS ${PROTOS}
        VERBATIM
)

add_library(voxel_grpc STATIC
        ${GEN_DIR}/voxel/ingest/v1/ingest_service.grpc.pb.cc
        ${GEN_DIR}/voxel/recon/v1/recon_service.grpc.pb.cc
        src/VoxelRenderer.cpp
)
target_include_directories(voxel_grpc PUBLIC ${GEN_DIR} ${GEN_DIR}/proto)
target_link_libraries(voxel_grpc PUBLIC gRPC::grpc++ protobuf::libprotobuf)

# ---- App(s) ----
add_executable(device_client src/device_client.cc
        src/VoxelRenderer.cpp)
target_include_directories(device_client PRIVATE ${GEN_DIR} ${GEN_DIR}/proto)
target_link_libraries(device_client PRIVATE voxel_proto voxel_grpc gRPC::grpc++ protobuf::libprotobuf)
