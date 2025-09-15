//
// Created by Peter Lenon Goshomi on 9/13/25.
//
#include "VoxelRenderer.h"
#include <fstream>
#include <iostream>
#include <vector>
#include <cstdint>
#include <cstddef>
#include "tinyply.h"
#include <pcl/io/pcd_io.h>
#include <pcl/io/ply_io.h>
#include <pcl/point_types.h>
#include <pcl/common/transforms.h>
#include <unordered_set>
#include <cmap>
#include <cstdint>

static inline uint64_t pack2(uint32_t a, uint32_t b) {
    return (uint64_t(a) << 32) | uint64_t(b);
}

bool VoxelRenderer::loadAsset(const std::string& path) {
     try{
          pcl::PointCloud<pcl::PointXYZ> loadedAssetPoints = this->load_pcd_or_ply(path);
     } catch (const std::exception& e) {
        std::cerr << "Error loading asset: " << e.what() << std::endl;
        return false;
     }
     return true;
}

std::vector<Point> VoxelRenderer::load_pcd_or_ply(const std::string& path) {
    pcl::PointCloud<pcl::PointXYZ> cloud;
    if (path.size()>=4 && path.substr(path.size()-4)==".pcd")
        pcl::io::loadPCDFile(path, cloud);
    else
        pcl::io::loadPLYFile(path, cloud);
    std::vector<Pt> out; out.reserve(cloud.size());
    for (auto& p : cloud.points) out.push_back({p.x,p.y,p.z});
    return out;
}

bool VoxelRenderer::voxelize_loaded_asset_points(std::vector<Point> pcdAssetPoints) {}

CylVox voxelizeCyl(const std::vector<Pt>& pts, float s_r, float s_z, float dtheta_deg)
{
    double sx=0, sy=0; float zmin=std::numeric_limits<float>::infinity();
    for (auto& p: pts){ sx+=p.x; sy+=p.y; if(p.z<zmin) zmin=p.z; }
    float cx = pts.empty()?0.f:float(sx/pts.size());
    float cy = pts.empty()?0.f:float(sy/pts.size());
    int N = std::max(1, int(std::round(360.0f / dtheta_deg)));

    CylVox out{ s_r, s_z, dtheta_deg, cx, cy, zmin, std::vector<Slice2D>(N) };
    const float inv_sr = 1.0f/s_r, inv_sz = 1.0f/s_z;

    for (auto& p: pts) {
        float dx = p.x - cx, dy = p.y - cy;
        float r  = std::sqrt(dx*dx + dy*dy);
        float th = std::atan2(dy, dx) * 180.0f / float(M_PI);
        if (th < 0) th += 360.0f;
        int k = int(th / dtheta_deg); if (k >= N) k = N-1;

        uint32_t ir = (uint32_t)std::floor(r * inv_sr);
        uint32_t iz = (uint32_t)std::floor((p.z - out.z_min) * inv_sz);

        out.slices[k].occ.insert(pack2(ir, iz));
    }
    return out;
}{

Frame VoxelRenderer::getNextFrame(double phaseAngle)