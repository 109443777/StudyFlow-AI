package com.studyflow.ai.service.text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor extends AbstractMaterialTextExtractor {

    @Override
    public Set<String> supportedFileTypes() {
        return Set.of("pdf");
    }

    @Override
    public TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException {
        byte[] content = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            return buildResult(textStripper.getText(document));
        }
    }
}
