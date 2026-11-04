package com.aquadecomp.recovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class CompilationCache {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final boolean enabled;
    private long totalHits = 0;
    private long totalMisses = 0;

    public CompilationCache() {
        this(true);
    }

    public CompilationCache(boolean enabled) {
        this.enabled = enabled;
    }

    public RealCompilationVerifier.CompilationResult get(Path javaFile) throws IOException {
        if (!enabled) {
            return null;
        }

        String fileHash = hashFile(javaFile);
        CacheEntry entry = cache.get(fileHash);

        if (entry != null) {
            totalHits++;
            return entry.result;
        }

        totalMisses++;
        return null;
    }

    public void put(Path javaFile, RealCompilationVerifier.CompilationResult result)
            throws IOException {
        if (!enabled) {
            return;
        }

        String fileHash = hashFile(javaFile);
        cache.put(fileHash, new CacheEntry(result));
    }

    public void clear() {
        cache.clear();
        totalHits = 0;
        totalMisses = 0;
    }

    public CacheStats getStats() {
        long total = totalHits + totalMisses;
        double hitRate = total > 0 ? (totalHits * 100.0) / total : 0;
        return new CacheStats(totalHits, totalMisses, cache.size(), hitRate);
    }

    private String hashFile(Path javaFile) throws IOException {
        byte[] fileContent = Files.readAllBytes(javaFile);
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception ignored) {
            return javaFile.toString();
        }

        byte[] hash = md.digest(fileContent);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static final class CacheEntry {
        private final RealCompilationVerifier.CompilationResult result;
        private final long timestamp;

        public CacheEntry(RealCompilationVerifier.CompilationResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        public RealCompilationVerifier.CompilationResult result() {
            return result;
        }

        public long timestamp() {
            return timestamp;
        }
    }

    public static final class CacheStats {
        private final long hits;
        private final long misses;
        private final int cacheSize;
        private final double hitRate;

        public CacheStats(long hits, long misses, int cacheSize, double hitRate) {
            this.hits = hits;
            this.misses = misses;
            this.cacheSize = cacheSize;
            this.hitRate = hitRate;
        }

        public long hits() {
            return hits;
        }

        public long misses() {
            return misses;
        }

        public int cacheSize() {
            return cacheSize;
        }

        public double hitRate() {
            return hitRate;
        }

        public String summary() {
            return String.format(
                    "Cache: %d hits, %d misses (%.1f%% hit rate), %d cached results",
                    hits, misses, hitRate, cacheSize
            );
        }
    }
}
