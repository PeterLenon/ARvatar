package com.arvatar.vortex.service;

import com.arvatar.vortex.dto.ASRJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;


public class AutomaticTranscriptionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticTranscriptionService.class);
    private static final String STREAM_NAME = "QUEUE:ASR_JOBS";
    private static final String GROUP_NAME = "ASR-WORKERS";
    private String redisServerURL = "redis://localhost:6379";
    private RedisClient redisClient = RedisClient.create(this.redisServerURL);
    private StatefulRedisConnection<String, String> redisConnection = redisClient.connect();
    private RedisCommands<String, String> redisCommands = redisConnection.sync();

    public AutomaticTranscriptionService() {}
    public AutomaticTranscriptionService(String redisServerURL) {
        this.redisServerURL = redisServerURL;
        this.redisClient = RedisClient.create(this.redisServerURL);
        this.redisConnection = redisClient.connect();
        this.redisCommands = redisConnection.sync();
    }

    private static void transcribe(ASRJob asrJob) {

    }

    public String enlistASRjob(ASRJob asrJob){
        String job_json = asrJob.toString();
        String message_id = redisCommands.xadd(STREAM_NAME, java.util.Map.of("type", "ASRJob", "job", job_json));
        LOGGER.info("Message ID: {}", message_id);
        return message_id;
    }
    public String getASRjobStatus(String object_store_url){
        return "";
    }
}
