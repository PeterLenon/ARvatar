package com.arvatar.vortex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vortex.workflow")
public class WorkflowProperties {
    private String asrStream = "asr-jobs";
    private String pcdStream = "pcd-jobs";

    public String getAsrStream() {
        return asrStream;
    }

    public void setAsrStream(String asrStream) {
        this.asrStream = asrStream;
    }

    public String getPcdStream() {
        return pcdStream;
    }

    public void setPcdStream(String pcdStream) {
        this.pcdStream = pcdStream;
    }
}
