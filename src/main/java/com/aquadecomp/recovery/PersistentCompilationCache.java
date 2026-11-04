package com.aquadecomp.recovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class PersistentCompilationCache {

    private final ConcurrentHashMap<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final Path cacheDirectory;
    private final long maxMemorySizeBytes;
    private final boolean enabled;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private long currentMemorySizeBytes = 0;
    private long totalHits = 0;
    private long totalMisses = 0;

    public PersistentCompilationCache(Path cacheDirectory) throws IOException {
        this(cacheDirectory, 104_857_600L);
    }

    public PersistentCompilationCache(Path cacheDirectory, long maxMemorySizeBytes) throws IOException {
        this.cacheDirectory = cacheDirectory;
        this.maxMemorySizeBytes = maxMemorySizeBytes;
        this.enabled = true;

        Files.createDirectories(cacheDirectory);
        loadCacheFromDisk();
    }

    public RealCompilationVerifier.CompilationResult get(Path javaFile) throws IOException {
        if (!enabled) {
            return null;
        }

        String fileHash = hashFile(javaFile);
        lock.readLock().lock();
        try {
            CacheEntry entry = memoryCache.get(fileHash);
            if (entry != null) {
                totalHits++;
                return entry.result();
            }
        } finally {
            lock.readLock().unlock();
        }

        try {
            return loadFromDisk(fileHash);
        } catch (IOException ignored) {
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
        CacheEntry entry = new CacheEntry(result);

        lock.writeLock().lock();
        try {
            CacheEntry old = memoryCache.put(fileHash, entry);
            if (old != null) {
                currentMemorySizeBytes -= old.estimatedSize();
            }
            currentMemorySizeBytes += entry.estimatedSize();

            if (currentMemorySizeBytes > maxMemorySizeBytes) {
                evictLRU();
            }

            saveToDisk(fileHash, result);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void evictLRU() {
        memoryCache.entrySet().stream()
                .min((a, b) -> Long.compare(a.getValue().accessTime(), b.getValue().accessTime()))
                .ifPresent(entry -> {
                    currentMemorySizeBytes -= entry.getValue().estimatedSize();
                    memoryCache.remove(entry.getKey());
                });
    }

    private void loadCacheFromDisk() throws IOException {
        if (!Files.exists(cacheDirectory)) {
            return;
        }

        Files.list(cacheDirectory)
                .filter(p -> p.toString().endsWith(".cache"))
                .forEach(p -> {
                    try {
                        String fileHash = p.getFileName().toString()
                                .replace(".cache", "");
                        RealCompilationVerifier.CompilationResult result = loadFromDiskFile(p);
                        if (result != null) {
                            CacheEntry entry = new CacheEntry(result);
                            memoryCache.put(fileHash, entry);
                            currentMemorySizeBytes += entry.estimatedSize();
                        }
                    } catch (Exception ignored) {
                    }
                });
    }

    private RealCompilationVerifier.CompilationResult loadFromDisk(String fileHash)
            throws IOException {
        Path cacheFile = cacheDirectory.resolve(fileHash + ".cache");
        return loadFromDiskFile(cacheFile);
    }

    private RealCompilationVerifier.CompilationResult loadFromDiskFile(Path cacheFile)
            throws IOException {
        if (!Files.exists(cacheFile)) {
            return null;
        }

        String content = Files.readString(cacheFile);
        String[] parts = content.split("\\|", 2);
        if (parts.length < 2) {
            return null;
        }

        try {
            int score = Integer.parseInt(parts[0]);
            boolean success = Boolean.parseBoolean(parts[1]);
            return new RealCompilationVerifier.CompilationResult(success, new ArrayList<>(), score);
        } catch (NumberFormatException ignored) {
        }

        return null;
    }

    private void saveToDisk(String fileHash, RealCompilationVerifier.CompilationResult result)
            throws IOException {
        Path cacheFile = cacheDirectory.resolve(fileHash + ".cache");
        String content = result.score() + "|" + result.isSuccess();
        Files.writeString(cacheFile, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            memoryCache.clear();
            currentMemorySizeBytes = 0;
            totalHits = 0;
            totalMisses = 0;

            Files.list(cacheDirectory)
                    .filter(p -> p.toString().endsWith(".cache"))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            long total = totalHits + totalMisses;
            double hitRate = total > 0 ? (totalHits * 100.0) / total : 0;
            return new CacheStats(totalHits, totalMisses, memoryCache.size(),
                    hitRate, currentMemorySizeBytes, maxMemorySizeBytes);
        } finally {
            lock.readLock().unlock();
        }
    }

    private String hashFile(Path javaFile) throws IOException {
        byte[] fileContent = Files.readAllBytes(javaFile);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
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
        private volatile long lastAccessTime;

        public CacheEntry(RealCompilationVerifier.CompilationResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
            this.lastAccessTime = timestamp;
        }

        public RealCompilationVerifier.CompilationResult result() {
            this.lastAccessTime = System.currentTimeMillis();
            return result;
        }

        public long accessTime() {
            return lastAccessTime;
        }

        public long estimatedSize() {
            return 256;
        }
    }

    public static final class CacheStats {
        private final long hits;
        private final long misses;
        private final int cacheSize;
        private final double hitRate;
        private final long currentMemory;
        private final long maxMemory;

        public CacheStats(long hits, long misses, int cacheSize, double hitRate,
                          long currentMemory, long maxMemory) {
            this.hits = hits;
            this.misses = misses;
            this.cacheSize = cacheSize;
            this.hitRate = hitRate;
            this.currentMemory = currentMemory;
            this.maxMemory = maxMemory;
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

        public long currentMemoryBytes() {
            return currentMemory;
        }

        public long maxMemoryBytes() {
            return maxMemory;
        }

        public double memoryUsagePercent() {
            return maxMemory > 0 ? (currentMemory * 100.0) / maxMemory : 0;
        }

        public String summary() {
            return String.format(
                    "Persistent Cache: %d hits, %d misses (%.1f%% hit rate), " +
                    "%d entries, %.1f MB / %.1f MB (%.1f%%)",
                    hits, misses, hitRate, cacheSize,
                    currentMemory / (1024.0 * 1024.0),
                    maxMemory / (1024.0 * 1024.0),
                    memoryUsagePercent()
            );
        }
    }
}
