//
// Created by Peter Lenon Goshomi on 9/16/25.
//

#include <map>
#include "models/CylinderVox.h"

CylinderVox::CylinderVox(int delta_theta){
    this->bin_size = 360/delta_theta;
}

void CylinderVox::add_point(double theta, int x_normalized, int y_normalized) {
    this->angle_led_map[theta].emplace_back(x_normalized, y_normalized);
}

std::vector<std::tuple<int, int>> CylinderVox::get_slice(double theta) {
    return angle_led_map[(int)theta];
}
