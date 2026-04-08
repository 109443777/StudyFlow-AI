package com.studyflow.ai.gateway;

import java.io.InputStream;

public interface StorageGateway {

    void upload(String objectKey, InputStream inputStream, long size, String contentType);

    String getFileUrl(String objectKey);
}
