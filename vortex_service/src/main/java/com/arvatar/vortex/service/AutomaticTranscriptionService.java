package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.ASRJob;
import com.arvatar.vortex.dto.MinIOS3Client;
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
    private final MinIOS3Client minIOS3Client = new MinIOS3Client();

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

    private String determineWorkflowId(ASRJob asrJob) {
        if (asrJob != null && asrJob.job_id != null && !asrJob.job_id.isEmpty()) {
            return "asr-job-" + asrJob.job_id;
        }
        return "asr-job-" + UUID.randomUUID();
    }
}
