package com.arvatar.vortex.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
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

    @JsonCreator
    public AsrPcdJob (String guru_id, String video_key){
        jobId = UUID.randomUUID();
        guruId = guru_id;
        videoKey = video_key;
        asrResultJsonString = null;
        status = JobStatus.ASR_QUEUED;
        createdAt = LocalDateTime.now();
    }
}
