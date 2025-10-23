package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.temporal.TemporalProperties;
import com.arvatar.vortex.temporal.workflow.PcdWorkflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowServiceException;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PcdService {
    private final RedisAsyncCommands<String, String> asyncCommands;
    private final StatefulRedisConnection<String, String> connection;
    private final ObjectMapper objectMapper;
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(PcdService.class);
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
    private final WorkflowClient workflowClient;
    private final TemporalProperties temporalProperties;

    PcdService(WorkflowClient workflowClient, TemporalProperties temporalProperties) {
        this.workflowClient = workflowClient;
        this.temporalProperties = temporalProperties;
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");
        this.connection = redisClient.connect();
        this.asyncCommands = connection.async();
    }

    @PostConstruct
    public void start() {
        initializeRedisStreamGroup();
        workerExecutor.submit(this::run);
    }

    private void initializeRedisStreamGroup() {
        String pcdJobRedisStream = "pcd_jobs";
        String pcdJobRedisStreamGroup = "pcd_jobs_workers";

        try {
            RedisCommands<String, String> syncCommands = connection.sync();
            syncCommands.xgroupCreate(XReadArgs.StreamOffset.from(pcdJobRedisStream, "0"), pcdJobRedisStreamGroup, XGroupCreateArgs.Builder.mkstream());
            logger.info("Created Redis stream group: {} for stream: {}", pcdJobRedisStreamGroup, pcdJobRedisStream);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                logger.info("Redis stream group already exists: {} for stream: {}", pcdJobRedisStreamGroup, pcdJobRedisStream);
            } else {
                logger.warn("Failed to create Redis stream group: {} for stream: {}, error: {}", pcdJobRedisStreamGroup, pcdJobRedisStream, e.getMessage());
            }
        }
    }

    private void run() {
        String pcdJobRedisStream = "pcd_jobs";
        String pcdJobRedisStreamGroup = "pcd_jobs_workers";
        String pcdJobRedisStreamConsumer = "pcd_jobs_consumer";
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<StreamMessage<String, String>> jobs = asyncCommands.xreadgroup(
                        Consumer.from(pcdJobRedisStreamGroup, pcdJobRedisStreamConsumer),
                        XReadArgs.Builder.block(Duration.ofSeconds(5)),
                        XReadArgs.StreamOffset.lastConsumed(pcdJobRedisStream)
                ).get();
                if (jobs == null || jobs.isEmpty()) continue;
                for (StreamMessage<String, String> message : jobs) {
                    String messageId = message.getId();
                    Map<String, String> jobEntry = message.getBody();
                    String job = jobEntry.get("job");
                    AsrPcdJob asrPcdJob = objectMapper.readValue(job, AsrPcdJob.class);
                    try {
                        WorkflowOptions options = WorkflowOptions.newBuilder()
                                .setTaskQueue(temporalProperties.getTaskQueues().getPcd())
                                .setWorkflowId("pcd-" + asrPcdJob.jobId)
                                .build();
                        PcdWorkflow workflow = workflowClient.newWorkflowStub(PcdWorkflow.class, options);
                        WorkflowExecution execution = WorkflowClient.start(workflow::run, asrPcdJob);
                        asyncCommands.xack(pcdJobRedisStream, pcdJobRedisStreamGroup, messageId).get();
                        asyncCommands.xdel(pcdJobRedisStream, messageId).get();
                        logger.info("Started PCD workflow for guruId: {} job {} run {}", asrPcdJob.guruId, asrPcdJob.jobId, execution.getRunId());
                    } catch (WorkflowServiceException workflowException) {
                        logger.error("Failed to start PCD workflow for guruId: {} job {}", asrPcdJob.guruId, asrPcdJob.jobId, workflowException);
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                logger.error("PCD stream processing failed, attempting to recover", e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdownNow();
        connection.close();
    }
}

