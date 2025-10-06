package com.arvatar.vortex.workflow;

import com.arvatar.vortex.dto.ASRJob;

public interface ASRWorkflow {
    void run(ASRJob job);
}
