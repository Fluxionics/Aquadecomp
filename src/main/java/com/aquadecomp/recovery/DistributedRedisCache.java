package com.aquadecomp.recovery;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class DistributedRedisCache {

    private final JedisPool jedisPool;
    private final String cacheKeyPrefix;
    private final long defaultExpirationSeconds;
    private final boolean enabled;
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);

    public DistributedRedisCache(String redisHost, int redisPort) {
        this(redisHost, redisPort, "aquadecomp_cache:", 86400L);
    }

    public DistributedRedisCache(String redisHost, int redisPort, String keyPrefix,
                                 long expirationSeconds) {
        this.cacheKeyPrefix = keyPrefix;
        this.defaultExpirationSeconds = expirationSeconds;

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(2);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 3000);
            this.enabled = testConnection();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Redis pool", e);
        }
    }

    private boolean testConnection() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            return false;
        }
    }

    public RealCompilationVerifier.CompilationResult get(String fileHash) {
        if (!enabled) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = cacheKeyPrefix + fileHash;
            String cached = jedis.get(key);

            if (cached != null) {
                totalHits.incrementAndGet();
                return deserializeResult(cached);
            }

            totalMisses.incrementAndGet();
            return null;
        } catch (Exception e) {
            totalMisses.incrementAndGet();
            return null;
        }
    }

    public void put(String fileHash, RealCompilationVerifier.CompilationResult result) {
        if (!enabled) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = cacheKeyPrefix + fileHash;
            String serialized = serializeResult(result);
            jedis.setex(key, defaultExpirationSeconds, serialized);
        } catch (Exception ignored) {
        }
    }

    public void putWithExpiration(String fileHash, RealCompilationVerifier.CompilationResult result,
                                   long expirationSeconds) {
        if (!enabled) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = cacheKeyPrefix + fileHash;
            String serialized = serializeResult(result);
            jedis.setex(key, expirationSeconds, serialized);
        } catch (Exception ignored) {
        }
    }

    public void invalidate(String fileHash) {
        if (!enabled) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = cacheKeyPrefix + fileHash;
            jedis.del(key);
        } catch (Exception ignored) {
        }
    }

    public void invalidateAll() {
        if (!enabled) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String pattern = cacheKeyPrefix + "*";
            var keys = jedis.keys(pattern);
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        } catch (Exception ignored) {
        }
    }

    public long getExpirationSeconds(String fileHash) {
        if (!enabled) {
            return -1;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = cacheKeyPrefix + fileHash;
            return jedis.ttl(key);
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean exists(String fileHash) {
        if (!enabled) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String key = cacheKeyPrefix + fileHash;
            return jedis.exists(key);
        } catch (Exception e) {
            return false;
        }
    }

    public long getCacheSize() {
        if (!enabled) {
            return 0;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String pattern = cacheKeyPrefix + "*";
            return jedis.keys(pattern).size();
        } catch (Exception e) {
            return 0;
        }
    }

    public void flushAll() {
        invalidateAll();
    }

    public DistributedCacheStats getStats() {
        long total = totalHits.get() + totalMisses.get();
        double hitRate = total > 0 ? (totalHits.get() * 100.0) / total : 0;
        long size = getCacheSize();

        return new DistributedCacheStats(totalHits.get(), totalMisses.get(), size, hitRate, enabled);
    }

    private String serializeResult(RealCompilationVerifier.CompilationResult result) {
        int score = result.score();
        boolean success = result.isSuccess();
        return score + "|" + success;
    }

    private RealCompilationVerifier.CompilationResult deserializeResult(String serialized) {
        try {
            String[] parts = serialized.split("\\|", 2);
            if (parts.length < 2) {
                return null;
            }

            int score = Integer.parseInt(parts[0]);
            boolean success = Boolean.parseBoolean(parts[1]);
            return new RealCompilationVerifier.CompilationResult(success, new ArrayList<>(), score);
        } catch (NumberFormatException ignored) {
        }

        return null;
    }

    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    public static final class DistributedCacheStats {
        private final long hits;
        private final long misses;
        private final long size;
        private final double hitRate;
        private final boolean enabled;

        public DistributedCacheStats(long hits, long misses, long size, double hitRate,
                                     boolean enabled) {
            this.hits = hits;
            this.misses = misses;
            this.size = size;
            this.hitRate = hitRate;
            this.enabled = enabled;
        }

        public long hits() {
            return hits;
        }

        public long misses() {
            return misses;
        }

        public long size() {
            return size;
        }

        public double hitRate() {
            return hitRate;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String summary() {
            if (!enabled) {
                return "Distributed Redis Cache: DISABLED";
            }

            return String.format(
                    "Distributed Redis Cache: %d hits, %d misses (%.1f%% hit rate), %d entries",
                    hits, misses, hitRate, size
            );
        }
    }
}
