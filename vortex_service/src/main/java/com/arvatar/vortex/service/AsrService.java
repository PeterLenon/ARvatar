package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.AsrPcdJob;
import com.arvatar.vortex.temporal.TemporalProperties;
import com.arvatar.vortex.temporal.workflow.AsrWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
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
public class AsrService {
    private RedisAsyncCommands<String, String> asyncCommands;
    private final RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private final ObjectMapper objectMapper;
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(AsrService.class);
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
    private final WorkflowClient workflowClient;
    private final TemporalProperties temporalProperties;

    public AsrService(WorkflowClient workflowClient, TemporalProperties temporalProperties) {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        redisClient = RedisClient.create("redis://localhost:6379");
        connection = redisClient.connect();
        asyncCommands = connection.async();
        this.workflowClient = workflowClient;
        this.temporalProperties = temporalProperties;
    }

    @PostConstruct
    public void start() {
        initializeRedisStreamGroup();
        workerExecutor.submit(this::run);
    }

    private void initializeRedisStreamGroup() {
        String asrJobRedisStream = "asr_jobs";
        String asrJobRedisStreamGroup = "asr_jobs_workers";
        
        try {
            RedisCommands<String, String> syncCommands = connection.sync();
            syncCommands.xgroupCreate(XReadArgs.StreamOffset.from(asrJobRedisStream, "0"), asrJobRedisStreamGroup, XGroupCreateArgs.Builder.mkstream());
            logger.info("Created Redis stream group: {} for stream: {}", asrJobRedisStreamGroup, asrJobRedisStream);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                logger.info("Redis stream group already exists: {} for stream: {}", asrJobRedisStreamGroup, asrJobRedisStream);
            } else {
                logger.warn("Failed to create Redis stream group: {} for stream: {}, error: {}", asrJobRedisStreamGroup, asrJobRedisStream, e.getMessage());
            }
        }
    }

    private void run(){
        String asrJobRedisStream = "asr_jobs";
        String asrJobRedisStreamGroup = "asr_jobs_workers";
        String asrJobRedisStreamConsumer = "asr_jobs_consumer";
        while(!Thread.currentThread().isInterrupted()) {
            try {
                List<StreamMessage<String, String>> jobs = asyncCommands.xreadgroup(
                        Consumer.from(asrJobRedisStreamGroup, asrJobRedisStreamConsumer),
                        XReadArgs.Builder.block(Duration.ofSeconds(5)),
                        XReadArgs.StreamOffset.lastConsumed(asrJobRedisStream)
                ).get();
                if (jobs == null || jobs.isEmpty()) {
                    continue;
                }
                for (StreamMessage<String, String> message : jobs) {
                    String messageId = message.getId();
                    Map<String, String> jobEntry = message.getBody();
                        String job = jobEntry.get("job");
                    AsrPcdJob asrPcdJob = objectMapper.readValue(job, AsrPcdJob.class);
                    try {
                        WorkflowOptions options = WorkflowOptions.newBuilder()
                                .setTaskQueue(temporalProperties.getTaskQueues().getAsr())
                                .setWorkflowId("asr-" + asrPcdJob.jobId)
                                .build();
                        AsrWorkflow workflow = workflowClient.newWorkflowStub(AsrWorkflow.class, options);
                        WorkflowExecution execution = WorkflowClient.start(workflow::run, asrPcdJob);
                        asyncCommands.xack(asrJobRedisStream, asrJobRedisStreamGroup, messageId).get();
                        asyncCommands.xdel(asrJobRedisStream, messageId).get();
                        logger.info("Started ASR workflow for guruId: {} job {} run {}", asrPcdJob.guruId, asrPcdJob.jobId, execution.getRunId());
                    } catch (WorkflowServiceException workflowException) {
                        logger.error("Failed to start ASR workflow for guruId: {} job {}", asrPcdJob.guruId, asrPcdJob.jobId, workflowException);
                        try {
                            asyncCommands.xack(asrJobRedisStream, asrJobRedisStreamGroup, messageId).get();
                            asyncCommands.xdel(asrJobRedisStream, messageId).get();
                        } catch (Exception ackException) {
                            logger.error("Failed to acknowledge ASR job {} after workflow start failure", messageId, ackException);
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                logger.error("ASR stream processing failed, attempting to recover", e);
                rebuildRedisConnection();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdownNow();
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private synchronized void rebuildRedisConnection() {
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception closeException) {
            logger.warn("Failed to close existing Redis connection during rebuild", closeException);
        }
        try {
            connection = redisClient.connect();
            asyncCommands = connection.async();
        } catch (Exception connectionException) {
            logger.error("Failed to rebuild Redis connection", connectionException);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
