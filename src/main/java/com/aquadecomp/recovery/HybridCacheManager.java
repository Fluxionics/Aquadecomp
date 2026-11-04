package com.aquadecomp.recovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public final class HybridCacheManager {

    private final CompilationCache localMemoryCache;
    private final PersistentCompilationCache persistentCache;
    private final DistributedRedisCache redisCache;
    private final CacheStrategy strategy;
    private final AtomicLong layerOneHits = new AtomicLong(0);
    private final AtomicLong layerTwoHits = new AtomicLong(0);
    private final AtomicLong layerThreeHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);

    public enum CacheStrategy {
        LOCAL_ONLY,
        LOCAL_PERSISTENT,
        LOCAL_REDIS,
        LOCAL_PERSISTENT_REDIS,
        REDIS_ONLY
    }

    public HybridCacheManager(CacheStrategy strategy) throws IOException {
        this(strategy, null, null, null, null);
    }

    public HybridCacheManager(CacheStrategy strategy, Path persistentCacheDir,
                              String redisHost, Integer redisPort) throws IOException {
        this(strategy, persistentCacheDir, redisHost, redisPort, null);
    }

    public HybridCacheManager(CacheStrategy strategy, Path persistentCacheDir,
                              String redisHost, Integer redisPort, String cacheKeyPrefix)
            throws IOException {
        this.strategy = strategy;
        this.localMemoryCache = new CompilationCache(true);

        PersistentCompilationCache tempPersistentCache = null;
        DistributedRedisCache tempRedisCache = null;

        if (strategy == CacheStrategy.LOCAL_PERSISTENT ||
            strategy == CacheStrategy.LOCAL_PERSISTENT_REDIS) {
            if (persistentCacheDir != null) {
                tempPersistentCache = new PersistentCompilationCache(persistentCacheDir);
            }
        }

        if (strategy == CacheStrategy.LOCAL_REDIS ||
            strategy == CacheStrategy.LOCAL_PERSISTENT_REDIS ||
            strategy == CacheStrategy.REDIS_ONLY) {
            if (redisHost != null && redisPort != null) {
                String prefix = cacheKeyPrefix != null ? cacheKeyPrefix : "aquadecomp_cache:";
                tempRedisCache = new DistributedRedisCache(redisHost, redisPort, prefix, 86400L);
            }
        }

        this.persistentCache = tempPersistentCache;
        this.redisCache = tempRedisCache;
    }

    public RealCompilationVerifier.CompilationResult get(Path javaFile) throws IOException {
        switch (strategy) {
            case LOCAL_ONLY:
                return getLocalOnly(javaFile);
            case LOCAL_PERSISTENT:
                return getLocalPersistent(javaFile);
            case LOCAL_REDIS:
                return getLocalRedis(javaFile);
            case LOCAL_PERSISTENT_REDIS:
                return getLocalPersistentRedis(javaFile);
            case REDIS_ONLY:
                return getRedisOnly(javaFile);
            default:
                return null;
        }
    }

    public void put(Path javaFile, RealCompilationVerifier.CompilationResult result)
            throws IOException {
        switch (strategy) {
            case LOCAL_ONLY:
                putLocalOnly(javaFile, result);
                break;
            case LOCAL_PERSISTENT:
                putLocalPersistent(javaFile, result);
                break;
            case LOCAL_REDIS:
                putLocalRedis(javaFile, result);
                break;
            case LOCAL_PERSISTENT_REDIS:
                putLocalPersistentRedis(javaFile, result);
                break;
            case REDIS_ONLY:
                putRedisOnly(javaFile, result);
                break;
        }
    }

    private RealCompilationVerifier.CompilationResult getLocalOnly(Path javaFile)
            throws IOException {
        RealCompilationVerifier.CompilationResult result = localMemoryCache.get(javaFile);
        if (result != null) {
            layerOneHits.incrementAndGet();
        } else {
            totalMisses.incrementAndGet();
        }
        return result;
    }

    private RealCompilationVerifier.CompilationResult getLocalPersistent(Path javaFile)
            throws IOException {
        RealCompilationVerifier.CompilationResult result = localMemoryCache.get(javaFile);
        if (result != null) {
            layerOneHits.incrementAndGet();
            return result;
        }

        if (persistentCache != null) {
            result = persistentCache.get(javaFile);
            if (result != null) {
                layerTwoHits.incrementAndGet();
                localMemoryCache.put(javaFile, result);
                return result;
            }
        }

        totalMisses.incrementAndGet();
        return null;
    }

    private RealCompilationVerifier.CompilationResult getLocalRedis(Path javaFile)
            throws IOException {
        RealCompilationVerifier.CompilationResult result = localMemoryCache.get(javaFile);
        if (result != null) {
            layerOneHits.incrementAndGet();
            return result;
        }

        if (redisCache != null) {
            String fileHash = hashFile(javaFile);
            result = redisCache.get(fileHash);
            if (result != null) {
                layerTwoHits.incrementAndGet();
                localMemoryCache.put(javaFile, result);
                return result;
            }
        }

        totalMisses.incrementAndGet();
        return null;
    }

    private RealCompilationVerifier.CompilationResult getLocalPersistentRedis(Path javaFile)
            throws IOException {
        RealCompilationVerifier.CompilationResult result = localMemoryCache.get(javaFile);
        if (result != null) {
            layerOneHits.incrementAndGet();
            return result;
        }

        if (persistentCache != null) {
            result = persistentCache.get(javaFile);
            if (result != null) {
                layerTwoHits.incrementAndGet();
                localMemoryCache.put(javaFile, result);
                return result;
            }
        }

        if (redisCache != null) {
            String fileHash = hashFile(javaFile);
            result = redisCache.get(fileHash);
            if (result != null) {
                layerThreeHits.incrementAndGet();
                localMemoryCache.put(javaFile, result);
                if (persistentCache != null) {
                    persistentCache.put(javaFile, result);
                }
                return result;
            }
        }

        totalMisses.incrementAndGet();
        return null;
    }

    private RealCompilationVerifier.CompilationResult getRedisOnly(Path javaFile)
            throws IOException {
        if (redisCache != null) {
            String fileHash = hashFile(javaFile);
            RealCompilationVerifier.CompilationResult result = redisCache.get(fileHash);
            if (result != null) {
                layerOneHits.incrementAndGet();
                return result;
            }
        }

        totalMisses.incrementAndGet();
        return null;
    }

    private void putLocalOnly(Path javaFile, RealCompilationVerifier.CompilationResult result)
            throws IOException {
        localMemoryCache.put(javaFile, result);
    }

    private void putLocalPersistent(Path javaFile, RealCompilationVerifier.CompilationResult result)
            throws IOException {
        localMemoryCache.put(javaFile, result);
        if (persistentCache != null) {
            persistentCache.put(javaFile, result);
        }
    }

    private void putLocalRedis(Path javaFile, RealCompilationVerifier.CompilationResult result)
            throws IOException {
        localMemoryCache.put(javaFile, result);
        if (redisCache != null) {
            String fileHash = hashFile(javaFile);
            redisCache.put(fileHash, result);
        }
    }

    private void putLocalPersistentRedis(Path javaFile,
                                         RealCompilationVerifier.CompilationResult result)
            throws IOException {
        localMemoryCache.put(javaFile, result);
        if (persistentCache != null) {
            persistentCache.put(javaFile, result);
        }
        if (redisCache != null) {
            String fileHash = hashFile(javaFile);
            redisCache.put(fileHash, result);
        }
    }

    private void putRedisOnly(Path javaFile, RealCompilationVerifier.CompilationResult result)
            throws IOException {
        if (redisCache != null) {
            String fileHash = hashFile(javaFile);
            redisCache.put(fileHash, result);
        }
    }

    private String hashFile(Path javaFile) throws IOException {
        byte[] fileContent = java.nio.file.Files.readAllBytes(javaFile);
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
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

    public void invalidate(Path javaFile) throws IOException {
        localMemoryCache.clear();
        if (persistentCache != null) {
            persistentCache.clear();
        }
        if (redisCache != null) {
            String fileHash = hashFile(javaFile);
            redisCache.invalidate(fileHash);
        }
    }

    public void invalidateAll() {
        localMemoryCache.clear();
        if (persistentCache != null) {
            persistentCache.clear();
        }
        if (redisCache != null) {
            redisCache.invalidateAll();
        }
    }

    public HybridCacheStats getStats() {
        PersistentCompilationCache.CacheStats persistentStats = null;
        DistributedRedisCache.DistributedCacheStats redisStats = null;

        if (persistentCache != null) {
            persistentStats = persistentCache.getStats();
        }

        if (redisCache != null) {
            redisStats = redisCache.getStats();
        }

        return new HybridCacheStats(
                strategy,
                layerOneHits.get(),
                layerTwoHits.get(),
                layerThreeHits.get(),
                totalMisses.get(),
                persistentStats,
                redisStats
        );
    }

    public void close() {
        if (redisCache != null) {
            redisCache.close();
        }
    }

    public static final class HybridCacheStats {
        private final CacheStrategy strategy;
        private final long layerOneHits;
        private final long layerTwoHits;
        private final long layerThreeHits;
        private final long totalMisses;
        private final PersistentCompilationCache.CacheStats persistentStats;
        private final DistributedRedisCache.DistributedCacheStats redisStats;

        public HybridCacheStats(CacheStrategy strategy, long layerOneHits, long layerTwoHits,
                                long layerThreeHits, long totalMisses,
                                PersistentCompilationCache.CacheStats persistentStats,
                                DistributedRedisCache.DistributedCacheStats redisStats) {
            this.strategy = strategy;
            this.layerOneHits = layerOneHits;
            this.layerTwoHits = layerTwoHits;
            this.layerThreeHits = layerThreeHits;
            this.totalMisses = totalMisses;
            this.persistentStats = persistentStats;
            this.redisStats = redisStats;
        }

        public CacheStrategy strategy() {
            return strategy;
        }

        public long layerOneHits() {
            return layerOneHits;
        }

        public long layerTwoHits() {
            return layerTwoHits;
        }

        public long layerThreeHits() {
            return layerThreeHits;
        }

        public long totalMisses() {
            return totalMisses;
        }

        public PersistentCompilationCache.CacheStats persistentStats() {
            return persistentStats;
        }

        public DistributedRedisCache.DistributedCacheStats redisStats() {
            return redisStats;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Hybrid Cache (%s):\n", strategy));
            sb.append(String.format("  Layer 1 (Memory): %d hits\n", layerOneHits));
            sb.append(String.format("  Layer 2 (Persistent): %d hits\n", layerTwoHits));
            sb.append(String.format("  Layer 3 (Redis): %d hits\n", layerThreeHits));
            sb.append(String.format("  Total Misses: %d\n", totalMisses));

            long totalHits = layerOneHits + layerTwoHits + layerThreeHits;
            long total = totalHits + totalMisses;
            double overallHitRate = total > 0 ? (totalHits * 100.0) / total : 0;
            sb.append(String.format("  Overall Hit Rate: %.1f%%\n", overallHitRate));

            if (persistentStats != null) {
                sb.append("  ").append(persistentStats.summary().replace(", ", ", ")).append("\n");
            }

            if (redisStats != null) {
                sb.append("  ").append(redisStats.summary()).append("\n");
            }

            return sb.toString();
        }
    }
}
