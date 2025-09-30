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
    private final java.nio.file.Path idCacheDir;

    public HttpChromaClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl").replaceAll("/$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.json = new ObjectMapper();
        this.idCacheDir = java.nio.file.Path.of(System.getProperty("user.dir"), ".chroma-collections");
    }

    @Override
    public void upsert(List<UpsertEmbedding> items, String collectionName) {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return;
        }
        final String cid = resolveCollectionId(collectionName, true);
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
        final String cid = resolveCollectionId(collectionName, false);
        if (cid == null || cid.isBlank()) {
            // Could not resolve collection; treat as empty results instead of failing
            return List.of();
        }
        final Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", List.of(toFloatList(embedding)));
        body.put("n_results", topK);
        final String url = baseUrl + "/api/v1/collections/" + cid + "/query";
        final Map<String, Object> resp = post(url, body);
        // Response format (typical): { "ids": [[...]], "distances": [[...]], "documents": [[...]], "metadatas": [[...]] }
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
        return resolveCollectionId(collectionName, true);
    }

    private String resolveCollectionId(String collectionName, boolean createIfMissing) {
        Objects.requireNonNull(collectionName, "collectionName");
        // In-memory cache
        final String cached = collectionIdCache.get(collectionName);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        // File cache in project dir
        final String fileId = readCachedId(collectionName);
        if (fileId != null && !fileId.isBlank()) {
            collectionIdCache.put(collectionName, fileId);
            return fileId;
        }
        if (createIfMissing) {
            final String id = createCollection(collectionName);
            if (id != null) {
                writeCachedId(collectionName, id);
                collectionIdCache.put(collectionName, id);
                return id;
            }
            throw new IllegalStateException("Failed to create or resolve collection: " + collectionName);
        } else {
            final String id = getCollectionIdByName(collectionName);
            if (id != null && !id.isBlank()) {
                writeCachedId(collectionName, id);
                collectionIdCache.put(collectionName, id);
                return id;
            }
            // Could not resolve existing collection; return null so callers can handle gracefully
            return null;
        }
    }

    private String createCollection(String collectionName) {
        final Map<String, Object> payload = Map.of("name", collectionName);
        final String url = baseUrl + "/api/v1/collections";
        try {
            final Map<String, Object> resp = post(url, payload);
            final Object id = resp.get("id");
            if (id == null) {
                throw new IllegalStateException("Chroma create collection did not return id: " + resp);
            }
            final String sid = String.valueOf(id);
            writeCachedId(collectionName, sid);
            return sid;
        } catch (IllegalStateException e) {
            if (isConflict(e)) {
                // Collection likely exists; try to find its ID by name via reliable endpoint(s)
                final String id = getCollectionIdByName(collectionName);
                if (id != null) {
                    System.out.println("[DEBUG_LOG] Collection '" + collectionName + "' exists; using id=" + id);
                    writeCachedId(collectionName, id);
                    return id;
                }
            }
            throw e;
        }
    }

    private boolean isConflict(IllegalStateException e) {
        final String msg = String.valueOf(e.getMessage());
        return msg.contains("HTTP 409") || msg.contains("UniqueConstraintError") || msg.contains("already exists");
    }

    @SuppressWarnings("unchecked")
    private String getCollectionIdByName(String name) {
        // 1) Preferred: POST /api/v1/collections/get {"name": "..."}
        try {
            final String byNameUrl = baseUrl + "/api/v1/collections/get";
            final Map<String, Object> resp = post(byNameUrl, Map.of("name", name));
            final Object id = resp.get("id");
            if (id != null) {
                return String.valueOf(id);
            }
            // Some servers nest fields differently; try nested access
            final Object collection = resp.get("collection");
            if (collection instanceof Map<?, ?> cm) {
                final Object nid = cm.get("id");
                if (nid != null) {
                    return String.valueOf(nid);
                }
            }
        } catch (IllegalStateException ignore) {
            // fall through to other strategies
        }

        // 2) Fallback: GET /api/v1/collections?name=...
        try {
            final String url = baseUrl + "/api/v1/collections?name=" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);
            final Object obj = get(url);
            if (obj instanceof Map<?, ?> m) {
                final Object id = m.get("id");
                if (id != null) {
                    return String.valueOf(id);
                }
                final Object collection = m.get("collection");
                if (collection instanceof Map<?, ?> cm) {
                    final Object nid = cm.get("id");
                    if (nid != null) {
                        return String.valueOf(nid);
                    }
                }
            } else if (obj instanceof java.util.List<?> arr) {
                for (Object o : arr) {
                    if (o instanceof Map<?, ?> m) {
                        if (name.equals(String.valueOf(m.get("name")))) {
                            final Object id = m.get("id");
                            if (id != null) {
                                return String.valueOf(id);
                            }
                        }
                    }
                }
            }
        } catch (IllegalStateException ignore) {
            // continue to last resort
        }

        // 3) Last resort: list all and filter (original behavior)
        try {
            final String url = baseUrl + "/api/v1/collections";
            final var list = get(url);
            if (list instanceof java.util.List) {
                for (Object o : ((java.util.List<?>) list)) {
                    if (o instanceof Map<?, ?> m) {
                        if (name.equals(String.valueOf(m.get("name")))) {
                            final Object id = m.get("id");
                            if (id != null) {
                                return String.valueOf(id);
                            }
                        }
                    }
                }
            } else if (list instanceof Map<?, ?> m) {
                final Object collections = m.get("collections");
                if (collections instanceof java.util.List<?> arr) {
                    for (Object o : arr) {
                        if (o instanceof Map<?, ?> cm) {
                            if (name.equals(String.valueOf(cm.get("name")))) {
                                final Object id = cm.get("id");
                                if (id != null) {
                                    return String.valueOf(id);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IllegalStateException ignore) {
            // give up
        }
        return null;
    }

    private Object get(String url) {
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
            try {
                return json.readValue(bodyStr, new TypeReference<List<Map<String, Object>>>() { });
            } catch (IOException e) {
                return json.readValue(bodyStr, new TypeReference<Map<String, Object>>() { });
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call to Chroma failed: " + url, e);
        }
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
                return json.readValue(bodyStr, new TypeReference<Map<String, Object>>() { });
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




    private String readCachedId(String name) {
        try {
            final java.nio.file.Path path = idCacheDir.resolve(safeName(name) + ".id");
            if (java.nio.file.Files.exists(path)) {
                final String s = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
                return s == null ? null : s.trim();
            }
        } catch (Exception ignore) {
            // ignore cache read errors
        }
        return null;
    }

    private void writeCachedId(String name, String id) {
        if (name == null || name.isBlank() || id == null || id.isBlank()) return;
        try {
            java.nio.file.Files.createDirectories(idCacheDir);
            final java.nio.file.Path path = idCacheDir.resolve(safeName(name) + ".id");
            java.nio.file.Files.writeString(path, id, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            // ignore cache write errors
        }
    }

    private static String safeName(String name) {
        return name == null ? "" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String trunc(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private List<Float> toFloatList(double[] vec) {
        return java.util.Arrays.stream(vec).mapToObj(d -> (float) d).toList();
    }
}
