package com.arvatar.vortex.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AsrPcdJob{
    public UUID jobId;
    public String guruId;
    public String videoKey;
    public String asrResultJsonString;
    public JobStatus status;
    public LocalDateTime createdAt;

    public AsrPcdJob() {
    }

    @JsonCreator
    public AsrPcdJob (@JsonProperty("jobId") UUID jobId,
                      @JsonProperty("guruId") String guru_id, 
                      @JsonProperty("videoKey") String video_key,
                      @JsonProperty("status") JobStatus status,
                      @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.jobId = jobId != null ? jobId : UUID.randomUUID();
        this.guruId = guru_id;
        this.videoKey = video_key;
        this.status = status != null ? status : JobStatus.ASR_QUEUED;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.asrResultJsonString = null;
    }

    public AsrPcdJob(String guru_id, String video_key) {
        this.jobId = UUID.randomUUID();
        this.guruId = guru_id;
        this.videoKey = video_key;
        this.asrResultJsonString = null;
        this.status = JobStatus.ASR_QUEUED;
        this.createdAt = LocalDateTime.now();
    }
}
