package com.example.springbatchdemo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * S3 operations using S3Client directly.
 * Bucket is configured via spring.cloud.aws.s3.bucket-name.
 */
@Service
public class S3Service {

    @Value("${spring.cloud.aws.s3.bucket-name:}")
    private String bucketName;

    private final S3Client s3Client;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String getBucketName() {
        validateBucket();
        return bucketName;
    }

    private void validateBucket() {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("S3 bucket name is required. Set spring.cloud.aws.s3.bucket-name or S3_BUCKET.");
        }
    }

    /**
     * Upload a file to S3.
     */
    public void uploadFile(String key, File file) {
        validateBucket();
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build(),
                RequestBody.fromFile(file));
    }

    /**
     * Download a file from S3 as byte array.
     */
    public byte[] downloadFile(String key) {
        validateBucket();
        ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build());
        return object.asByteArray();
    }

    /**
     * Get a streaming Resource for reading an S3 object.
     */
    public Resource getResource(String key) {
        validateBucket();
        return new S3ObjectResource(bucketName, key, s3Client);
    }

    /**
     * List object keys under a prefix (folder).
     */
    public List<String> listObjectKeys(String prefix) {
        validateBucket();
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        return s3Client.listObjectsV2Paginator(request)
                .stream()
                .flatMap(this::streamKeys)
                .filter(key -> key.toLowerCase().endsWith(".csv"))
                .toList();
    }

    private Stream<String> streamKeys(ListObjectsV2Response response) {
        return response.contents() != null
                ? response.contents().stream().map(S3Object::key)
                : Stream.empty();
    }

    /**
     * Resource implementation that streams from S3 on getInputStream().
     */
    private static final class S3ObjectResource extends AbstractResource {

        private final String bucket;
        private final String key;
        private final S3Client s3Client;

        S3ObjectResource(String bucket, String key, S3Client s3Client) {
            this.bucket = bucket;
            this.key = key;
            this.s3Client = s3Client;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());
        }

        @Override
        public String getFilename() {
            int lastSlash = key.lastIndexOf('/');
            return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
        }

        @Override
        public String getDescription() {
            return "s3://" + bucket + "/" + key;
        }
    }
}
