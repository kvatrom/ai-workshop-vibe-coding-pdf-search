package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.example.search.ChromaClient;
import org.example.search.DummyEmbeddingService;
import org.example.search.HttpChromaClient;
import org.example.search.OpenAIEmbeddingService;
import org.example.search.PdfBoxTextExtractor;
import org.example.search.PdfSearchService;

/**
 * CLI entry point.
 *
 * Commands:
 *   - index [--dir <path>]           Index all PDFs from the folder (default: data/pdfs)
 *   - search --q <query> [--topK N]  Search the configured collection (default topK=5)
 *
 * Env vars:
 *   CHROMA_URL (default http://localhost:8000)
 *   COLLECTION_NAME (default pdf-search)
 *   OPENAI_API_KEY (optional; if present uses OpenAI embeddings)
 *   OPENAI_EMBED_MODEL (default text-embedding-3-small)
 */
public class Main {
    public static void main(String[] args) {
        final String chromaUrl = envOr("CHROMA_URL", "http://localhost:8000");
        final String collection = envOr("COLLECTION_NAME", "pdf-search");
        final var embedder = chooseEmbedder();
        final ChromaClient chroma = new HttpChromaClient(chromaUrl);
        final var extractor = new PdfBoxTextExtractor();
        final var service = new PdfSearchService(extractor, embedder, chroma, collection);

        if (args == null || args.length == 0) {
            // Backward-compatible default: index data/pdfs
            indexDefaultFolder(service);
            return;
        }

        final String cmd = args[0];
        switch (cmd) {
            case "index":
                handleIndex(service, args);
                break;
            case "search":
                handleSearch(service, args);
                break;
            case "help":
            case "-h":
            case "--help":
                printHelp();
                break;
            default:
                System.err.println("Unknown command: " + cmd);
                printHelp();
        }
    }

    private static void indexDefaultFolder(PdfSearchService service) {
        final Path pdfDir = Path.of(System.getProperty("user.dir"), "data", "pdfs");
        indexFolder(service, pdfDir);
    }

    private static void handleIndex(PdfSearchService service, String[] args) {
        Path dir = null;
        for (int i = 1; i < args.length; i++) {
            if ("--dir".equals(args[i]) && i + 1 < args.length) {
                dir = Path.of(args[++i]);
            }
        }
        if (dir == null) {
            dir = Path.of(System.getProperty("user.dir"), "data", "pdfs");
        }
        indexFolder(service, dir);
    }

    private static void indexFolder(PdfSearchService service, Path pdfDir) {
        if (!Files.isDirectory(pdfDir)) {
            System.err.println("PDF directory not found: " + pdfDir.toAbsolutePath());
            return;
        }
        try (Stream<Path> files = Files.list(pdfDir)) {
            final List<Path> pdfs = files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf")).toList();
            if (pdfs.isEmpty()) {
                System.out.println("No PDFs found in: " + pdfDir.toAbsolutePath());
                return;
            }
            for (Path path : pdfs) {
                System.out.println("Indexing: " + path.getFileName());
                try (InputStream in = Files.newInputStream(path)) {
                    service.indexPdf(in);
                } catch (IOException e) {
                    System.err.println("Failed to index " + path + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to list PDFs in " + pdfDir + ": " + e.getMessage());
        }
    }

    private static void handleSearch(PdfSearchService service, String[] args) {
        String query = null;
        int topK = 5;
        for (int i = 1; i < args.length; i++) {
            final String a = args[i];
            if ("--q".equals(a) && i + 1 < args.length) {
                query = args[++i];
            } else if ("--topK".equals(a) && i + 1 < args.length) {
                try {
                    topK = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ex) {
                    System.err.println("Invalid --topK value; using default 5");
                }
            }
        }
        if (query == null || query.isBlank()) {
            System.err.println("search requires --q <query>");
            printHelp();
            return;
        }
        final var results = service.semanticSearch(query, topK);
        if (results.isEmpty()) {
            System.out.println("No results.");
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            final var r = results.get(i);
            System.out.println("#" + (i + 1) + " score=" + String.format("%.4f", r.score()));
            System.out.println(truncate(r.text(), 400));
            System.out.println();
        }
    }

    private static void printHelp() {
        System.out.println("Usage:\n" +
                "  ./gradlew run --args=\"index [--dir <path>]\"\n" +
                "  ./gradlew run --args=\"search --q <query> [--topK N]\"\n\n" +
                "Env:\n" +
                "  CHROMA_URL, COLLECTION_NAME, OPENAI_API_KEY, OPENAI_EMBED_MODEL\n");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String envOr(String key, String def) {
        final String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    private static org.example.search.EmbeddingService chooseEmbedder() {
        final String key = System.getenv("OPENAI_API_KEY");
        if (key != null && !key.isBlank()) {
            System.out.println("Using OpenAI embeddings (model=" + envOr("OPENAI_EMBED_MODEL", "text-embedding-3-small") + ")");
            try {
                return new OpenAIEmbeddingService();
            } catch (Exception e) {
                System.err.println("Failed to initialize OpenAIEmbeddingService, falling back to Dummy: " + e.getMessage());
            }
        }
        System.out.println("Using DummyEmbeddingService (deterministic test embeddings)");
        return new DummyEmbeddingService(8);
    }
}
