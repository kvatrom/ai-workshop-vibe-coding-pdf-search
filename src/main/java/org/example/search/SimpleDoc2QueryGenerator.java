package org.example.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple, offline doc2query generator that crafts generic questions from the text.
 * This is a heuristic fallback used when OpenAI is not available.
 */
public final class SimpleDoc2QueryGenerator implements Doc2QueryGenerator {

    @Override
    public List<String> generateQuestions(String text, int maxQuestions) {
        final String t = text == null ? "" : text.strip();
        final int n = Math.max(0, maxQuestions);
        final List<String> qs = new ArrayList<>(n);
        if (n == 0 || t.isEmpty()) {
            return qs;
        }
        final String summary = summarize(t, 12);
        qs.add("What does this say about: " + summary + "?");
        if (qs.size() >= n) {
            return qs;
        }
        qs.add("Explain the key points about " + summary + ".");
        if (qs.size() >= n) {
            return qs;
        }
        qs.add("Where in the document does it discuss: " + summary + "?");
        if (qs.size() >= n) {
            return qs;
        }
        qs.add("Provide a brief overview of " + summary + ".");
        if (qs.size() >= n) {
            return qs;
        }
        qs.add("How does this section relate to " + summary + "?");
        return qs.subList(0, Math.min(qs.size(), n));
    }

    private String summarize(String text, int maxWords) {
        final String[] words = text.replaceAll("\n+", " ").split("\\s+");
        final int take = Math.min(words.length, Math.max(3, maxWords));
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i].toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
