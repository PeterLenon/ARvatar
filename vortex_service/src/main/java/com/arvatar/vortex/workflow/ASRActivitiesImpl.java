package com.arvatar.vortex.workflow;

import com.arvatar.vortex.dto.ASRJob;
import com.arvatar.vortex.service.AutomaticTranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ASRActivitiesImpl implements ASRActivities {
    private static final Logger LOGGER = LoggerFactory.getLogger(ASRActivitiesImpl.class);

    private final AutomaticTranscriptionService transcriptionService;

    public ASRActivitiesImpl(AutomaticTranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @Override
    public void processJob(ASRJob job) {
        LOGGER.info("Running ASR activity for job {}", job != null ? job.job_id : "unknown");
        transcriptionService.processJob(job);
    }
}
