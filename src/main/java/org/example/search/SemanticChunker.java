package org.example.search;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Heuristic semantic chunker that splits text into sentences and greedily merges them
 * into chunks bounded by target character sizes. Page number is preserved.
 *
 * This is language-agnostic (uses BreakIterator sentence boundaries) and requires no external deps.
 */
public final class SemanticChunker {

    private final int targetMinChars;
    private final int targetMaxChars;

    public SemanticChunker() {
        this(400, 1200);
    }

    public SemanticChunker(int targetMinChars, int targetMaxChars) {
        this.targetMinChars = Math.max(100, targetMinChars);
        this.targetMaxChars = Math.max(this.targetMinChars + 100, targetMaxChars);
    }

    public Stream<PdfTextExtractor.Chunk> chunk(String pageText, int pageNumber) {
        Objects.requireNonNull(pageText, "pageText");
        final String text = pageText.strip();
        if (text.isEmpty()) {
            return Stream.empty();
        }
        final List<String> sentences = splitSentences(text);
        final List<PdfTextExtractor.Chunk> chunks = new ArrayList<>();
        final StringBuilder buf = new StringBuilder();

        for (String s : sentences) {
            final String sentence = s.strip();
            if (sentence.isEmpty()) {
                continue;
            }
            if (buf.isEmpty()) {
                buf.append(sentence);
                continue;
            }
            if (buf.length() + 1 + sentence.length() <= targetMaxChars) {
                buf.append(' ').append(sentence);
            } else {
                // Flush if we are already reasonably sized; otherwise, still flush to avoid oversize.
                chunks.add(new PdfTextExtractor.Chunk(buf.toString(), pageNumber));
                buf.setLength(0);
                buf.append(sentence);
            }
        }
        if (!buf.isEmpty()) {
            chunks.add(new PdfTextExtractor.Chunk(buf.toString(), pageNumber));
        }

        // Post-pass: if we produced many tiny chunks, greedily merge neighbors until >= targetMinChars where possible.
        final List<PdfTextExtractor.Chunk> merged = new ArrayList<>();
        StringBuilder acc = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            final String t = chunks.get(i).text();
            if (acc.isEmpty()) {
                acc.append(t);
            } else if (acc.length() + 1 + t.length() <= targetMaxChars && acc.length() < targetMinChars) {
                acc.append(' ').append(t);
            } else {
                merged.add(new PdfTextExtractor.Chunk(acc.toString(), pageNumber));
                acc = new StringBuilder(t);
            }
        }
        if (!acc.isEmpty()) {
            merged.add(new PdfTextExtractor.Chunk(acc.toString(), pageNumber));
        }

        return merged.stream();
    }

    private List<String> splitSentences(String text) {
        final List<String> sentences = new ArrayList<>();
        final BreakIterator bi = BreakIterator.getSentenceInstance(Locale.getDefault());
        bi.setText(text);
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            final String s = text.substring(start, end).trim();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }
        return sentences;
    }
}
