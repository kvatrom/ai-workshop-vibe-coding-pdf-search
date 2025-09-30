package org.example.search;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final SemanticChunker chunker;

    public PdfSearchService(PdfTextExtractor extractor, EmbeddingService embeddings, ChromaClient chroma,
            String collectionName) {
        this(extractor, embeddings, chroma, collectionName, new SemanticChunker());
    }

    public PdfSearchService(PdfTextExtractor extractor, EmbeddingService embeddings, ChromaClient chroma,
            String collectionName, SemanticChunker chunker) {
        this.extractor = Objects.requireNonNull(extractor);
        this.embeddings = Objects.requireNonNull(embeddings);
        this.chroma = Objects.requireNonNull(chroma);
        this.collectionName = Objects.requireNonNullElse(collectionName, "pdf-search");
        this.chunker = Objects.requireNonNull(chunker);
    }

    public void indexPdf(InputStream pdfInput) {
        indexPdf(pdfInput, "unknown");
    }

    public void indexPdf(InputStream pdfInput, String filename) {
        final var items = extractor.extractChunks(pdfInput)
                .flatMap(pageChunk -> chunker.chunk(pageChunk.text(), pageChunk.pageNumber()))
                .map(chunk -> {
                    final String id = UUID.randomUUID().toString();
                    final double[] vec = embeddings.embed(chunk.text());
                    final Map<String, Object> md = new HashMap<>();
                    md.put("filename", filename);
                    md.put("page", chunk.pageNumber());
                    md.put("type", "chunk");
                    md.put("chunkId", id);
                    return new ChromaClient.UpsertEmbedding(id, vec, chunk.text(), md);
                })
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
