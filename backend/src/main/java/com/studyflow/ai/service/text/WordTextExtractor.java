package com.studyflow.ai.service.text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class WordTextExtractor extends AbstractMaterialTextExtractor {

    @Override
    public Set<String> supportedFileTypes() {
        return Set.of("doc", "docx");
    }

    @Override
    public TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException {
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase();
        if (normalizedFileName.endsWith(".docx")) {
            try (XWPFDocument document = new XWPFDocument(inputStream);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return buildResult(extractor.getText());
            }
        }
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return buildResult(extractor.getText());
        }
    }
}
