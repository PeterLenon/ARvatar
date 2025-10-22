package com.arvatar.vortex.controller;

import com.arvatar.vortex.service.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import voxel.assets.v1.AssetServiceOuterClass.*;

@RestController
@RequestMapping("/api/v1/assets")
@CrossOrigin(origins = "*")
public class AssetController {

    @Autowired
    private AssetService assetService;

    /**
     * List all available point clouds
     */
    @GetMapping("/point-clouds")
    public ResponseEntity<ListPointCloudsResponse> listPointClouds(
            @RequestParam(required = true) String guruId) {
        
        ListPointCloudsRequest request = ListPointCloudsRequest.newBuilder()
                .setGuruId(guruId)
                .build();
        
        ListPointCloudsResponse response = assetService.listPointClouds(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a specific point cloud
     */
    @GetMapping("/point-clouds/{guruId}")
    public ResponseEntity<GetPointCloudResponse> getPointCloud(
            @PathVariable String guruId,
            @RequestParam(required = false) String variant,
            @RequestParam(required = false) Boolean includeMesh) {
        
        GetPointCloudRequest request = GetPointCloudRequest.newBuilder()
                .setGuruId(guruId)
                .setVariant(variant != null ? variant : "neutral")
                .setIncludeMesh(includeMesh != null ? includeMesh : false)
                .build();
        
        GetPointCloudResponse response = assetService.getPointCloud(request);
        return ResponseEntity.ok(response);
    }
}
