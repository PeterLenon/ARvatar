package com.arvatar.vortex.config;

import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcServerConfig {

    /**
     * Configure gRPC server to accept larger messages.
     * Default max message size is 4MB, we increase it to 100MB to handle large asset transfers.
     */
    @Bean
    public GrpcServerConfigurer grpcServerConfigurer() {
        return serverBuilder -> {
            serverBuilder.maxInboundMessageSize(100 * 1024 * 1024);
            
            // Configure keepalive to prevent connection resets
            if (serverBuilder instanceof NettyServerBuilder) {
                NettyServerBuilder nettyBuilder = (NettyServerBuilder) serverBuilder;
                nettyBuilder.keepAliveTime(30, TimeUnit.SECONDS)
                           .keepAliveTimeout(5, TimeUnit.SECONDS)
                           .permitKeepAliveWithoutCalls(true)
                           .permitKeepAliveTime(10, TimeUnit.SECONDS);
            }
        };
    }
}

