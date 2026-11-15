package com.gramsetu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for the WhatsApp audio follow-up (text-to-speech, upload, send).
 *
 * The text reply is sent first and the audio is produced in the background. A bounded pool
 * replaces one new thread per message: a burst of messages queues up (50 jobs) and, once the queue
 * is full, further audio jobs are rejected and skipped (the farmer still gets the text reply).
 */
@Configuration
public class AsyncConfig {

    public static final String AUDIO_REPLY_EXECUTOR = "audioReplyExecutor";
    public static final String MESSAGE_REPLY_EXECUTOR = "messageReplyExecutor";

    /**
     * Runs a whole incoming WhatsApp message (AI call and reply) after Twilio has been given its 200, so a slow AI
     * answer never hits Twilio's webhook timeout. Kept apart from the audio pool so slow speech jobs cannot starve text replies.
     */
    @Bean(name = MESSAGE_REPLY_EXECUTOR)
    public ThreadPoolTaskExecutor messageReplyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("msg-reply-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(name = AUDIO_REPLY_EXECUTOR)
    public ThreadPoolTaskExecutor audioReplyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("audio-reply-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
