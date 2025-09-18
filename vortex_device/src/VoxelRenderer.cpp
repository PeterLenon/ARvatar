#include "VoxelRenderer.h"
#include <fstream>
#include <pcl/point_cloud.h>
#include <pcl/point_types.h>
#include <pcl/io/pcd_io.h>
#include <pcl/common/centroid.h>
#include "models/CylinderVox.h"

VoxelRenderer::VoxelRenderer() {}

Eigen::Vector4f VoxelRenderer::get_pcd_centroid(const pcl::PointCloud<pcl::PointXYZRGB>::Ptr &point_cloud_pointer) {
    Eigen::Vector4f pcd_centroid;
    pcl::compute3DCentroid(*point_cloud_pointer, pcd_centroid);
    return pcd_centroid;
}

void VoxelRenderer::load_model(std::string path) {
    if (path.substr(path.length() - 4) == ".ply" || path.substr(path.length() - 4) == ".pcd") return;
    pcl::PointCloud<pcl::PointXYZRGB>::Ptr cloud(new pcl::PointCloud<pcl::PointXYZRGB>);
    if (pcl::io::loadPCDFile<pcl::PointXYZRGB>(path, *cloud) == -1) {
        PCL_ERROR("Couldn't read PCD file\n");
        return;
    }
    Eigen::Vector4f pcd_centroid = VoxelRenderer::get_pcd_centroid(cloud);
    int x_norm, y_norm, z_norm;
    for (auto point : *cloud) {
        x_norm = (point.x - pcd_centroid[0])/this->S_X;
        y_norm = (point.y - pcd_centroid[1])/this->S_Y;
        z_norm = (point.z - pcd_centroid[2])/this->S_Z;

        float theta = atan2(z_norm, x_norm);
        this->cylindricalized_voxel.add_point(theta, x_norm, y_norm);
    }
}

std::vector<std::tuple<int, int> > VoxelRenderer::get_next_slice(int theta) {
   if (this->cylindricalized_voxel.bin_size > theta && theta > 0) {
       return this->cylindricalized_voxel.get_slice(theta);
   }
    return this->cylindricalized_voxel.get_slice(0);
};