package com.arvatar.vortex.config;

import com.arvatar.vortex.dto.MinioS3AsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {

    @Bean
    public MinioS3AsyncClient minioS3AsyncClient(StorageProperties storageProperties,
                                                 ObjectMapper objectMapper) {
        StorageProperties.Minio minio = storageProperties.getMinio();
        return new MinioS3AsyncClient(
                minio.getEndpoint(),
                minio.getRegion(),
                minio.getAccessKey(),
                minio.getSecretKey(),
                minio.isPathStyleAccess(),
                objectMapper
        );
    }
}
