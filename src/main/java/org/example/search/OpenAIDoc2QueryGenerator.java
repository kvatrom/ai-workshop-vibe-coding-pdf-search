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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * OpenAI-backed doc2query generator using /v1/chat/completions (or compatible gateway).
 */
public final class OpenAIDoc2QueryGenerator implements Doc2QueryGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String apiKey;
    private final URI chatUri;
    private final String model;

    public OpenAIDoc2QueryGenerator() {
        this(System.getenv("OPENAI_API_KEY"),
                orDefault(System.getenv("OPENAI_BASE_URL"), "https://api.openai.com"),
                orDefault(System.getenv("OPENAI_DOC2QUERY_MODEL"), "gpt-4o-mini"));
    }

    public OpenAIDoc2QueryGenerator(String apiKey, String baseUrl, String model) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).version(HttpClient.Version.HTTP_1_1).build();
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        if (this.apiKey.isBlank()) {
            throw new IllegalArgumentException("OPENAI_API_KEY is blank");
        }
        final String base = orDefault(baseUrl, "https://api.openai.com");
        this.chatUri = URI.create(base.endsWith("/") ? base + "v1/chat/completions" : base + "/v1/chat/completions");
        this.model = orDefault(model, "gpt-4o-mini");
    }

    @Override
    public List<String> generateQuestions(String text, int maxQuestions) {
        final int n = Math.max(0, maxQuestions);
        if (n == 0) {
            return List.of();
        }
        final String prompt = buildPrompt(text, n);
        final String payload = '{' +
                "\"model\":\"" + escape(model) + "\"," +
                "\"temperature\":0.3," +
                "\"messages\":[{" +
                "\"role\":\"system\",\"content\":\"You generate concise, diverse search questions.\"},{" +
                "\"role\":\"user\",\"content\":\"" + escape(prompt) + "\"}]" +
                '}';
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(chatUri)
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("OpenAI chat API failed: " + resp.statusCode() + ": " + truncate(resp.body()));
            }
            final JsonNode root = MAPPER.readTree(resp.body());
            final JsonNode content = root.path("choices").get(0).path("message").path("content");
            final String raw = content.asText("");
            return parseBulletList(raw, n);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed calling OpenAI chat API", e);
        }
    }

    private static List<String> parseBulletList(String raw, int n) {
        final List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        final String[] lines = raw.split("\r?\n");
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            s = s.replaceFirst("^[-*\\d+.)\\s]+", "").trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
            if (out.size() >= n) {
                break;
            }
        }
        if (out.isEmpty()) {
            out.add(raw.trim());
        }
        return out.size() > n ? out.subList(0, n) : out;
    }

    private static String buildPrompt(String text, int n) {
        final String t = text == null ? "" : text.strip();
        final String limited = t.length() > 1200 ? t.substring(0, 1200) : t;
        return "Given the following document chunk, generate " + n + " diverse, concise, natural-language search questions a user might ask to find this content. Output one question per line without numbering.\n\nChunk:\n" + limited;
    }

    private static String orDefault(String v, String def) { return v == null || v.isBlank() ? def : v; }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static String truncate(String s) { if (s == null) { return ""; } return s.length() > 300 ? s.substring(0, 300) + "…" : s; }
}
