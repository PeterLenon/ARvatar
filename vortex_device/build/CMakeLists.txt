cmake_minimum_required(VERSION 3.20)
project(VoxelRenderer LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---- VoxelRenderer Library ----
add_library(voxel_renderer STATIC src/VixelRenderer.cpp)
target_include_directories(voxel_renderer PUBLIC src)
target_link_libraries(voxel_renderer PUBLIC pthread)

# VoxelRenderer demo
add_executable(voxel_demo src/voxel_demo.cc)
target_link_libraries(voxel_demo PRIVATE voxel_renderer pthread)
