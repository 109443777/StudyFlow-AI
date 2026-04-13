package com.studyflow.ai.gateway;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.MinioProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import com.studyflow.ai.vo.UploadedPartVO;
import io.minio.BucketExistsArgs;
import io.minio.CreateMultipartUploadResponse;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.S3Base;
import io.minio.UploadPartResponse;
import io.minio.messages.Part;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MinioClient.class)
public class MinioStorageGateway implements StorageGateway {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    private volatile Object minioAsyncClient;

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(resolveContentType(contentType))
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to upload file to storage");
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            ensureBucket();
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to download file from storage");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            ensureBucket();
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to delete file from storage");
        }
    }

    @Override
    public String getFileUrl(String objectKey) {
        String endpoint = minioProperties.getEndpoint();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioProperties.getBucketName() + "/" + objectKey;
    }

    @Override
    public String initMultipartUpload(String objectKey, String contentType) {
        try {
            ensureBucket();
            Multimap<String, String> headers = LinkedListMultimap.create();
            headers.put("Content-Type", resolveContentType(contentType));
            CreateMultipartUploadResponse response = invokeAsync(
                    "createMultipartUploadAsync",
                    new Class<?>[] {String.class, String.class, String.class, Multimap.class, Multimap.class},
                    minioProperties.getBucketName(),
                    null,
                    objectKey,
                    headers,
                    LinkedListMultimap.create());
            return response.result().uploadId();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to initialize multipart upload, objectKey={}", objectKey, exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to initialize multipart upload");
        }
    }

    @Override
    public String uploadPart(String objectKey, String storageUploadId, int partNumber, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucket();
            UploadPartResponse response = invokeAsync(
                    "uploadPartAsync",
                    new Class<?>[] {String.class, String.class, String.class, Object.class, long.class, String.class, int.class, Multimap.class, Multimap.class},
                    minioProperties.getBucketName(),
                    null,
                    objectKey,
                    inputStream,
                    size,
                    storageUploadId,
                    partNumber,
                    LinkedListMultimap.create(),
                    LinkedListMultimap.create());
            return response.etag();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to upload multipart part, objectKey={}, uploadId={}, partNumber={}", objectKey, storageUploadId, partNumber, exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to upload multipart part");
        }
    }

    @Override
    public List<UploadedPartVO> listUploadedParts(String objectKey, String storageUploadId) {
        try {
            ensureBucket();
            List<UploadedPartVO> uploadedParts = new ArrayList<>();
            int marker = 0;
            boolean truncated;
            do {
                io.minio.ListPartsResponse response = invokeAsync(
                        "listPartsAsync",
                        new Class<?>[] {String.class, String.class, String.class, Integer.class, Integer.class, String.class, Multimap.class, Multimap.class},
                        minioProperties.getBucketName(),
                        null,
                        objectKey,
                        1000,
                        marker,
                        storageUploadId,
                        LinkedListMultimap.create(),
                        LinkedListMultimap.create());
                List<Part> partList = response.result().partList();
                if (partList != null) {
                    for (Part part : partList) {
                        uploadedParts.add(UploadedPartVO.builder()
                                .partNumber(part.partNumber())
                                .etag(part.etag())
                                .build());
                    }
                }
                truncated = response.result().isTruncated();
                marker = response.result().nextPartNumberMarker();
            } while (truncated);
            uploadedParts.sort(Comparator.comparing(UploadedPartVO::getPartNumber));
            return uploadedParts;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to list multipart parts, objectKey={}, uploadId={}", objectKey, storageUploadId, exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to list multipart parts");
        }
    }

    @Override
    public void completeMultipartUpload(String objectKey, String storageUploadId, List<UploadedPartVO> uploadedParts) {
        try {
            ensureBucket();
            Part[] parts = uploadedParts.stream()
                    .sorted(Comparator.comparing(UploadedPartVO::getPartNumber))
                    .map(uploadedPart -> new Part(uploadedPart.getPartNumber(), uploadedPart.getEtag()))
                    .toArray(Part[]::new);
            invokeAsync(
                    "completeMultipartUploadAsync",
                    new Class<?>[] {String.class, String.class, String.class, String.class, Part[].class, Multimap.class, Multimap.class},
                    minioProperties.getBucketName(),
                    null,
                    objectKey,
                    storageUploadId,
                    parts,
                    LinkedListMultimap.create(),
                    LinkedListMultimap.create());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to complete multipart upload, objectKey={}, uploadId={}", objectKey, storageUploadId, exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to complete multipart upload");
        }
    }

    @Override
    public void abortMultipartUpload(String objectKey, String storageUploadId) {
        try {
            ensureBucket();
            invokeAsync(
                    "abortMultipartUploadAsync",
                    new Class<?>[] {String.class, String.class, String.class, String.class, Multimap.class, Multimap.class},
                    minioProperties.getBucketName(),
                    null,
                    objectKey,
                    storageUploadId,
                    LinkedListMultimap.create(),
                    LinkedListMultimap.create());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Failed to abort multipart upload, objectKey={}, uploadId={}", objectKey, storageUploadId, exception);
            throw new BusinessException(ResultCodeEnum.SYSTEM_BUSY, "failed to abort multipart upload");
        }
    }

    private String resolveContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : DEFAULT_CONTENT_TYPE;
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioProperties.getBucketName())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeAsync(String methodName, Class<?>[] parameterTypes, Object... args)
            throws NoSuchMethodException, NoSuchFieldException, InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException {
        Method method = S3Base.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        CompletableFuture<T> future = (CompletableFuture<T>) method.invoke(resolveAsyncClient(), args);
        return future.get();
    }

    private Object resolveAsyncClient() throws NoSuchFieldException, IllegalAccessException {
        if (minioAsyncClient != null) {
            return minioAsyncClient;
        }
        Field field = MinioClient.class.getDeclaredField("asyncClient");
        field.setAccessible(true);
        minioAsyncClient = field.get(minioClient);
        return minioAsyncClient;
    }
}
