package org.example.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Minimal HTTP client for ChromaDB REST API (v1).
 * This client lazily creates collections and caches their IDs.
 */
public final class HttpChromaClient implements ChromaClient {

    private final String baseUrl; // e.g., http://localhost:8000
    private final HttpClient http;
    private final ObjectMapper json;
    private final Map<String, String> collectionIdCache = new ConcurrentHashMap<>();

    public HttpChromaClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.json = new ObjectMapper();
    }

    @Override
    public void upsert(List<UpsertEmbedding> items, String collectionName) {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return;
        }
        final String cid = ensureCollection(collectionName);
        final Map<String, Object> body = new HashMap<>();
        body.put("ids", items.stream().map(UpsertEmbedding::id).toList());
        body.put("embeddings", items.stream()
                .map(UpsertEmbedding::embedding)
                .map(this::toFloatList)
                .toList());
        body.put("documents", items.stream().map(UpsertEmbedding::text).toList());
        body.put("metadatas", items.stream().map(UpsertEmbedding::metadata).toList());
        final String url = baseUrl + "/api/v1/collections/" + cid + "/add";
        post(url, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SearchResult> query(double[] embedding, String collectionName, int topK) {
        final String cid = ensureCollection(collectionName);
        final Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", List.of(toFloatList(embedding)));
        body.put("n_results", topK);
        final String url = baseUrl + "/api/v1/collections/" + cid + "/query";
        final Map<String, Object> resp = post(url, body);
        // Response format (typical): { "ids": [[...]], "distances": [[...]], "documents": [[...]], "metadatas": [[{...}]] }
        final List<List<String>> ids = (List<List<String>>) resp.getOrDefault("ids", List.of());
        final List<List<String>> docs = (List<List<String>>) resp.getOrDefault("documents", List.of());
        final List<List<Double>> distances = (List<List<Double>>) resp.getOrDefault("distances", List.of());
        final List<List<Map<String, Object>>> metas = (List<List<Map<String, Object>>>) resp.getOrDefault("metadatas", List.of());
        if (ids.isEmpty()) {
            return List.of();
        }
        final List<String> firstIds = ids.getFirst();
        final List<String> firstDocs = docs.isEmpty() ? List.of() : docs.getFirst();
        final List<Double> firstDistances = distances.isEmpty() ? List.of() : distances.getFirst();
        final List<Map<String, Object>> firstMetas = metas.isEmpty() ? List.of() : metas.getFirst();
        return firstIds.stream().map(i -> firstIds.indexOf(i)).map(idx -> {
            final String id = firstIds.get(idx);
            final String text = idx < firstDocs.size() ? firstDocs.get(idx) : "";
            final double score = idx < firstDistances.size() ? safeScore(firstDistances.get(idx)) : 0.0;
            final Map<String, Object> md = idx < firstMetas.size() ? firstMetas.get(idx) : Map.of();
            return new SearchResult(id, text, score, md);
        }).collect(Collectors.toList());
    }

    private double safeScore(Double d) {
        if (d == null) {
            return 0.0;
        }
        // Chroma returns distance; convert to similarity-like score (1 / (1 + distance))
        return 1.0 / (1.0 + d.doubleValue());
    }

    private String ensureCollection(String collectionName) {
        return collectionIdCache.computeIfAbsent(collectionName, this::createCollection);
    }

    private String createCollection(String collectionName) {
        final Map<String, Object> payload = Map.of("name", collectionName);
        final String url = baseUrl + "/api/v1/collections";
        final Map<String, Object> resp = post(url, payload);
        final Object id = resp.get("id");
        if (id == null) {
            throw new IllegalStateException("Chroma create collection did not return id: " + resp);
        }
        return String.valueOf(id);
    }

    private Map<String, Object> post(String url, Map<String, Object> body) {
        try {
            final String requestBody = json.writeValueAsString(body);
            System.out.println("[DEBUG_LOG] POST " + url);
            final HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            final int code = resp.statusCode();
            System.out.println("[DEBUG_LOG] <-- " + code + " from " + url + (resp.body() != null ? ": " + trunc(resp.body()) : ""));
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Chroma HTTP " + code + " for " + url + ": " + resp.body());
            }
            final String bodyStr = resp.body();
            if (bodyStr == null || bodyStr.isBlank()) {
                return Map.of();
            }
            try {
                return json.readValue(bodyStr, new TypeReference<Map<String, Object>>() {});
            } catch (IOException parseEx) {
                // Some endpoints (e.g., /add) may return bare booleans like "true" with 201 status.
                // In such cases, we don't need a structured response; return an empty map.
                System.out.println("[DEBUG_LOG] Non-JSON object response, ignoring body for " + url + ": " + trunc(bodyStr));
                return Map.of();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call to Chroma failed: " + url, e);
        }
    }




    private static String trunc(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private List<Float> toFloatList(double[] vec) {
        return java.util.Arrays.stream(vec).mapToObj(d -> (float) d).toList();
    }
}
