package com.arvatar.vortex.temporal.workflow;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.temporal.activities.PcdActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class PcdWorkflowImpl implements PcdWorkflow {

    private final PcdActivities activities = Workflow.newActivityStub(
            PcdActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofHours(4))
                    .build()
    );

    @Override
    public void run(AsrPcdJob job) {
        activities.executePcdJob(job);
    }
}

