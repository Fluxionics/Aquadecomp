package com.aquadecomp.recovery;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;

public final class ThreadMetricsCollector {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ConcurrentHashMap<Long, ThreadMetrics> threadMetrics = new ConcurrentHashMap<>();

    public ThreadMetricsCollector() {
        threadMXBean.setThreadCpuTimeEnabled(true);
        threadMXBean.setThreadContentionMonitoringEnabled(true);
    }

    public void recordThreadStart(String threadName) {
        long threadId = Thread.currentThread().getId();
        threadMetrics.put(threadId, ThreadMetrics.start(threadId, threadName));
    }

    public void recordThreadEnd(String threadName) {
        long threadId = Thread.currentThread().getId();
        long cpuTimeNano = threadMXBean.getThreadCpuTime(threadId);
        long wallTimeMs = System.currentTimeMillis();
        long memoryUsedBytes = memoryMXBean.getHeapMemoryUsage().getUsed();

        ThreadMetrics end = ThreadMetrics.end(threadId, threadName, wallTimeMs,
                cpuTimeNano, memoryUsedBytes);
        threadMetrics.put(threadId, end);
    }

    public ThreadMetrics getThreadMetrics(long threadId) {
        return threadMetrics.get(threadId);
    }

    public SystemMetrics getSystemMetrics() {
        long totalCpuTimeNano = 0;
        long totalWallTimeMs = 0;
        long totalMemoryUsedBytes = 0;
        int threadCount = 0;

        for (ThreadMetrics metrics : threadMetrics.values()) {
            if (metrics.cpuTimeNano > 0) {
                totalCpuTimeNano += metrics.cpuTimeNano;
                totalWallTimeMs = Math.max(totalWallTimeMs, metrics.wallTimeMs);
                totalMemoryUsedBytes += metrics.memoryUsedBytes;
                threadCount++;
            }
        }

        return new SystemMetrics(
                threadCount,
                totalCpuTimeNano,
                totalWallTimeMs,
                totalMemoryUsedBytes,
                memoryMXBean.getHeapMemoryUsage().getUsed(),
                memoryMXBean.getHeapMemoryUsage().getMax()
        );
    }

    public void clear() {
        threadMetrics.clear();
    }

    public static final class ThreadMetrics {
        private final long threadId;
        private final String threadName;
        private final long wallTimeMs;
        private final long cpuTimeNano;
        private final long memoryUsedBytes;

        public static ThreadMetrics start(long threadId, String threadName) {
            return new ThreadMetrics(threadId, threadName, 0, 0, 0);
        }

        public static ThreadMetrics end(long threadId, String threadName, long wallTimeMs,
                                       long cpuTimeNano, long memoryUsedBytes) {
            return new ThreadMetrics(threadId, threadName, wallTimeMs, cpuTimeNano, memoryUsedBytes);
        }

        private ThreadMetrics(long threadId, String threadName, long wallTimeMs,
                           long cpuTimeNano, long memoryUsedBytes) {
            this.threadId = threadId;
            this.threadName = threadName;
            this.wallTimeMs = wallTimeMs;
            this.cpuTimeNano = cpuTimeNano;
            this.memoryUsedBytes = memoryUsedBytes;
        }

        public long threadId() {
            return threadId;
        }

        public String threadName() {
            return threadName;
        }

        public long wallTimeMs() {
            return wallTimeMs;
        }

        public long cpuTimeMs() {
            return cpuTimeNano / 1_000_000L;
        }

        public long memoryUsedMB() {
            return memoryUsedBytes / (1024 * 1024);
        }

        public String summary() {
            return String.format(
                    "Thread %s: wall=%dms, cpu=%dms, memory=%d MB",
                    threadName, wallTimeMs, cpuTimeMs(), memoryUsedMB()
            );
        }
    }

    public static final class SystemMetrics {
        private final int activeThreads;
        private final long totalCpuTimeNano;
        private final long totalWallTimeMs;
        private final long totalMemoryUsedBytes;
        private final long currentHeapUsedBytes;
        private final long maxHeapBytes;

        public SystemMetrics(int activeThreads, long totalCpuTimeNano,
                           long totalWallTimeMs, long totalMemoryUsedBytes,
                           long currentHeapUsedBytes, long maxHeapBytes) {
            this.activeThreads = activeThreads;
            this.totalCpuTimeNano = totalCpuTimeNano;
            this.totalWallTimeMs = totalWallTimeMs;
            this.totalMemoryUsedBytes = totalMemoryUsedBytes;
            this.currentHeapUsedBytes = currentHeapUsedBytes;
            this.maxHeapBytes = maxHeapBytes;
        }

        public int activeThreads() {
            return activeThreads;
        }

        public long totalCpuTimeMs() {
            return totalCpuTimeNano / 1_000_000L;
        }

        public long totalWallTimeMs() {
            return totalWallTimeMs;
        }

        public long totalMemoryUsedMB() {
            return totalMemoryUsedBytes / (1024 * 1024);
        }

        public double cpuEfficiency() {
            return totalWallTimeMs > 0 ? (totalCpuTimeMs() * 100.0) / (totalWallTimeMs * activeThreads) : 0;
        }

        public long currentHeapUsedMB() {
            return currentHeapUsedBytes / (1024 * 1024);
        }

        public long maxHeapMB() {
            return maxHeapBytes / (1024 * 1024);
        }

        public double heapUsagePercent() {
            return maxHeapBytes > 0 ? (currentHeapUsedBytes * 100.0) / maxHeapBytes : 0;
        }

        public String summary() {
            return String.format(
                    "System: %d threads, total_cpu=%dms, wall=%dms, " +
                    "efficiency=%.1f%%, heap=%d MB / %d MB (%.1f%%)",
                    activeThreads, totalCpuTimeMs(), totalWallTimeMs,
                    cpuEfficiency(), currentHeapUsedMB(), maxHeapMB(), heapUsagePercent()
            );
        }
    }
}
