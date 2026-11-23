package com.example.clinic.config;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Starter websocket tự đăng ký các executor riêng cho messaging, khiến Spring Boot không còn tạo
// applicationTaskExecutor mặc định -> @Async sẽ rơi về SimpleAsyncTaskExecutor (1 thread mỗi lần gọi).
// Khai báo lại bean mặc định, đúng 2 tên mà Boot vẫn dùng.
@Configuration
public class AsyncConfig {

    @Bean(name = {
            TaskExecutionAutoConfigurationNames.APPLICATION_TASK_EXECUTOR,
            AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME
    })
    public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    static final class TaskExecutionAutoConfigurationNames {
        static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
    }
}
