package com.arvatar.vortex.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum JobStatus {
    QUEUED("QUEUED"),
    ASR_FAILED("ASR-FAILED"),
    ASR_COMPLETE("ASR-COMPLETE"),
    ASR_IN_PROGRESS("ASR-IN-PROGRESS"),
    PCD_IN_PROGRESS("PCD-IN-PROGRESS"),
    PCD_FAILED("PCD-FAILED"),
    COMPLETE("COMPLETE");

    private final String value;

    JobStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static JobStatus fromValue(String value) {
        for (JobStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown job status: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
