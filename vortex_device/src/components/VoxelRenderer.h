//
// Created by Peter Lenon Goshomi on 9/13/25.
//

#ifndef VOXELRENDERER_H
#define VOXELRENDERER_H
#include <string>
#include <fstream>
#include <unordered_set>
#include <pcl/io/pcd_io.h>
#include <pcl/io/ply_io.h>
#include <pcl/point_types.h>
#include <pcl/point_cloud.h>
#include <vector>
#include "../models/CylinderVox.h"
#include "Eigen/Dense"

class VoxelRenderer {
    private:
    const int DELTA_THETA = 2;
    Eigen::Vector4f get_pcd_centroid(const pcl::PointCloud<pcl::PointXYZRGB>::Ptr&);
    const int S_X = 1;
    const int S_Y = 1;
    const int S_Z = 1;
    CylinderVox cylindricalized_voxel= CylinderVox(DELTA_THETA);

    public:
    VoxelRenderer();
    void load_model(std::string);
    std::vector<std::tuple<int, int>> get_next_slice(int);
};

#endif //VOXELRENDERER_H
