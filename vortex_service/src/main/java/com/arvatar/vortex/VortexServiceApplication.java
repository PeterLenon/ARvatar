package com.arvatar.vortex;

import com.arvatar.vortex.workflow.ASRActivities;
import com.arvatar.vortex.workflow.ASRWorkflowImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.lettuce.core.RedisClient;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.CommandLineRunner;

@SpringBootApplication
public class VortexServiceApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(VortexServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(VortexServiceApplication.class, args);
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(@Value("${vortex.redis.url:redis://localhost:6379}") String redisUrl) {
        return RedisClient.create(redisUrl);
    }

    @Bean(destroyMethod = "shutdown")
    public WorkflowServiceStubs workflowServiceStubs(
            @Value("${vortex.temporal.target:127.0.0.1:7233}") String target) {
        WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .build();
        return WorkflowServiceStubs.newInstance(options);
    }

    @Bean(destroyMethod = "close")
    public WorkflowClient workflowClient(
            WorkflowServiceStubs serviceStubs,
            @Value("${vortex.temporal.namespace:default}") String namespace) {
        WorkflowClientOptions options = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build();
        return WorkflowClient.newInstance(serviceStubs, options);
    }

    @Bean(destroyMethod = "shutdown")
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        return WorkerFactory.newInstance(workflowClient);
    }

    @Bean
    public Worker asrTemporalWorker(
            WorkerFactory workerFactory,
            ASRActivities asrActivities,
            @Value("${vortex.temporal.task-queue:ASR_TASK_QUEUE}") String taskQueue) {
        Worker worker = workerFactory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(ASRWorkflowImpl.class);
        worker.registerActivitiesImplementations(asrActivities);
        LOGGER.info("Registered Temporal ASR worker on task queue {}", taskQueue);
        return worker;
    }

    @Bean
    public CommandLineRunner startTemporalWorkers(WorkerFactory workerFactory) {
        return args -> {
            LOGGER.info("Starting Temporal worker factory for ASR workflows");
            workerFactory.start();
        };
    }
}
