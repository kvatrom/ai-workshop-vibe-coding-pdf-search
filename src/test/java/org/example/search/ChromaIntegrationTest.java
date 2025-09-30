package org.example.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
class ChromaIntegrationTest {

    private static GenericContainer<?> chroma;

    @BeforeAll
    static void startChroma() {
        chroma = new GenericContainer<>(DockerImageName.parse("chromadb/chroma:latest"))
                .withExposedPorts(8000)
                .waitingFor(Wait.forHttp("/").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60)));
        chroma.start();
    }

    @AfterAll
    static void stopChroma() {
        if (chroma != null) {
            chroma.stop();
        }
    }

    private byte[] smallPdf(String text) throws IOException {
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

    @Test
    void indexesAndQueriesAgainstRealChroma() throws IOException {
        final String baseUrl = "http://" + chroma.getHost() + ":" + chroma.getMappedPort(8000);
        final var extractor = new PdfBoxTextExtractor();
        final var embedder = new DummyEmbeddingService(8);
        final var client = new HttpChromaClient(baseUrl);
        final var service = new PdfSearchService(extractor, embedder, client, "it-pdfs");

        final byte[] pdf = smallPdf("Cats and dogs are lovely.");
        service.indexPdf(new ByteArrayInputStream(pdf));

        final List<ChromaClient.SearchResult> results = service.semanticSearch("cats", 3);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).text().toLowerCase().contains("cats"));
    }
}
