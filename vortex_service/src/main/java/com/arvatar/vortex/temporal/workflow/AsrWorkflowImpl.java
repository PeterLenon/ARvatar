package com.arvatar.vortex.temporal.workflow;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.temporal.activities.AsrActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class AsrWorkflowImpl implements AsrWorkflow {

    private final AsrActivities activities = Workflow.newActivityStub(
            AsrActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofHours(1))
                    .build()
    );

    @Override
    public void run(AsrPcdJob job) {
        activities.executeAsrJob(job);
    }
}

