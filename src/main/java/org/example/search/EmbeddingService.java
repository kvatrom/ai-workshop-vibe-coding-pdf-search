package org.example.search;

/**
 * Simple interface to produce vector embeddings from text.
 */
@FunctionalInterface
public interface EmbeddingService {
    double[] embed(String text);
}
