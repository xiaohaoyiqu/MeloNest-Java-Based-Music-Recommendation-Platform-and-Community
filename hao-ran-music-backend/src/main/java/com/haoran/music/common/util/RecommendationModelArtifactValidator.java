package com.haoran.music.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

   
                                                                                      
  
                      
   
public final class RecommendationModelArtifactValidator {

    private static final int CURRENT_SCHEMA_VERSION = 2;

    private RecommendationModelArtifactValidator() {
    }

    public static Map<String, Object> load(ObjectMapper mapper,
                                            String artifactPath,
                                            String expectedModelType,
                                            boolean requireManifest) throws IOException {
        Path path = Paths.get(artifactPath);
        byte[] payload = Files.readAllBytes(path);
        Map<String, Object> model = mapper.readValue(payload, new TypeReference<Map<String, Object>>() { });

        if (requireManifest) {
            validateMetadata(model, expectedModelType);
            validateManifest(mapper, path, payload, model, expectedModelType);
        }
        validatePayloadShape(model, expectedModelType);
        return model;
    }

    private static void validateManifest(ObjectMapper mapper,
                                         Path artifactPath,
                                         byte[] payload,
                                         Map<String, Object> model,
                                         String expectedModelType) throws IOException {
        Path manifestPath = Paths.get(artifactPath.toString() + ".manifest.json");
        Map<String, Object> manifest = mapper.readValue(
                Files.readAllBytes(manifestPath),
                new TypeReference<Map<String, Object>>() { });
        requireInteger(manifest.get("schema_version"), CURRENT_SCHEMA_VERSION, "manifest schema");
        requireEquals(expectedModelType, manifest.get("model_type"), "manifest model type");
        requireEquals(artifactPath.getFileName().toString(), manifest.get("artifact_file"), "artifact file");
        requireInteger(manifest.get("artifact_size"), payload.length, "artifact size");
        requireEquals(sha256(payload), manifest.get("artifact_sha256"), "artifact checksum");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) model.get("metadata");
        requireEquals(String.valueOf(metadata.get("catalog_version")),
                manifest.get("catalog_version"), "catalog version");
        requireEquals(String.valueOf(metadata.get("model_version")),
                manifest.get("model_version"), "model version");
    }

    @SuppressWarnings("unchecked")
    private static void validateMetadata(Map<String, Object> model, String expectedModelType) throws IOException {
        Object rawMetadata = model.get("metadata");
        if (!(rawMetadata instanceof Map)) {
            throw new IOException("model metadata is missing");
        }
        Map<String, Object> metadata = (Map<String, Object>) rawMetadata;
        requireInteger(metadata.get("schema_version"), CURRENT_SCHEMA_VERSION, "model schema");
        requireEquals(expectedModelType, metadata.get("model_type"), "model type");
        String catalogVersion = String.valueOf(metadata.get("catalog_version")).trim();
        if (catalogVersion.isEmpty() || "unknown".equalsIgnoreCase(catalogVersion)) {
            throw new IOException("catalog version is missing");
        }
        String modelVersion = String.valueOf(metadata.get("model_version")).trim();
        if (modelVersion.isEmpty() || "null".equalsIgnoreCase(modelVersion)) {
            throw new IOException("model version is missing");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validatePayloadShape(Map<String, Object> model, String expectedModelType) throws IOException {
        if ("als_collaborative_filtering".equals(expectedModelType)) {
            requireScalarListMap(model.get("recommendations"), "recommendations");
            return;
        }
        if ("audio_similarity".equals(expectedModelType)) {
            if (!(model.get("similarity_matrix") instanceof Map)) {
                throw new IOException("audio similarity matrix is missing");
            }
            return;
        }
        if ("hybrid_recommendation".equals(expectedModelType)) {
            if (!(model.get("global_hot_songs") instanceof List)
                    || !(model.get("genre_index") instanceof Map)) {
                throw new IOException("hybrid fallback payload is incomplete");
            }
            requireScalarListMap(model.get("user_recommendations"), "user recommendations");
        }
    }

    @SuppressWarnings("unchecked")
    private static void requireScalarListMap(Object value, String label) throws IOException {
        if (!(value instanceof Map)) {
            throw new IOException(label + " is missing");
        }
        for (Object entryValue : ((Map<Object, Object>) value).values()) {
            if (!(entryValue instanceof List)) {
                throw new IOException(label + " values must be lists");
            }
            for (Object item : (List<Object>) entryValue) {
                if (!(item instanceof String) && !(item instanceof Number)) {
                    throw new IOException(label + " items must be song ids");
                }
            }
        }
    }

    private static void requireInteger(Object actual, long expected, String label) throws IOException {
        if (!(actual instanceof Number) || ((Number) actual).longValue() != expected) {
            throw new IOException(label + " mismatch");
        }
    }

    private static void requireEquals(String expected, Object actual, String label) throws IOException {
        if (actual == null || !expected.equalsIgnoreCase(String.valueOf(actual))) {
            throw new IOException(label + " mismatch");
        }
    }

    private static String sha256(byte[] payload) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }
}
