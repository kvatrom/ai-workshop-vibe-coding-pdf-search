package org.example.search;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates extracting, embedding, and upserting text chunks into ChromaDB, and querying.
 */
public final class PdfSearchService {

    private final PdfTextExtractor extractor;
    private final EmbeddingService embeddings;
    private final ChromaClient chroma;
    private final String collectionName;

    public PdfSearchService(PdfTextExtractor extractor, EmbeddingService embeddings, ChromaClient chroma,
            String collectionName) {
        this.extractor = Objects.requireNonNull(extractor);
        this.embeddings = Objects.requireNonNull(embeddings);
        this.chroma = Objects.requireNonNull(chroma);
        this.collectionName = Objects.requireNonNullElse(collectionName, "pdf-search");
    }

    public void indexPdf(InputStream pdfInput) {
        final var items = extractor.extractChunks(pdfInput)
                .map(text -> new ChromaClient.UpsertEmbedding(UUID.randomUUID().toString(), embeddings.embed(text), text))
                .collect(Collectors.toList());
        if (!items.isEmpty()) {
            chroma.upsert(items, collectionName);
        }
    }

    public List<ChromaClient.SearchResult> semanticSearch(String query, int topK) {
        final double[] vec = embeddings.embed(query);
        return chroma.query(vec, collectionName, topK);
    }
}
