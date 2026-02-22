package com.example.springbatchdemo.job;

import com.example.springbatchdemo.config.BatchProperties;
import com.example.springbatchdemo.domain.OrderDataComposite;
import com.example.springbatchdemo.domain.RawOrderRecord;
import com.example.springbatchdemo.listener.ETLJobExecutionListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class OrderETLJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchProperties batchProperties;
    private final OrderItemReaderFactory readerFactory;
    private final OrderDataItemProcessor processor;
    private final OrderDataCompositeItemWriter writer;
    private final ETLJobExecutionListener jobListener;

    public OrderETLJobConfig(JobRepository jobRepository,
                             PlatformTransactionManager transactionManager,
                             BatchProperties batchProperties,
                             OrderItemReaderFactory readerFactory,
                             OrderDataItemProcessor processor,
                             OrderDataCompositeItemWriter writer,
                             ETLJobExecutionListener jobListener) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.batchProperties = batchProperties;
        this.readerFactory = readerFactory;
        this.processor = processor;
        this.writer = writer;
        this.jobListener = jobListener;
    }

    @Bean
    public FlatFileItemReader<RawOrderRecord> orderItemReader() {
        return readerFactory.create();
    }

    @Bean
    public Step orderETLStep(FlatFileItemReader<RawOrderRecord> orderItemReader) {
        return new StepBuilder("orderETLStep", jobRepository)
                .<RawOrderRecord, OrderDataComposite>chunk(batchProperties.getChunkSize(), transactionManager)
                .reader(orderItemReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skipLimit(batchProperties.getSkipLimit())
                .build();
    }

    @Bean
    public Job orderETLJob(Step orderETLStep) {
        return new JobBuilder("orderETLJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(orderETLStep)
                .build();
    }
}
