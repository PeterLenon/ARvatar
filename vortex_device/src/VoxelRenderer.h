//
// Created by Peter Lenon Goshomi on 9/13/25.
//

#ifndef VOXELRENDERER_H
#define VOXELRENDERER_H
#include <string>
#include <fstream>

struct Point{
    float x;
    float y;
    float z;
};

struct Slice2D {
    std::unordered_set<uint64_t> occ;
};

struct CylVox {
    float s_r, s_z, dtheta_deg;
    float sx_xy, z_min;
    std::vector<Slice2D> slices;
};

class VoxelRenderer {
private:
    std::ifstream pcdAssetFile;
    std::ofstream voxelAssetFile;
    std::vector<std::uint8_t> buffer;

    std::vector<Point> load_pcd_or_ply_points(const std::string& path);
    bool voxelize_loaded_asset_points(std::vector<Point> pcdAssetPoints);
    CylVox load_voxel_asset_file(const std::vector<Point>& pcdAssetPoints, float s_r, float s_z, float dtheta_deg, float sx_xy);
public:
    bool loadAsset(const std::string& path);
    Frame getNextFrame(double phaseAngle);
};



#endif //VOXELRENDERER_H
