package org.example.search;

import java.io.InputStream;
import java.util.stream.Stream;

/**
 * Extracts text chunks from a PDF.
 */
@FunctionalInterface
public interface PdfTextExtractor {
    Stream<String> extractChunks(InputStream pdfInput);
}
