package org.example.search;

import java.util.List;

/**
 * Minimal ChromaDB client interface for stub purposes.
 */
public interface ChromaClient {

    record UpsertEmbedding(String id, double[] embedding, String text) { }

    void upsert(List<UpsertEmbedding> items, String collectionName);

    List<SearchResult> query(double[] embedding, String collectionName, int topK);

    record SearchResult(String id, String text, double score) { }
}
