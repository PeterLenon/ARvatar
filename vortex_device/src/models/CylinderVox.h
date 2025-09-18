//
// Created by Peter Lenon Goshomi on 9/18/25.
//

#ifndef VORTEXDEVICE_CYLINDERVOX_H
#define VORTEXDEVICE_CYLINDERVOX_H
#include <pcl/point_cloud.h>
#include <pcl/point_types.h>
#include <pcl/io/pcd_io.h>
#include <pcl/io/ply_io.h>
#include <map>

class CylinderVox {
private:
    std::map<int, std::vector<std::tuple<int, int>>> angle_led_map;
public:
    int bin_size = 0;
    CylinderVox(int delta_theta);
    void add_point(int theta, int x_normalized, int y_normalized);
    std::vector<std::tuple<int, int>> get_slice(int theta);
};
#endif //VORTEXDEVICE_CYLINDERVOX_H