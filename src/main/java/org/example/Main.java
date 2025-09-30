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

        if (args == null || args.length == 0) {
            // Default: index data/pdfs with doc2query from env
            final var service = new PdfSearchService(extractor, embedder, chroma, collection, new org.example.search.SemanticChunker(), chooseDoc2Query(), envDoc2QueryCount());
            indexDefaultFolder(service);
            return;
        }

        final String cmd = args[0];
        switch (cmd) {
            case "index": {
                // Read flags for doc2query
                int count = envDoc2QueryCount();
                boolean disable = false;
                Path dir = null;
                for (int i = 1; i < args.length; i++) {
                    if ("--dir".equals(args[i]) && i + 1 < args.length) {
                        dir = Path.of(args[++i]);
                    } else if ("--doc2query-count".equals(args[i]) && i + 1 < args.length) {
                        try { count = Integer.parseInt(args[++i]); } catch (NumberFormatException ignore) { }
                    } else if ("--no-doc2query".equals(args[i])) {
                        disable = true;
                    }
                }
                final var doc2q = disable ? null : chooseDoc2Query();
                final var service = new PdfSearchService(extractor, embedder, chroma, collection, new org.example.search.SemanticChunker(), doc2q, Math.max(0, count));
                handleIndex(service, args);
                break;
            }
            case "search": {
                final var service = new PdfSearchService(extractor, embedder, chroma, collection);
                handleSearch(service, args);
                break;
            }
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
        long startNanos = System.nanoTime();
        int successCount = 0;
        int failCount = 0;
        try (Stream<Path> files = Files.list(pdfDir)) {
            final List<Path> pdfs = files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf")).toList();
            if (pdfs.isEmpty()) {
                System.out.println("No PDFs found in: " + pdfDir.toAbsolutePath());
                return;
            }
            for (Path path : pdfs) {
                System.out.println("Indexing: " + path.getFileName());
                try (InputStream in = Files.newInputStream(path)) {
                    service.indexPdf(in, path.getFileName().toString());
                    successCount++;
                } catch (IOException e) {
                    System.err.println("Failed to index " + path + ": " + e.getMessage());
                    failCount++;
                }
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            System.out.println("Indexing finished. Files processed=" + pdfs.size() + ", succeeded=" + successCount + ", failed=" + failCount + ", took=" + elapsedMs + " ms");
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
            final Object fname = r.metadata() == null ? null : r.metadata().get("filename");
            final Object page = r.metadata() == null ? null : r.metadata().get("page");
            System.out.println("#" + (i + 1) + " score=" + String.format("%.4f", r.score())
                    + (fname != null ? " file=" + fname : "")
                    + (page != null ? " page=" + page : ""));
            System.out.println(truncate(r.text(), 400));
            System.out.println();
        }
    }

    private static void printHelp() {
        System.out.println("Usage:\n" +
                "  ./gradlew run --args=\"index [--dir <path>] [--doc2query-count N] [--no-doc2query]\"\n" +
                "  ./gradlew run --args=\"search --q <query> [--topK N]\"\n\n" +
                "Env:\n" +
                "  CHROMA_URL, COLLECTION_NAME, OPENAI_API_KEY, OPENAI_EMBED_MODEL, OPENAI_BASE_URL, OPENAI_DOC2QUERY_MODEL, DOC2QUERY_COUNT\n");
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

    private static int envDoc2QueryCount() {
        try {
            return Integer.parseInt(envOr("DOC2QUERY_COUNT", "3"));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    private static org.example.search.Doc2QueryGenerator chooseDoc2Query() {
        final String key = System.getenv("OPENAI_API_KEY");
        if (key != null && !key.isBlank()) {
            try {
                System.out.println("Using OpenAI doc2query (model=" + envOr("OPENAI_DOC2QUERY_MODEL", "gpt-4o-mini") + ")");
                return new org.example.search.OpenAIDoc2QueryGenerator();
            } catch (Exception e) {
                System.err.println("Failed to initialize OpenAIDoc2QueryGenerator, falling back to simple: " + e.getMessage());
            }
        }
        System.out.println("Using SimpleDoc2QueryGenerator (offline heuristic)");
        return new org.example.search.SimpleDoc2QueryGenerator();
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
