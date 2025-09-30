package org.example.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

class PdfSearchServiceDoc2QueryTest {

    private byte[] onePagePdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    static class CapturingChroma implements ChromaClient {
        List<UpsertEmbedding> upserts = new ArrayList<>();
        @Override public void upsert(List<UpsertEmbedding> items, String collectionName) { upserts.addAll(items); }
        @Override public List<SearchResult> query(double[] embedding, String collectionName, int topK) { return List.of(); }
    }

    static class FakeDoc2Q implements Doc2QueryGenerator {
        @Override public List<String> generateQuestions(String text, int maxQuestions) {
            return List.of("What about cats?", "Where are dogs?").stream().limit(maxQuestions).collect(Collectors.toList());
        }
    }

    @Test
    void generatesAndUpsertsSyntheticQuestionsLinkedToChunks() throws IOException {
        final var extractor = new PdfBoxTextExtractor();
        final var embedder = new DummyEmbeddingService(8);
        final var chroma = new CapturingChroma();
        final var doc2q = new FakeDoc2Q();
        final var service = new PdfSearchService(extractor, embedder, chroma, "coll", new SemanticChunker(200, 500), doc2q, 2);

        service.indexPdf(new ByteArrayInputStream(onePagePdf("This section discusses cats and dogs.")), "pets.pdf");

        // Expect 1 chunk + 2 questions
        assertEquals(3, chroma.upserts.size());
        final var types = chroma.upserts.stream().map(u -> String.valueOf(u.metadata().get("type"))).collect(Collectors.toList());
        assertTrue(types.contains("chunk"));
        assertEquals(2, types.stream().filter(t -> t.equals("question")).count());

        // Verify linkage and metadata
        final Map<String, Object> chunkMd = chroma.upserts.stream().filter(u -> "chunk".equals(u.metadata().get("type"))).findFirst().get().metadata();
        final String chunkId = String.valueOf(chunkMd.get("chunkId"));
        chroma.upserts.stream().filter(u -> "question".equals(u.metadata().get("type"))).forEach(u -> {
            assertEquals(chunkId, String.valueOf(u.metadata().get("parentChunkId")));
            assertEquals("pets.pdf", String.valueOf(u.metadata().get("filename")));
        });
    }
}
