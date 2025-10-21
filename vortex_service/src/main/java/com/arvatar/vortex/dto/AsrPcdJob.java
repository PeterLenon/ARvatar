package com.arvatar.vortex.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AsrPcdJob{
    public UUID jobId;
    public String guruId;
    public String videoKey;
    public String asrResultJsonString;
    public String pcdResultJsonString;
    public JobStatus status;
    public LocalDateTime createdAt;

    public AsrPcdJob (String guru_id, String video_key){
        jobId = UUID.randomUUID();
        guruId = guru_id;
        videoKey = video_key;
        asrResultJsonString = null;
        pcdResultJsonString = null;
        status = JobStatus.ASR_QUEUED;
        createdAt = LocalDateTime.now();
    }
}
