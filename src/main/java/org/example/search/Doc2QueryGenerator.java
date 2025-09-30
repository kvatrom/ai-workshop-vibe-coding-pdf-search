package org.example.search;

import java.util.List;

/**
 * Generates synthetic user questions for a given text chunk (aka doc2query).
 */
@FunctionalInterface
public interface Doc2QueryGenerator {

    /**
     * Generate up to maxQuestions natural-language questions that a user might ask
     * to retrieve the given text.
     */
    List<String> generateQuestions(String text, int maxQuestions);
}
