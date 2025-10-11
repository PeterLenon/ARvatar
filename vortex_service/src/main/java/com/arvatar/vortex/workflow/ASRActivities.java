package com.arvatar.vortex.workflow;

import com.arvatar.vortex.dto.ASRJob;
import voxel.common.v1.Types;

public interface ASRActivities {
    void processJob(ASRJob job);
}
