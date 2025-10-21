package com.arvatar.vortex.dto;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.S3Configuration;
import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class MinIOS3Client {
    private final String endpoint = "http://127.0.0.1:9000";
    private final S3AsyncClient asyncS3Client = S3AsyncClient.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("admin", "admin123")))
            .region(Region.US_EAST_1)
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .httpClientBuilder(NettyNioAsyncHttpClient.builder())
            .build();

    public MinIOS3Client() {
    }

    public String putVideo(String guruId, byte[] videoData){
        String videosBucket = "videos";
        try {
            String videoKey = guruId + "/" + Instant.now().toEpochMilli();
            CompletableFuture<PutObjectResponse> response = asyncS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(videosBucket)
                            .key(videoKey)
                            .build(),
                    AsyncRequestBody.fromBytes(videoData)
            );
            response.join();
            return videoKey;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public byte[] getVideo(String videoKey) {
        String bucket = "videos";
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(videoKey)
                    .build();
            ResponseBytes<GetObjectResponse> response = asyncS3Client.getObject(request, AsyncResponseTransformer.toBytes()).join();
            return response.asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch video bytes", e);
        }
    }

    public void updateJob(AsrPcdJob job){
        String jobsBucket = "jobs";
        try{
            String jobKey = job.jobId.toString();
            CompletableFuture<PutObjectResponse> response = asyncS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(jobsBucket)
                            .key(jobKey)
                            .build(),
                    AsyncRequestBody.fromString(job.toString())
            );
            response.join();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public void updateGuruAssetInventory(String guruId, Path file){
        String bucket = "assets";
        String key = guruId + "/" + file.getFileName();
        try{
            CompletableFuture<PutObjectResponse> response = asyncS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build(),
                    AsyncRequestBody.fromFile(file)
            );
            response.join();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}

