package com.arvatar.vortex.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "temporal")
public class TemporalProperties {

    private String target = "127.0.0.1:7233";
    private String namespace = "default";
    private final TaskQueues taskQueues = new TaskQueues();

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public TaskQueues getTaskQueues() {
        return taskQueues;
    }

    public static class TaskQueues {
        private String asr = "asr-jobs";
        private String pcd = "pcd-jobs";

        public String getAsr() {
            return asr;
        }

        public void setAsr(String asr) {
            this.asr = asr;
        }

        public String getPcd() {
            return pcd;
        }

        public void setPcd(String pcd) {
            this.pcd = pcd;
        }
    }
}

