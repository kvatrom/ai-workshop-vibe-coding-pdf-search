package org.example.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * EmbeddingService implementation that calls OpenAI's embeddings API.
 *
 * Configuration via environment variables (resolved at construction time):
 * - OPENAI_API_KEY: required to enable this client.
 * - OPENAI_BASE_URL: optional, defaults to https://api.openai.com (useful for Azure/OpenAI-compatible gateways).
 * - OPENAI_EMBED_MODEL: optional, defaults to text-embedding-3-small.
 */
public final class OpenAIEmbeddingService implements EmbeddingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String apiKey;
    private final URI embeddingsUri;
    private final String model;

    public OpenAIEmbeddingService() {
        this(System.getenv("OPENAI_API_KEY"),
                orDefault(System.getenv("OPENAI_BASE_URL"), "https://api.openai.com"),
                orDefault(System.getenv("OPENAI_EMBED_MODEL"), "text-embedding-3-small"));
    }

    public OpenAIEmbeddingService(String apiKey, String baseUrl, String model) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        if (this.apiKey.isBlank()) {
            throw new IllegalArgumentException("OPENAI_API_KEY is blank");
        }
        final String base = orDefault(baseUrl, "https://api.openai.com");
        if (!base.startsWith("http")) {
            throw new IllegalArgumentException("OPENAI_BASE_URL must start with http/https");
        }
        this.embeddingsUri = URI.create(base.endsWith("/") ? base + "v1/embeddings" : base + "/v1/embeddings");
        this.model = orDefault(model, "text-embedding-3-small");
    }

    @Override
    public double[] embed(String text) {
        final String input = text == null ? "" : text;
        final String payload = '{' +
                "\"model\":\"" + escape(model) + "\"," +
                "\"input\":\"" + escape(input) + "\"" +
                '}';
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(embeddingsUri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("OpenAI embeddings API failed: " + resp.statusCode() + ": " + truncate(resp.body()));
            }
            final JsonNode root = MAPPER.readTree(resp.body());
            final JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("Unexpected embeddings response: missing data array");
            }
            final JsonNode embedding = data.get(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new IllegalStateException("Unexpected embeddings response: missing embedding vector");
            }
            final int dim = embedding.size();
            final double[] v = new double[dim];
            for (int i = 0; i < dim; i++) {
                v[i] = embedding.get(i).asDouble();
            }
            return v;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed calling OpenAI embeddings API", e);
        }
    }

    private static String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
