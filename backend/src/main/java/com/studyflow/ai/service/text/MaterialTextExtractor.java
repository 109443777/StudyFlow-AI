package com.studyflow.ai.service.text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface MaterialTextExtractor {

    Set<String> supportedFileTypes();

    TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException;

    default boolean supports(String fileType) {
        return supportedFileTypes().contains(fileType == null ? "" : fileType.toLowerCase());
    }
}
