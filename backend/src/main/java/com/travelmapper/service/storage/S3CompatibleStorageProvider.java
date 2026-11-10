package com.travelmapper.service.storage;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class S3CompatibleStorageProvider implements AutoCloseable, StorageProvider {

    private final StorageConfig config;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3CompatibleStorageProvider(StorageConfig config) {
        this.config = config;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
        Region region = Region.of(config.region() == null || config.region().isBlank()
                ? "us-east-1" : config.region());
        URI endpoint = URI.create(config.endpoint());
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(config.pathStyle())
                .build();
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Override
    public PresignedUpload presignUpload(String objectKey, String contentType, long expireSeconds) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            PresignedPutObjectRequest presigned = presigner.presignPutObject(builder -> builder
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .putObjectRequest(request));
            return new PresignedUpload(objectKey, presigned.url().toString(), "PUT",
                    Map.of("Content-Type", contentType), presigned.expiration());
        } catch (SdkException ex) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "生成上传签名失败");
        }
    }

    @Override
    public String presignDownload(String objectKey, long expireSeconds) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(objectKey)
                    .build();
            PresignedGetObjectRequest presigned = presigner.presignGetObject(builder -> builder
                    .signatureDuration(Duration.ofSeconds(expireSeconds))
                    .getObjectRequest(request));
            return presigned.url().toString();
        } catch (SdkException ex) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "生成下载签名失败");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(builder -> builder.bucket(config.bucket()).key(objectKey));
        } catch (SdkException ex) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "删除对象失败");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.headObject(builder -> builder.bucket(config.bucket()).key(objectKey));
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "检查对象失败");
        } catch (SdkException ex) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "检查对象失败");
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        try {
            return client.listObjectsV2Paginator(builder -> builder
                            .bucket(config.bucket())
                            .prefix(prefix))
                    .contents()
                    .stream()
                    .map(object -> new StoredObject(object.key(), object.lastModified(), object.size()))
                    .collect(Collectors.toList());
        } catch (SdkException ex) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "列举对象失败");
        }
    }

    @Override
    public ProbeResult probe() {
        long start = System.nanoTime();
        try {
            client.headBucket(builder -> builder.bucket(config.bucket()));
            return new ProbeResult(true, elapsedMs(start), null);
        } catch (SdkException ex) {
            return new ProbeResult(false, elapsedMs(start), ex.getMessage());
        }
    }

    @Override
    public Stats stats(int maxObjects) {
        long count = 0;
        long bytes = 0;
        boolean truncated = false;
        try {
            for (S3Object object : client.listObjectsV2Paginator(
                    builder -> builder.bucket(config.bucket())).contents()) {
                if (count >= maxObjects) {
                    truncated = true;
                    break;
                }
                count++;
                bytes += object.size() == null ? 0 : object.size();
            }
            return new Stats(count, bytes, truncated);
        } catch (SdkException ex) {
            throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE, "统计对象失败");
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @Override
    public String providerName() {
        return config.provider();
    }

    @Override
    public void close() {
        client.close();
        presigner.close();
    }
}
