package com.arvatar.vortex.dto;

import com.arvatar.vortex.models.AsrPcdJob;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MinIOS3Client {
    private final String endpoint = System.getenv().getOrDefault("MINIO_ENDPOINT", "http://localhost:9000");
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

    private void ensureBucketExists(String bucketName){
        try{
            asyncS3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).join();
        }catch (Exception e){
            asyncS3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();
        }
    }

    public MinIOS3Client() {
    }

    public String putVideo(String guruId, byte[] videoData){
        String videosBucket = "videos";
        ensureBucketExists(videosBucket);
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
        ensureBucketExists(bucket);
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
        ensureBucketExists(jobsBucket);
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
        ensureBucketExists(bucket);
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

    public void updateGuruAssetInventory(String guruId, List<String> assetIds){
        String bucket = "assets";
        ensureBucketExists(bucket);
        String key = guruId + "/assetIds.txt";
        List<String> storedAssetIds = new ArrayList<>();
        try{
            storedAssetIds.addAll(this.listAvailableGuruAssets(guruId));
        }catch (Exception ignored){}
        storedAssetIds.addAll(assetIds);
        Set<String> assetIdSet = new HashSet<>(storedAssetIds);
        assetIds.clear();
        assetIds.addAll(assetIdSet);
        try{
            CompletableFuture<PutObjectResponse> response = asyncS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build(),
                    AsyncRequestBody.fromString(String.join("\n", assetIds))
            );
            response.join();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public List<String> listAvailableGuruAssets(String guruId){
        String bucket = "assets";
        ensureBucketExists(bucket);
        String key = guruId + "/assetIds.txt";
        try{
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> response = asyncS3Client.getObject(request, AsyncResponseTransformer.toBytes()).join();
            return List.of(response.asUtf8String().split("\n"));
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}

