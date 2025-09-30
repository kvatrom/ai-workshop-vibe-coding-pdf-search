package org.example.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

class PdfSearchServiceTest {

    private byte[] twoPagePdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writePage(doc, "First page about cats");
            writePage(doc, "Second page about dogs");
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void writePage(PDDocument doc, String text) throws IOException {
        final PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(72, 700);
            cs.showText(text);
            cs.endText();
        }
    }

    @Test
    void indexesAndSearches() throws IOException {
        final var extractor = new PdfBoxTextExtractor();
        final var embedder = new DummyEmbeddingService(8);
        final var fakeChroma = new InMemoryChroma();
        final var service = new PdfSearchService(extractor, embedder, fakeChroma, "test-collection");

        service.indexPdf(new ByteArrayInputStream(twoPagePdf()));
        assertEquals(2, fakeChroma.upserted.size());
        assertFalse(fakeChroma.upserted.get(0).text().isBlank());

        final var results = service.semanticSearch("cats", 1);
        assertEquals(1, results.size());
    }

    static class InMemoryChroma implements ChromaClient {
        List<UpsertEmbedding> upserted = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();

        @Override
        public void upsert(List<UpsertEmbedding> items, String collectionName) {
            upserted.addAll(items);
        }

        @Override
        public List<SearchResult> query(double[] embedding, String collectionName, int topK) {
            // Return first topK items as dummy results with descending score
            return upserted.stream()
                    .limit(topK)
                    .map(it -> new SearchResult(it.id(), it.text(), 1.0))
                    .collect(Collectors.toList());
        }
    }
}
