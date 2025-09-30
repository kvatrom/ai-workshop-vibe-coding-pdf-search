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
    private final Doc2QueryGenerator doc2Query;
    private final int doc2QueryCount;

    public PdfSearchService(PdfTextExtractor extractor, EmbeddingService embeddings, ChromaClient chroma,
            String collectionName) {
        this(extractor, embeddings, chroma, collectionName, new SemanticChunker(), null, 0);
    }

    public PdfSearchService(PdfTextExtractor extractor, EmbeddingService embeddings, ChromaClient chroma,
            String collectionName, SemanticChunker chunker) {
        this(extractor, embeddings, chroma, collectionName, chunker, null, 0);
    }

    public PdfSearchService(PdfTextExtractor extractor, EmbeddingService embeddings, ChromaClient chroma,
            String collectionName, SemanticChunker chunker, Doc2QueryGenerator doc2Query, int doc2QueryCount) {
        this.extractor = Objects.requireNonNull(extractor);
        this.embeddings = Objects.requireNonNull(embeddings);
        this.chroma = Objects.requireNonNull(chroma);
        this.collectionName = Objects.requireNonNullElse(collectionName, "pdf-search");
        this.chunker = Objects.requireNonNull(chunker);
        this.doc2Query = doc2Query; // may be null to disable
        this.doc2QueryCount = Math.max(0, doc2QueryCount);
    }

    public void indexPdf(InputStream pdfInput) {
        indexPdf(pdfInput, "unknown");
    }

    public void indexPdf(InputStream pdfInput, String filename) {
        final var items = extractor.extractChunks(pdfInput)
                .flatMap(pageChunk -> chunker.chunk(pageChunk.text(), pageChunk.pageNumber()))
                .flatMap(chunk -> {
                    final List<ChromaClient.UpsertEmbedding> list = new java.util.ArrayList<>();
                    final String chunkId = UUID.randomUUID().toString();
                    final double[] chunkVec = embeddings.embed(chunk.text());
                    final Map<String, Object> md = new HashMap<>();
                    md.put("filename", filename);
                    md.put("page", chunk.pageNumber());
                    md.put("type", "chunk");
                    md.put("chunkId", chunkId);
                    list.add(new ChromaClient.UpsertEmbedding(chunkId, chunkVec, chunk.text(), md));

                    if (doc2Query != null && doc2QueryCount > 0) {
                        final List<String> qs = doc2Query.generateQuestions(chunk.text(), doc2QueryCount);
                        for (int i = 0; i < qs.size(); i++) {
                            final String q = qs.get(i);
                            final String qId = chunkId + "#q" + i;
                            final double[] qVec = embeddings.embed(q);
                            final Map<String, Object> qmd = new HashMap<>();
                            qmd.put("filename", filename);
                            qmd.put("page", chunk.pageNumber());
                            qmd.put("type", "question");
                            qmd.put("parentChunkId", chunkId);
                            qmd.put("questionIndex", i);
                            list.add(new ChromaClient.UpsertEmbedding(qId, qVec, q, qmd));
                        }
                    }
                    return list.stream();
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
