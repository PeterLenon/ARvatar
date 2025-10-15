package com.arvatar.vortex.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around the asynchronous S3 client used by the services to interact with
 * a MinIO compatible object store. The client exposes convenience helpers for JSON blobs and
 * binary payloads used throughout the ingestion workflows.
 */
public class MinioS3AsyncClient {
    private static final Logger log = LoggerFactory.getLogger(MinioS3AsyncClient.class);

    private final S3AsyncClient s3AsyncClient;
    private final ObjectMapper objectMapper;

    public MinioS3AsyncClient(String endpoint,
                              String region,
                              String accessKey,
                              String secretKey,
                              boolean pathStyleAccess,
                              ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");

        S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .httpClientBuilder(NettyNioAsyncHttpClient.builder())
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());

        if (StringUtils.hasText(endpoint)) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }

        builder = builder.credentialsProvider(resolveCredentialsProvider(accessKey, secretKey));

        this.s3AsyncClient = builder.build();
    }

    private AwsCredentialsProvider resolveCredentialsProvider(String accessKey, String secretKey) {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
        return DefaultCredentialsProvider.create();
    }

    public CompletableFuture<Void> putObject(String bucket, String key, byte[] payload, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) payload.length)
                .build();

        log.debug("Uploading object {} to bucket {} ({} bytes)", key, bucket, payload.length);
        return s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(payload))
                .thenAccept(response -> log.debug("Uploaded object {} to bucket {}", key, bucket));
    }

    public CompletableFuture<Void> putJson(String bucket, String key, Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return putObject(bucket, key, json, "application/json");
        } catch (JsonProcessingException e) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public CompletableFuture<byte[]> getObject(String bucket, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        return s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes())
                .thenApply(response -> {
                    log.debug("Downloaded object {} from bucket {} ({} bytes)", key, bucket, response.asByteArray().length);
                    return response.asByteArray();
                });
    }

    public <T> CompletableFuture<T> getJson(String bucket, String key, Class<T> type) {
        return getObject(bucket, key).thenApply(bytes -> {
            try {
                return objectMapper.readValue(bytes, type);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize JSON from object store", e);
            }
        });
    }
}
