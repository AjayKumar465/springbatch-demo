package com.example.springbatchdemo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Marker configuration that activates Spring Cloud AWS auto-configuration
 * when running against a real S3 bucket (i.e. use-local-file=false).
 *
 * When {@code app.s3.use-local-file=true} the reader falls back to a
 * classpath/file-system resource and no AWS client is needed.
 */
@Configuration
@ConditionalOnProperty(name = "app.s3.use-local-file", havingValue = "false", matchIfMissing = true)
public class S3Config {
    // Spring Cloud AWS auto-configuration picks up region + credentials
    // from environment / application-*.yml automatically.
}
