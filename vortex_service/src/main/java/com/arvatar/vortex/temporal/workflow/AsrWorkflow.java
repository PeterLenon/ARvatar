package com.arvatar.vortex.temporal.workflow;

import com.arvatar.vortex.models.AsrPcdJob;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AsrWorkflow {

    @WorkflowMethod
    void run(AsrPcdJob job);
}

