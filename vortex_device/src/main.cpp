//
// Created by Peter Lenon Goshomi on 11/1/25.
//

#include <chrono>
#include <iostream>
#include <string>
#include <thread>
#include <vector>
#include <atomic>
#include <csignal>
#include <mutex>
#include <condition_variable>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <memory>
#include <cmath>
#include <filesystem>
#include "components/VoxelRenderer.h"
#include "components/GrpcClient.h"
#include "voxel/asset/v1/asset_service.pb.h"
#include "voxel/common/v1/types.pb.h"
#include <future>

using namespace std;

class VortexDeviceOrchestrator {
private:
        GrpcClient* grpcClient;

public:
    VortexDeviceOrchestrator(std::string connection_port) : grpcClient(  new GrpcClient(connection_port)) {};
    virtual ~VortexDeviceOrchestrator() {
        delete this->grpcClient;
    };

    bool upload_guru_video(const std::string& guru_id, const std::string& video_path) {
        if (!std::filesystem::exists(video_path)) {
            std::cerr << "Error: Video file not found: " << video_path << std::endl;
            return false;
        }

        std::ifstream video_file(video_path, std::ios::binary | std::ios::ate);
        if (!video_file.is_open()) {
            std::cerr << "Error: Failed to open video file: " << video_path << std::endl;
            return false;
        }

        std::streamsize file_size = video_file.tellg();
        video_file.seekg(0, std::ios::beg);

        std::vector<char> buffer(file_size);
        if (!video_file.read(buffer.data(), file_size)) {
            std::cerr << "Error: Failed to read video file: " << video_path << std::endl;
            return false;
        }
        video_file.close();
        std::cout << "Read video file: " << file_size << " bytes" << std::endl;
        std::filesystem::path path_obj(video_path);
        std::string extension = path_obj.extension().string();
        std::string format = extension.empty() ? "mp4" : extension.substr(1);

        std::string mime_type = "video/mp4"; // default
        if (format == "mov") {
            mime_type = "video/quicktime";
        } else if (format == "avi") {
            mime_type = "video/x-msvideo";
        } else if (format == "webm") {
            mime_type = "video/webm";
        } else if (format == "mp4") {
            mime_type = "video/mp4";
        }

        std::cout << "Preparing upload request: guru_id=" << guru_id 
                  << ", format=" << format << ", mime_type=" << mime_type << std::endl;

        voxel::assets::v1::UploadGuruVideoRequest request;
        request.set_guru_id(guru_id);

        voxel::common::v1::Video* video = request.mutable_video();
        video->set_format(format);
        video->set_mime_type(mime_type);
        // Use string constructor for payload to ensure proper handling
        video->set_payload(std::string(buffer.data(), file_size));

        voxel::assets::v1::UploadGuruVideoResponse response;
        std::cout << "Sending gRPC request to server..." << std::endl;
        grpc::Status status = this->grpcClient->upload_guru_video(request, &response);

        if (!status.ok()) {
            std::string error_msg = status.error_message();
            if (error_msg.empty()) {
                error_msg = "(no error message provided)";
            }
            std::cerr << "Error: gRPC call failed: " << error_msg 
                      << " (code: " << status.error_code() << ")" << std::endl;
            
            // Provide helpful diagnostics based on error code
            if (status.error_code() == grpc::StatusCode::UNAVAILABLE) {
                std::cerr << "Hint: Server is not reachable. Is the vortex_service running on port 9091?" << std::endl;
            } else if (status.error_code() == grpc::StatusCode::UNKNOWN) {
                std::cerr << "Hint: Unknown error (code 2). This often indicates:" << std::endl;
                std::cerr << "  1. The server received the request but encountered an internal error" << std::endl;
                std::cerr << "  2. Check the server logs for more details" << std::endl;
                std::cerr << "  3. The request format might not match what the server expects" << std::endl;
                std::cerr << "  4. The payload size might be too large" << std::endl;
            } else if (status.error_code() == grpc::StatusCode::DEADLINE_EXCEEDED) {
                std::cerr << "Hint: Request timed out. The server might be processing a large file." << std::endl;
            }
            return false;
        }

        if (response.success()) {
            std::cout << "Success: " << response.message() << std::endl;
            if (!response.point_cloud_variant().empty()) {
                std::cout << "Generated point cloud variant: " << response.point_cloud_variant() << std::endl;
            }
            return true;
        } else {
            std::cerr << "Error: Upload failed - " << response.message() << std::endl;
            return false;
        }
    };
};


int main(void) {
    string vortex_service_connection_port = "9091";
    VortexDeviceOrchestrator orchestrator = VortexDeviceOrchestrator(vortex_service_connection_port);
    string test_guru_id = "0001";
    string test_video_path ="/Users/pgoshomi/Desktop/arvatar_test4.MOV";
    bool success = orchestrator.upload_guru_video(test_guru_id, test_video_path);
    cout << success << endl;
}