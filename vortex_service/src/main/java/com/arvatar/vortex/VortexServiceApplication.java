package com.arvatar.vortex;

import org.springframework.boot.SpringApplication;
import com.arvatar.vortex.config.StorageProperties;
import com.arvatar.vortex.config.WorkflowProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, WorkflowProperties.class})
public class VortexServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VortexServiceApplication.class, args);
    }
}
