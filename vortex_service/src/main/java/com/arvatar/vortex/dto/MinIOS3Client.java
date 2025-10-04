package com.arvatar.vortex.dto;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.waiters.S3AsyncWaiter;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;
import voxel.common.v1.Types;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public class MinIOS3Client {
    private String endpoint = "http://127.0.0.1:9000";
    private final String accessUser = "minioadmin";
    private final String accessKey = "minioadmin";
    private Region defaultRegion = Region.US_EAST_1;

    private final S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(defaultRegion)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessUser,accessKey)))
                .serviceConfiguration(b -> b.pathStyleAccessEnabled(true))
                .build();

    private final S3TransferManager s3TransferManager = S3TransferManager.builder()
            .s3Client(this.s3AsyncClient)
            .build();

    private static void ensureBucketExists(S3AsyncClient s3AsyncClient, String bucketName) {
        try {
            s3AsyncClient.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        }catch(NoSuchBucketException e) {
            s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            try(S3AsyncWaiter waiter = s3AsyncClient.waiter()) {
               waiter.waitUntilBucketExists(b -> b.bucket(bucketName))
                  .thenAccept(resp -> resp.matched().response().ifPresent(r -> { /* ... */ }))
                  .join();
            }
        }
    }

    public MinIOS3Client(String endpoint, String defaultRegion) {
        this.endpoint = endpoint;
        this.defaultRegion = Region.of(defaultRegion);
    }
    public MinIOS3Client(String endpoint) {
        this.endpoint = endpoint;
    }
    public MinIOS3Client() {}

    public String putVideo(String guru_id, Types.Video training_video) {
        String videos_bucket = "videos";
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        ensureBucketExists(s3AsyncClient, videos_bucket);
        String objectKey = new StringJoiner("/")
                .add(guru_id)
                .add(timestamp)
                .add("raw_video")
                .add(training_video.getFormat())
                .toString();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(videos_bucket)
                .key(objectKey)
                .contentType(training_video.getFormat())
                .build();
        CompletableFuture<PutObjectResponse> res = s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(training_video.getPayload().toByteArray()));
        return !res.isCompletedExceptionally() ? new StringJoiner("/").add(guru_id).add(timestamp).toString(): null;
        }

    public String putImages(String guru_id, ArrayList<Types.Image> images) {
        String images_bucket = "images";
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        ensureBucketExists(s3AsyncClient, images_bucket);
        boolean failed = false;
        try {
            for (Types.Image image : images) {
                String objectKey = new StringJoiner("/")
                        .add(guru_id)
                        .add(timestamp)
                        .add("img")
                        .add(String.valueOf(images.indexOf(image)))
                        .add(image.getFormat())
                        .toString();
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(images_bucket)
                        .key(objectKey)
                        .contentType(image.getFormat())
                        .build();
                CompletableFuture<PutObjectResponse> res = s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(image.getPayload().toByteArray()));
                failed = res.isCompletedExceptionally();
            }
            return !failed ? new StringJoiner("/").add(guru_id).add(timestamp).toString() : null;
        }catch(Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }
}

