package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.example.search.ChromaClient;
import org.example.search.DummyEmbeddingService;
import org.example.search.HttpChromaClient;
import org.example.search.PdfBoxTextExtractor;
import org.example.search.PdfSearchService;

/**
 * Entry point that indexes PDFs under data/pdfs into a local ChromaDB instance.
 */
public class Main {
    public static void main(String[] args) {
        final String baseDir = System.getProperty("user.dir");
        final Path pdfDir = Path.of(baseDir, "data", "pdfs");
        final String chromaUrl = envOr("CHROMA_URL", "http://localhost:8000");
        final String collection = envOr("COLLECTION_NAME", "pdf-search");

        if (!Files.isDirectory(pdfDir)) {
            System.out.println("PDF directory not found: " + pdfDir.toAbsolutePath());
            return;
        }

        final var extractor = new PdfBoxTextExtractor();
        final var embedder = new DummyEmbeddingService(8);
        final ChromaClient chroma = new HttpChromaClient(chromaUrl);
        final var service = new PdfSearchService(extractor, embedder, chroma, collection);

        try (Stream<Path> files = Files.list(pdfDir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .forEach(path -> {
                        System.out.println("Indexing: " + path.getFileName());
                        try (InputStream in = Files.newInputStream(path)) {
                            service.indexPdf(in);
                        } catch (IOException e) {
                            System.err.println("Failed to index " + path + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Failed to list PDFs in " + pdfDir + ": " + e.getMessage());
        }
    }

    private static String envOr(String key, String def) {
        final String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
