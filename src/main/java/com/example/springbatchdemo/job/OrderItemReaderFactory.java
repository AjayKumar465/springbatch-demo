package com.example.springbatchdemo.job;

import com.example.springbatchdemo.config.S3Properties;
import com.example.springbatchdemo.domain.RawOrderRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class OrderItemReaderFactory {

    private static final Logger log = LoggerFactory.getLogger(OrderItemReaderFactory.class);

    private static final String[] FIELD_NAMES = {
            "customerId", "customerName", "customerEmail",
            "orderId", "orderDate", "orderAmount", "orderStatus",
            "transactionId", "transactionType", "transactionAmount", "transactionStatus"
    };

    private final S3Properties s3Properties;
    private final ResourceLoader resourceLoader;

    public OrderItemReaderFactory(S3Properties s3Properties, ResourceLoader resourceLoader) {
        this.s3Properties = s3Properties;
        this.resourceLoader = resourceLoader;
    }

    public FlatFileItemReader<RawOrderRecord> create() {
        Resource resource = resolveResource();
        log.info("Reading order data from: {}", resource.getDescription());

        BeanWrapperFieldSetMapper<RawOrderRecord> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(RawOrderRecord.class);

        return new FlatFileItemReaderBuilder<RawOrderRecord>()
                .name("orderItemReader")
                .resource(resource)
                .linesToSkip(1)
                .delimited()
                .names(FIELD_NAMES)
                .fieldSetMapper(fieldSetMapper)
                .strict(true)
                .build();
    }

    private Resource resolveResource() {
        if (s3Properties.isUseLocalFile()) {
            log.info("Using local file: {}", s3Properties.getLocalFilePath());
            return new FileSystemResource(s3Properties.getLocalFilePath());
        }
        String s3Uri = String.format("s3://%s/%s", s3Properties.getBucket(), s3Properties.getKey());
        log.info("Using S3 resource: {}", s3Uri);
        return resourceLoader.getResource(s3Uri);
    }
}
