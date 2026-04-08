package com.studyflow.ai.service.text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TxtTextExtractor extends AbstractMaterialTextExtractor {

    @Override
    public Set<String> supportedFileTypes() {
        return Set.of("txt");
    }

    @Override
    public TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException {
        return buildResult(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
    }
}
