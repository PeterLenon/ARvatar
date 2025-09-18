cmake_minimum_required(VERSION 3.20)
project(VortexDevice LANGUAGES CXX)

# ---------------- Core toolchain ----------------
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---------- Homebrew hints (optional, safe to leave in) ----------
# If *_DIR vars aren’t provided (e.g., via CLion CMake options), try to infer them from brew.
# Works on Intel (/usr/local) and Apple Silicon (/opt/homebrew).
function(_hint_pkg _formula _var _suffix)
    if(NOT DEFINED ${_var})
        execute_process(COMMAND brew --prefix ${_formula}
                OUTPUT_VARIABLE _prefix
                OUTPUT_STRIP_TRAILING_WHITESPACE
                ERROR_QUIET)
        if(EXISTS "${_prefix}/${_suffix}")
            set(${_var} "${_prefix}/${_suffix}" CACHE PATH "Hint for ${_formula}" FORCE)
        endif()
    endif()
endfunction()

_hint_pkg(protobuf Protobuf_DIR "lib/cmake/protobuf")
_hint_pkg(grpc     gRPC_DIR     "lib/cmake/grpc")
_hint_pkg(pcl      PCL_DIR      "lib/cmake/pcl")

# ---------------- Dependencies ----------------
find_package(Protobuf CONFIG REQUIRED)      # exports protobuf::libprotobuf, protobuf::protoc
find_package(gRPC     CONFIG REQUIRED)      # exports gRPC::grpc++, gRPC::grpc_cpp_plugin
# PCL exports PCL_INCLUDE_DIRS, PCL_LIBRARIES, PCL_DEFINITIONS (and sometimes component targets)
find_package(PCL 1.15 REQUIRED COMPONENTS common io)

# Make Protobuf helper functions (protobuf_generate) available (Homebrew installs this module)
list(APPEND CMAKE_MODULE_PATH "${Protobuf_DIR}")
include(protobuf-generate)

# Convenience interface to propagate PCL include dirs/defs/libs to any target that needs PCL
add_library(pcl_deps INTERFACE)
target_include_directories(pcl_deps INTERFACE ${PCL_INCLUDE_DIRS})
target_compile_definitions(pcl_deps INTERFACE ${PCL_DEFINITIONS})
target_link_libraries(pcl_deps INTERFACE ${PCL_LIBRARIES})

# ---------------- Paths ----------------
set(PROTO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/proto)
set(GEN_DIR    ${CMAKE_CURRENT_BINARY_DIR}/generated)
file(MAKE_DIRECTORY ${GEN_DIR})

# ---------------- Proto list ----------------
set(PROTOS
        ${PROTO_ROOT}/voxel/common/v1/types.proto
        ${PROTO_ROOT}/voxel/ingest/v1/ingest_service.proto
        ${PROTO_ROOT}/voxel/recon/v1/recon_service.proto
)

# ---------------- Protobuf messages (.pb.*) ----------------
add_library(voxel_proto STATIC)  # sources are generated below and attached via protobuf_generate
target_include_directories(voxel_proto PUBLIC
        ${GEN_DIR}                    # generated headers (e.g., ${GEN_DIR}/voxel/.../*.pb.h)
)
target_link_libraries(voxel_proto PUBLIC protobuf::libprotobuf)

protobuf_generate(
        LANGUAGE       cpp
        TARGET         voxel_proto
        PROTOC_OUT_DIR ${GEN_DIR}
        IMPORT_DIRS    ${PROTO_ROOT}
        PROTOS         ${PROTOS}
)

# ---------------- gRPC stubs (.grpc.pb.*) ----------------
# Use protoc + grpc plugin to generate gRPC C++ sources for the service protos.
add_custom_command(
        OUTPUT
        ${GEN_DIR}/voxel/ingest/v1/ingest_service.grpc.pb.cc
        ${GEN_DIR}/voxel/ingest/v1/ingest_service.grpc.pb.h
        ${GEN_DIR}/voxel/recon/v1/recon_service.grpc.pb.cc
        ${GEN_DIR}/voxel/recon/v1/recon_service.grpc.pb.h
        COMMAND $<TARGET_FILE:protobuf::protoc>
        --proto_path=${PROTO_ROOT}
        --grpc_out=${GEN_DIR}
        --plugin=protoc-gen-grpc=$<TARGET_FILE:gRPC::grpc_cpp_plugin>
        ${PROTO_ROOT}/voxel/ingest/v1/ingest_service.proto
        ${PROTO_ROOT}/voxel/recon/v1/recon_service.proto
        DEPENDS ${PROTOS}
        VERBATIM
)

add_library(voxel_grpc STATIC
        ${GEN_DIR}/voxel/ingest/v1/ingest_service.grpc.pb.cc
        ${GEN_DIR}/voxel/recon/v1/recon_service.grpc.pb.cc
)
target_include_directories(voxel_grpc PUBLIC ${GEN_DIR})
target_link_libraries(voxel_grpc PUBLIC gRPC::grpc++ voxel_proto)

# ---------------- Your source libs/apps ----------------
# Voxel renderer uses PCL
add_library(vortex_renderer STATIC
        src/VoxelRenderer.cpp
)
target_include_directories(vortex_renderer PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/src
)
target_link_libraries(vortex_renderer PUBLIC pcl_deps)

# Device client pulls in renderer (→ PCL), and gRPC stubs
add_executable(device_client
        src/device_client.cc
        src/models/CylinderVox.cpp
        src/models/CylinderVox.h
)
target_include_directories(device_client PRIVATE ${GEN_DIR})
target_link_libraries(device_client PRIVATE vortex_renderer voxel_grpc)

# ---------------- Debug info (optional) ----------------
message(STATUS "PCL_INCLUDE_DIRS = ${PCL_INCLUDE_DIRS}")
message(STATUS "PCL_LIBRARIES    = ${PCL_LIBRARIES}")
message(STATUS "Using Protobuf_DIR = ${Protobuf_DIR}")
message(STATUS "Using gRPC_DIR     = ${gRPC_DIR}")
message(STATUS "Using PCL_DIR      = ${PCL_DIR}")
