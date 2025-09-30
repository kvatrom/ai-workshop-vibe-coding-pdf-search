package org.example.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SemanticChunkerTest {

    @Test
    void splitsLongTextIntoMultipleChunksAndPreservesPage() {
        final String sentence = "This is a fairly long sentence that will be repeated to create enough content. ";
        final String text = sentence.repeat(50); // ~3000+ chars
        final SemanticChunker chunker = new SemanticChunker(400, 800);

        final List<PdfTextExtractor.Chunk> chunks = chunker.chunk(text, 7).collect(Collectors.toList());
        assertTrue(chunks.size() >= 3, "Expected multiple chunks due to max size bound");
        assertEquals(7, chunks.get(0).pageNumber());
        assertTrue(chunks.get(0).text().length() >= 200);
    }
}
