package com.example.springbatchdemo.job;

import com.example.springbatchdemo.config.S3Properties;
import com.example.springbatchdemo.domain.RawOrderRecord;
import com.example.springbatchdemo.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderItemReaderFactory {

    private static final Logger log = LoggerFactory.getLogger(OrderItemReaderFactory.class);

    private static final String[] FIELD_NAMES = {
            "customerId", "customerName", "customerEmail",
            "orderId", "orderDate", "orderAmount", "orderStatus",
            "transactionId", "transactionType", "transactionAmount", "transactionStatus"
    };

    private final S3Properties s3Properties;
    private final S3Service s3Service;

    public OrderItemReaderFactory(S3Properties s3Properties,
                                  @Autowired(required = false) S3Service s3Service) {
        this.s3Properties = s3Properties;
        this.s3Service = s3Service;
    }

    public ItemReader<RawOrderRecord> create() {
        if (s3Properties.isUseLocalFile()) {
            Resource resource = resolveLocalResource();
            return buildFlatFileReader(resource);
        }
        if (s3Properties.isUseFolder()) {
            return buildMultiResourceReader();
        }
        Resource resource = resolveS3SingleResource();
        return buildFlatFileReader(resource);
    }

    private FlatFileItemReader<RawOrderRecord> buildFlatFileReader(Resource resource) {
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

    private MultiResourceItemReader<RawOrderRecord> buildMultiResourceReader() {
        if (s3Service == null) {
            throw new IllegalStateException("S3 folder mode requires S3Service; ensure S3 auto-configuration is enabled.");
        }
        String prefix = s3Properties.getPrefix();
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalStateException("app.s3.prefix is required when app.s3.use-folder is true.");
        }

        List<String> keys = s3Service.listObjectKeys(prefix);
        Resource[] resources = keys.stream()
                .map(s3Service::getResource)
                .toArray(Resource[]::new);

        if (resources.length == 0) {
            log.warn("No CSV files found under s3://{}/{}", s3Service.getBucketName(), prefix);
        }

        log.info("Reading order data from {} files under s3://{}/{}", resources.length,
                s3Service.getBucketName(), prefix);

        Resource placeholder = resources.length > 0 ? resources[0] : new ByteArrayResource(new byte[0]);
        FlatFileItemReader<RawOrderRecord> delegate = buildFlatFileReader(placeholder);

        MultiResourceItemReader<RawOrderRecord> multiReader = new MultiResourceItemReader<>();
        multiReader.setName("orderItemReader");
        multiReader.setResources(resources);
        multiReader.setDelegate(delegate);

        return multiReader;
    }

    private Resource resolveLocalResource() {
        String path = s3Properties.getKey();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("app.s3.key is required when app.s3.use-local-file is true.");
        }
        log.info("Using local file: {}", path);
        return new FileSystemResource(path);
    }

    private Resource resolveS3SingleResource() {
        if (s3Service == null) {
            throw new IllegalStateException("S3 single-file mode requires S3Service; ensure S3 auto-configuration is enabled.");
        }
        String key = s3Properties.getKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("app.s3.key is required for S3 single-file mode.");
        }
        log.info("Using S3 resource: s3://{}/{}", s3Service.getBucketName(), key);
        return s3Service.getResource(key);
    }
}
