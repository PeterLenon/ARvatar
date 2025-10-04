package com.arvatar.vortex.dto;

import java.util.StringJoiner;

public class ASRJob {
    public String job_id = null;
    public String stage = null;
    public String status = null;
    public String guru_id = null;
    public String video_id = null;
    public String images_id = null;
    public String outputs = null;
    public String error = null;
    public String queued_at = null;
    public String started_at = null;
    public String finished_at = null;

    public ASRJob() {}

    public String toString() {
        return "{" + new StringJoiner(", ").add("job_id: " + job_id).add("stage: " + stage).add("status: " + status).add("guru_id: " + guru_id).add("video_id: " + video_id).add("images_id: " + images_id).add("outputs: " + outputs).add("error: " + error).add("queued_at: " + queued_at).add("started_at: " + started_at).add("finished_at: " + finished_at).toString() + "}";
    }
}
