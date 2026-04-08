package com.studyflow.ai.service.text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.poi.hslf.extractor.QuickButCruddyTextExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.extractor.XSLFExtractor;
import org.springframework.stereotype.Component;

@Component
public class PptTextExtractor extends AbstractMaterialTextExtractor {

    @Override
    public Set<String> supportedFileTypes() {
        return Set.of("ppt", "pptx");
    }

    @Override
    public TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException {
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase();
        if (normalizedFileName.endsWith(".pptx")) {
            try (XMLSlideShow slideShow = new XMLSlideShow(inputStream);
                 XSLFExtractor extractor = new XSLFExtractor(slideShow)) {
                return buildResult(extractor.getText());
            }
        }
        QuickButCruddyTextExtractor extractor = new QuickButCruddyTextExtractor(inputStream);
        try {
            return buildResult(extractor.getTextAsString());
        } finally {
            extractor.close();
        }
    }
}
