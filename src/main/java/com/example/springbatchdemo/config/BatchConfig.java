package com.example.springbatchdemo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({BatchProperties.class, S3Properties.class})
public class BatchConfig {
    // Spring Boot 3.2 / Batch 5.1 auto-configures JobRepository, JobLauncher,
    // PlatformTransactionManager from the primary DataSource.
    // No manual @EnableBatchProcessing needed when using Boot's auto-config.
}
