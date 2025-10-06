package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.ASRJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;

import com.arvatar.vortex.workflow.ASRWorkflow;

@Service
public class AutomaticTranscriptionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticTranscriptionService.class);

    private final ObjectMapper objectMapper;
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> redisConnection;
    private final RedisCommands<String, String> redisCommands;
    private final String redisServerURL;
    private final Object redisLock = new Object();
    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public AutomaticTranscriptionService(
            ObjectMapper objectMapper,
            RedisClient redisClient,
            @Value("${vortex.redis.url:redis://localhost:6379}") String redisServerURL,
            WorkflowClient workflowClient,
            @Value("${vortex.temporal.task-queue:ASR_TASK_QUEUE}") String taskQueue) {
        this.objectMapper = objectMapper;
        this.redisClient = redisClient;
        this.redisServerURL = redisServerURL;
        this.redisConnection = this.redisClient.connect();
        this.redisCommands = this.redisConnection.sync();
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
        LOGGER.info("AutomaticTranscriptionService connected to Redis at {}", this.redisServerURL);
    }

    @PreDestroy
    public void shutdown() {
        if (redisConnection != null) {
            redisConnection.close();
        }
    }

    public String enlistASRjob(ASRJob asrJob) {
        try {
            markJobQueued(asrJob);
            String workflowId = determineWorkflowId(asrJob);
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId(workflowId)
                    .build();

            ASRWorkflow workflow = workflowClient.newWorkflowStub(ASRWorkflow.class, options);
            WorkflowExecution execution = WorkflowClient.start(workflow::run, asrJob);

            LOGGER.info("Started Temporal workflow {} for ASR job {}", execution.getWorkflowId(), asrJob.job_id);
            return execution.getWorkflowId();
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            LOGGER.warn("Temporal workflow {} already exists for ASR job {}", alreadyStarted.getExecution().getWorkflowId(),
                    asrJob.job_id);
            return alreadyStarted.getExecution().getWorkflowId();
        }
    }

    public String getASRjobStatus(String jobId) {
        if (jobId == null) {
            return null;
        }
        synchronized (redisLock) {
            return redisCommands.get(jobStatusKey(jobId));
        }
    }

    public void processJob(ASRJob asrJob) {
        markJobStarted(asrJob);
        try {
            LOGGER.info("Processing ASR job {} for guru {}", asrJob.job_id, asrJob.guru_id);
            // Placeholder for actual transcription logic. In a real implementation this would invoke
            // the ASR engine and populate outputs (transcripts, phonemes, etc.).
            markJobCompleted(asrJob);
        } catch (RuntimeException e) {
            markJobFailed(asrJob, e);
            throw e;
        } catch (Exception e) {
            markJobFailed(asrJob, e);
            throw new IllegalStateException("Unexpected error while processing ASR job", e);
        }
    }

    public void markJobQueued(ASRJob asrJob) {
        asrJob.status = "queued";
        asrJob.queued_at = Instant.now().toString();
        asrJob.error = null;
        persistJob(asrJob);
    }

    public void markJobStarted(ASRJob asrJob) {
        asrJob.status = "running";
        asrJob.started_at = Instant.now().toString();
        asrJob.error = null;
        persistJob(asrJob);
    }

    public void markJobFailed(ASRJob asrJob, Exception exception) {
        asrJob.status = "failed";
        asrJob.error = exception.getMessage();
        asrJob.finished_at = Instant.now().toString();
        persistJob(asrJob);
    }

    public void markJobCompleted(ASRJob asrJob) {
        asrJob.status = "succeeded";
        asrJob.finished_at = Instant.now().toString();
        asrJob.error = null;
        persistJob(asrJob);
    }

    private void persistJob(ASRJob asrJob) {
        if (asrJob == null || asrJob.job_id == null) {
            LOGGER.warn("Skipping job persistence because job or job_id is null");
            return;
        }
        try {
            String serialized = objectMapper.writeValueAsString(asrJob);
            synchronized (redisLock) {
                redisCommands.set(jobStatusKey(asrJob.job_id), serialized);
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to persist ASR job status for {}", asrJob.job_id, e);
        }
    }

    private String jobStatusKey(String jobId) {
        return String.format("JOB:ASR:%s", jobId);
    }

    private String determineWorkflowId(ASRJob asrJob) {
        if (asrJob != null && asrJob.job_id != null && !asrJob.job_id.isEmpty()) {
            return "asr-job-" + asrJob.job_id;
        }
        return "asr-job-" + UUID.randomUUID();
    }
}
