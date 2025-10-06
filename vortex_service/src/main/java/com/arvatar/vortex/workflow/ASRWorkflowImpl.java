package com.arvatar.vortex.workflow;

import com.arvatar.vortex.dto.ASRJob;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class ASRWorkflowImpl implements ASRWorkflow {

    private final ASRActivities activities = Workflow.newActivityStub(
            ASRActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(15))
                    .build());

    @Override
    public void run(ASRJob job) {
        activities.processJob(job);
    }
}
