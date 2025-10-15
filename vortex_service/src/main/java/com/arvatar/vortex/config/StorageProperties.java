package com.arvatar.vortex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.storage")
public class StorageProperties {
    private String videoBucket = "videos";
    private String jobBucket = "jobs";
    private final Minio minio = new Minio();

    public String getVideoBucket() {
        return videoBucket;
    }

    public void setVideoBucket(String videoBucket) {
        this.videoBucket = videoBucket;
    }

    public String getJobBucket() {
        return jobBucket;
    }

    public void setJobBucket(String jobBucket) {
        this.jobBucket = jobBucket;
    }

    public Minio getMinio() {
        return minio;
    }

    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String region = "us-east-1";
        private String accessKey = "minio";
        private String secretKey = "minio123";
        private boolean pathStyleAccess = true;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }
    }
}
