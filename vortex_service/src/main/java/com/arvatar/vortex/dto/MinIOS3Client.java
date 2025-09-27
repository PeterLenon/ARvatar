package com.arvatar.vortex.dto;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import voxel.common.v1.Types;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.StringJoiner;

public class MinIOS3Client {
    private String endpoint = "http://127.0.0.1:9000";
    private String bucket = "my-bucket";
    private String defaultRegion = "us-east-1";
    private String key = "uploads/example.txt";
    private String path = "/path/to/example.txt";

    private S3Client s3Client = S3Client.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            System.getenv("AWS_ACCESS_KEY_ID"),
                            System.getenv("AWS_ACCESS_KEY_SECRET")))
            )
            .forcePathStyle(true)
            .build();

    public MinIOS3Client(String endpoint, String defaultRegion) {
        this.endpoint = endpoint;
        this.defaultRegion = defaultRegion;
    }
    public MinIOS3Client(String endpoint) {
        this.endpoint = endpoint;
    }
    public MinIOS3Client() {}

    public Boolean putVideo(String guru_id, Types.Video training_video) {
        //todo: ensure that videos are inserted at videos/{guru_id}/timestamp/raw_video.mp4
        String bucket = String.join("/","videos", guru_id, String.valueOf(System.currentTimeMillis()));
        this.s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .build(),
                RequestBody.fromString(training_video.toString())
        );
        return true;
    }

    public Boolean putImages(String guru_id, ArrayList<Types.Image> images) {
        //todo: ensure that videos are inserted at videos/{guru}/timestamp/raw_video.mp4
        return true;
    }

}

