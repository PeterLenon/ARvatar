package com.arvatar.vortex.dto;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.net.URI;
import java.nio.file.Paths;

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
                    AwsBasicCredentials.create(,
                            System.getenv("peterlenon_access_key"))))
            .forcePathStyle(true)
            .build();

    public MinIOS3Client(String endpoint, String bucket, String defaultRegion) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.defaultRegion = defaultRegion;
    }

    public putVideo(String guruId, )
}

