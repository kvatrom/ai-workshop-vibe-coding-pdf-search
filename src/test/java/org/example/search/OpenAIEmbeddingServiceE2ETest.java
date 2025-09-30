package org.example.search;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test for OpenAIEmbeddingService.
 *
 * This test only runs when an OpenAI API key is provided via environment variable.
 * It issues a real request to the OpenAI embeddings API and verifies a non-empty vector is returned.
 *
 * Run with:
 *   export OPENAI_API_KEY=sk-...
 *   ./gradlew integrationTest
 */
@Tag("integration")
class OpenAIEmbeddingServiceE2ETest {

    @Test
    void embedsWithRealOpenAIWhenKeyPresent() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getenv("OPENAOI_API_KEY"); // tolerate common typo
        }
        assumeTrue(key != null && !key.isBlank(), "No OpenAI API key set; skipping E2E test");

        final OpenAIEmbeddingService svc = new OpenAIEmbeddingService();
        final double[] vec = svc.embed("Hello from E2E test");
        assertTrue(vec.length > 100, "Expected a reasonably-sized embedding vector from OpenAI");
    }
}
