package com.arvatar.vortex.temporal.activities;

import com.arvatar.vortex.models.AsrPcdJob;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface AsrActivities {
    void executeAsrJob(AsrPcdJob job);
}

