package org.example.search;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * PDFBox-based extractor that returns text per page as chunks.
 */
public final class PdfBoxTextExtractor implements PdfTextExtractor {

    @Override
    public Stream<String> extractChunks(InputStream pdfInput) {
        Objects.requireNonNull(pdfInput, "pdfInput");
        try (PDDocument doc = PDDocument.load(pdfInput)) {
            final int pageCount = doc.getNumberOfPages();
            final PDFTextStripper stripper = new PDFTextStripper();
            final var list = IntStream.rangeClosed(1, pageCount)
                    .mapToObj(page -> extractPage(doc, stripper, page))
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            return list.stream();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract text from PDF", e);
        }
    }

    private String extractPage(PDDocument doc, PDFTextStripper stripper, int page) {
        try {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(doc).trim();
        } catch (IOException e) {
            return null;
        }
    }
}
