package com.arvatar.vortex.config;

import com.arvatar.vortex.service.AsrWorkflowService;
import com.arvatar.vortex.service.PcdWorkflowService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class WorkflowConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService workflowExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            AsrWorkflowService asrWorkflowService,
            PcdWorkflowService pcdWorkflowService,
            WorkflowProperties workflowProperties) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(StreamOffset.fromStart(workflowProperties.getAsrStream()), asrWorkflowService);
        container.receive(StreamOffset.fromStart(workflowProperties.getPcdStream()), pcdWorkflowService);
        container.start();
        return container;
    }
}
