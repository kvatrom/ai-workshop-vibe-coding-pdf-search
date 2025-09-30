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
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
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
        final String urlV1 = baseUrl + "/api/v1/collections/" + cid + "/add";
        postV1ThenV2(urlV1, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SearchResult> query(double[] embedding, String collectionName, int topK) {
        final String cid = ensureCollection(collectionName);
        final Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", List.of(toFloatList(embedding)));
        body.put("n_results", topK);
        final String urlV1 = baseUrl + "/api/v1/collections/" + cid + "/query";
        final Map<String, Object> resp = postV1ThenV2(urlV1, body);
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
        // Try multiple endpoints for compatibility across Chroma versions
        final String[] urls = new String[] {
                baseUrl + "/api/v1/collections/get_or_create",
                baseUrl + "/api/v1/collections",
                baseUrl + "/api/v1/collections/"
        };
        final String putByName = baseUrl + "/api/v1/collections/" + java.net.URLEncoder.encode(collectionName, java.nio.charset.StandardCharsets.UTF_8);
        IllegalStateException last = null;
        for (String url : urls) {
            try {
                final Map<String, Object> resp = post(url, payload);
                final Object id = resp.get("id");
                if (id != null) {
                    return String.valueOf(id);
                }
                // Some servers return the full collection object under "collection"
                final Object coll = resp.get("collection");
                if (coll instanceof Map<?, ?> m && m.get("id") != null) {
                    return String.valueOf(m.get("id"));
                }
            } catch (IllegalStateException e) {
                last = e; // keep and try next URL
            }
        }
        // Try PUT by name (some variants may accept this)
        try {
            final Map<String, Object> resp = put(putByName, payload);
            final Object id = resp.get("id");
            if (id != null) {
                return String.valueOf(id);
            }
            final Object coll = resp.get("collection");
            if (coll instanceof Map<?, ?> m && m.get("id") != null) {
                return String.valueOf(m.get("id"));
            }
        } catch (IllegalStateException e) {
            last = e;
        }
        // As a last resort, attempt to list collections and find by name
        try {
            // Some servers may return a single collection on /collections/{name}
            try {
                final Map<String, Object> byName = get(putByName);
                final Object id = byName.get("id");
                final Object n = byName.get("name");
                if (id != null && (n == null || collectionName.equals(String.valueOf(n)))) {
                    return String.valueOf(id);
                }
            } catch (IllegalStateException ignore) {
                // ignore and try list
            }
            final Map<String, Object> listResp = get(baseUrl + "/api/v1/collections");
            Object cols = listResp.get("collections");
            if (cols == null) cols = listResp.get("results");
            if (cols instanceof List<?> arr) {
                for (Object o : arr) {
                    if (o instanceof Map<?, ?> m) {
                        final Object n2 = m.get("name");
                        final Object id2 = m.get("id");
                        if (n2 != null && collectionName.equals(String.valueOf(n2)) && id2 != null) {
                            return String.valueOf(id2);
                        }
                    }
                }
            }
        } catch (IllegalStateException ignore) {
            // fall through to throw last error
        }
        // If v1 seems deprecated, try the same sequence under /api/v2
        if (last != null && isV1Deprecated(last)) {
            final String[] urlsV2 = new String[] {
                    baseUrl + "/api/v2/collections/get_or_create",
                    baseUrl + "/api/v2/collections",
                    baseUrl + "/api/v2/collections/"
            };
            IllegalStateException lastV2 = null;
            for (String url : urlsV2) {
                try {
                    final Map<String, Object> resp = post(url, payload);
                    final Object id = resp.get("id");
                    if (id != null) {
                        return String.valueOf(id);
                    }
                    final Object coll = resp.get("collection");
                    if (coll instanceof Map<?, ?> m && m.get("id") != null) {
                        return String.valueOf(m.get("id"));
                    }
                } catch (IllegalStateException e2) {
                    lastV2 = e2;
                }
            }
            // Try PUT/GET by name under v2
            final String putByNameV2 = putByName.replace("/api/v1/", "/api/v2/");
            try {
                final Map<String, Object> resp = put(putByNameV2, payload);
                final Object id = resp.get("id");
                if (id != null) return String.valueOf(id);
                final Object coll = resp.get("collection");
                if (coll instanceof Map<?, ?> m && m.get("id") != null) {
                    return String.valueOf(m.get("id"));
                }
            } catch (IllegalStateException e2) {
                lastV2 = e2;
            }
            try {
                try {
                    final Map<String, Object> byName = get(putByNameV2);
                    final Object id = byName.get("id");
                    final Object n = byName.get("name");
                    if (id != null && (n == null || collectionName.equals(String.valueOf(n)))) {
                        return String.valueOf(id);
                    }
                } catch (IllegalStateException ignore) {
                    // ignore and try list
                }
                final Map<String, Object> listResp = get(baseUrl + "/api/v2/collections");
                Object cols = listResp.get("collections");
                if (cols == null) cols = listResp.get("results");
                if (cols instanceof List<?> arr) {
                    for (Object o : arr) {
                        if (o instanceof Map<?, ?> m) {
                            final Object n2 = m.get("name");
                            final Object id2 = m.get("id");
                            if (n2 != null && collectionName.equals(String.valueOf(n2)) && id2 != null) {
                                return String.valueOf(id2);
                            }
                        }
                    }
                }
            } catch (IllegalStateException ignore) {
                // fall through
            }
            if (lastV2 != null) {
                throw lastV2;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("Failed to create or find collection '" + collectionName + "'");
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
            return json.readValue(bodyStr, new TypeReference<Map<String, Object>>() {});
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call to Chroma failed: " + url, e);
        }
    }

    private Map<String, Object> put(String url, Map<String, Object> body) {
        try {
            final String requestBody = json.writeValueAsString(body);
            System.out.println("[DEBUG_LOG] PUT  " + url);
            final HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
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
            return json.readValue(bodyStr, new TypeReference<Map<String, Object>>() {});
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call to Chroma failed: " + url, e);
        }
    }

    private Map<String, Object> postV1ThenV2(String urlV1, Map<String, Object> body) {
        try {
            return post(urlV1, body);
        } catch (IllegalStateException e) {
            if (isV1Deprecated(e) && urlV1.contains("/api/v1/")) {
                final String urlV2 = urlV1.replace("/api/v1/", "/api/v2/");
                System.out.println("[DEBUG_LOG] v1 endpoint deprecated; retrying as v2: " + urlV2);
                return post(urlV2, body);
            }
            throw e;
        }
    }

    private boolean isV1Deprecated(IllegalStateException e) {
        final String msg = String.valueOf(e.getMessage());
        return msg.contains("v1 API is deprecated") || msg.contains("HTTP 410") || msg.contains("/v2");
    }

    private Map<String, Object> get(String url) {
        try {
            System.out.println("[DEBUG_LOG] GET  " + url);
            final HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
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
            return json.readValue(bodyStr, new TypeReference<Map<String, Object>>() {});
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
