package com.arvatar.vortex.temporal;

import com.arvatar.vortex.temporal.activities.AsrActivities;
import com.arvatar.vortex.temporal.activities.PcdActivities;
import com.arvatar.vortex.temporal.workflow.AsrWorkflowImpl;
import com.arvatar.vortex.temporal.workflow.PcdWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Configuration
@EnableConfigurationProperties(TemporalProperties.class)
public class TemporalConfig {

    @Bean
    public WorkflowServiceStubs workflowServiceStubs(TemporalProperties properties) {
        WorkflowServiceStubsOptions.Builder optionsBuilder = WorkflowServiceStubsOptions.newBuilder();
        if (properties.getTarget() != null && !properties.getTarget().isBlank()) {
            optionsBuilder.setTarget(properties.getTarget());
        }
        return WorkflowServiceStubs.newServiceStubs(optionsBuilder.build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs workflowServiceStubs, TemporalProperties properties) {
        WorkflowClientOptions.Builder clientOptions = WorkflowClientOptions.newBuilder();
        if (properties.getNamespace() != null && !properties.getNamespace().isBlank()) {
            clientOptions.setNamespace(properties.getNamespace());
        }
        return WorkflowClient.newInstance(workflowServiceStubs, clientOptions.build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        return WorkerFactory.newInstance(workflowClient);
    }

    @Bean
    public WorkerLifecycle temporalWorkerLifecycle(WorkerFactory workerFactory,
                                                   TemporalProperties properties,
                                                   AsrActivities asrActivities,
                                                   PcdActivities pcdActivities) {
        return new WorkerLifecycle(workerFactory, properties, asrActivities, pcdActivities);
    }

    public static class WorkerLifecycle {
        private final WorkerFactory workerFactory;
        private final TemporalProperties properties;
        private final AsrActivities asrActivities;
        private final PcdActivities pcdActivities;

        public WorkerLifecycle(WorkerFactory workerFactory,
                               TemporalProperties properties,
                               AsrActivities asrActivities,
                               PcdActivities pcdActivities) {
            this.workerFactory = workerFactory;
            this.properties = properties;
            this.asrActivities = asrActivities;
            this.pcdActivities = pcdActivities;
        }

        @PostConstruct
        public void start() {
            Worker asrWorker = workerFactory.newWorker(properties.getTaskQueues().getAsr());
            asrWorker.registerWorkflowImplementationTypes(AsrWorkflowImpl.class);
            asrWorker.registerActivitiesImplementations(asrActivities);

            Worker pcdWorker = workerFactory.newWorker(properties.getTaskQueues().getPcd());
            pcdWorker.registerWorkflowImplementationTypes(PcdWorkflowImpl.class);
            pcdWorker.registerActivitiesImplementations(pcdActivities);

            workerFactory.start();
        }

        @PreDestroy
        public void shutdown() {
            workerFactory.shutdown();
        }
    }
}

