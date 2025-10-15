package com.arvatar.vortex.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JsonPropertyOrder({"jobId", "guruId", "status", "createdAt", "updatedAt", "video", "output", "metadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobBlob {
    private String jobId;
    private String guruId;
    private JobStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> video = new HashMap<>();
    private Map<String, Object> output = new HashMap<>();
    private Map<String, Object> metadata = new HashMap<>();

    public JobBlob() {
        this.jobId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = JobStatus.QUEUED;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getGuruId() {
        return guruId;
    }

    public void setGuruId(String guruId) {
        this.guruId = guruId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
        touch();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getVideo() {
        return video;
    }

    public void setVideo(Map<String, Object> video) {
        this.video = video;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
