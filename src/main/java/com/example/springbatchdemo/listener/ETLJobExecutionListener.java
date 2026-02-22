package com.example.springbatchdemo.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ETLJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ETLJobExecutionListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("=== Job [{}] STARTING ===", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        BatchStatus status = jobExecution.getStatus();
        Duration duration = Duration.between(
                jobExecution.getStartTime(),
                jobExecution.getEndTime() != null ? jobExecution.getEndTime() : jobExecution.getStartTime()
        );

        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            log.info("Step [{}] — read={}, written={}, filtered={}, skipped={}, commitCount={}",
                    stepExecution.getStepName(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getFilterCount(),
                    stepExecution.getSkipCount(),
                    stepExecution.getCommitCount());
        }

        if (status == BatchStatus.COMPLETED) {
            log.info("=== Job [{}] COMPLETED in {} ===",
                    jobExecution.getJobInstance().getJobName(), duration);
        } else {
            log.error("=== Job [{}] FINISHED with status {} in {} ===",
                    jobExecution.getJobInstance().getJobName(), status, duration);
            for (Throwable t : jobExecution.getAllFailureExceptions()) {
                log.error("Failure: {}", t.getMessage(), t);
            }
        }
    }
}
