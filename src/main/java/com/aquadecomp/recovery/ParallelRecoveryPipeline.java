package com.aquadecomp.recovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParallelRecoveryPipeline {

    private final int threadCount;
    private final Map<String, RecoveryMetrics> metrics = new ConcurrentHashMap<>();
    private final RecoveryEnginePool enginePool;
    private final CompilationCache compilationCache;
    private final List<RecoveryProgressListener> listeners = new ArrayList<>();

    public ParallelRecoveryPipeline() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ParallelRecoveryPipeline(int threadCount) {
        this(threadCount, true);
    }

    public ParallelRecoveryPipeline(int threadCount, boolean enableCache) {
        this.threadCount = Math.max(1, threadCount);
        this.enginePool = new RecoveryEnginePool(this.threadCount);
        this.compilationCache = new CompilationCache(enableCache);
    }

    public RecoveryResult recover(Path sourcesDir, Path resourcesDir) {
        if (!Files.isDirectory(sourcesDir)) {
            return new RecoveryResult(false, 0, new ArrayList<>(), new HashMap<>());
        }

        List<Path> filesToProcess = new ArrayList<>();
        try {
            Files.walk(sourcesDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(filesToProcess::add);
        } catch (Exception ignored) {
        }

        if (filesToProcess.isEmpty()) {
            return new RecoveryResult(false, 0, new ArrayList<>(), new HashMap<>());
        }

        notifyRecoveryStart(filesToProcess.size());

        AtomicInteger filesRecovered = new AtomicInteger(0);
        List<String> recoveredItems = Collections.synchronizedList(new ArrayList<>());
        Map<String, RecoveryMetrics> fileMetrics = new ConcurrentHashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        final int[] fileCounter = {1};
        for (Path file : filesToProcess) {
            int fileNumber = fileCounter[0]++;
            executor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    notifyFileStart(file, fileNumber, filesToProcess.size());

                    RecoveryMetrics result = recoverFile(file, resourcesDir);
                    fileMetrics.put(file.toString(), result);
                    metrics.put(file.toString(), result);

                    if (result.wasRecovered()) {
                        filesRecovered.incrementAndGet();
                        recoveredItems.addAll(result.recoveredItems());
                    }

                    long elapsedMs = System.currentTimeMillis() - startTime;
                    RecoveryProgressListener.RecoveryResult progressResult =
                            new RecoveryProgressListener.RecoveryResult(
                                    result.compilationScore(),
                                    result.wasRecovered(),
                                    result.isValid(),
                                    elapsedMs
                            );
                    notifyFileComplete(file, fileNumber, filesToProcess.size(), progressResult);
                } catch (Exception e) {
                    notifyError(file, e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        double recoveryRate = filesToProcess.size() > 0 ?
                (filesRecovered.get() * 100.0) / filesToProcess.size() : 0;
        boolean success = recoveryRate >= 95.0;

        notifyRecoveryComplete(filesToProcess.size(), filesRecovered.get(), recoveryRate);

        return new RecoveryResult(success, recoveryRate, recoveredItems, fileMetrics);
    }

    private RecoveryMetrics recoverFile(Path file, Path resourcesDir) throws Exception {
        String originalContent = Files.readString(file);
        String recovered = originalContent;
        List<String> items = new ArrayList<>();
        String className = extractClassName(file);

        RecoveryEnginePool.RecoveryEngineSet engines = enginePool.getEngineSet();

        try {
            recovered = engines.stringDecryption.attemptDecryption(recovered);
            if (!recovered.equals(originalContent)) {
                items.add("String decryption applied");
            }

            engines.nameRecovery.recoverFromKotlinMetadata(recovered, className);
            engines.nameRecovery.recoverFromSourceFile(recovered, className);
            engines.nameRecovery.recoverFromEnumPatterns(recovered, className);
            engines.nameRecovery.recoverFromResourceReferences(recovered);
            engines.nameRecovery.recoverFromCallContext(recovered);

            recovered = engines.deobfuscationEngine.deobfuscate(recovered, className);
            if (!recovered.equals(originalContent)) {
                items.add("Deobfuscation applied");
            }

            recovered = engines.classRecovery.recoverClasses(recovered);
            if (!recovered.equals(originalContent)) {
                items.add("Classes recovered");
            }

            engines.metadataExtractor.extractMetadata(recovered, className);

            if (!recovered.equals(originalContent)) {
                Files.writeString(file, recovered);
            }

            RealCompilationVerifier.CompilationResult compilationResult = null;

            try {
                compilationResult = compilationCache.get(file);
            } catch (IOException ignored) {
            }

            if (compilationResult == null) {
                compilationResult = engines.compilationVerifier.verify(file);
                try {
                    compilationCache.put(file, compilationResult);
                } catch (IOException ignored) {
                }
            }

            int score = compilationResult.score();
            boolean isValid = compilationResult.isSuccess();

            notifyCompilationComplete(file, score, isValid);

            return new RecoveryMetrics(
                    file.toString(),
                    items,
                    score,
                    isValid,
                    !recovered.equals(originalContent)
            );
        } finally {
            enginePool.releaseEngineSet(engines);
        }
    }

    private String extractClassName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }

    public Map<String, RecoveryMetrics> getMetrics() {
        return new HashMap<>(metrics);
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void addProgressListener(RecoveryProgressListener listener) {
        listeners.add(listener);
    }

    public void removeProgressListener(RecoveryProgressListener listener) {
        listeners.remove(listener);
    }

    public CompilationCache.CacheStats getCacheStats() {
        return compilationCache.getStats();
    }

    public void clearCache() {
        compilationCache.clear();
    }

    private void notifyRecoveryStart(int totalFiles) {
        for (RecoveryProgressListener listener : listeners) {
            try {
                listener.onRecoveryStart(totalFiles, threadCount);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyFileStart(Path file, int fileNumber, int totalFiles) {
        for (RecoveryProgressListener listener : listeners) {
            try {
                listener.onFileStart(file, fileNumber, totalFiles);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyCompilationComplete(Path file, int score, boolean success) {
        for (RecoveryProgressListener listener : listeners) {
            try {
                listener.onCompilationComplete(file, score, success);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyFileComplete(Path file, int fileNumber, int totalFiles,
                                    RecoveryProgressListener.RecoveryResult result) {
        for (RecoveryProgressListener listener : listeners) {
            try {
                listener.onFileComplete(file, fileNumber, totalFiles, result);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyRecoveryComplete(int totalFiles, int filesRecovered, double recoveryRate) {
        for (RecoveryProgressListener listener : listeners) {
            try {
                listener.onRecoveryComplete(totalFiles, filesRecovered, recoveryRate);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyError(Path file, String errorMessage) {
        for (RecoveryProgressListener listener : listeners) {
            try {
                listener.onError(file, errorMessage);
            } catch (Exception ignored) {
            }
        }
    }

    public static final class RecoveryResult {
        private final boolean success;
        private final double recoveryRate;
        private final List<String> recoveredItems;
        private final Map<String, RecoveryMetrics> fileMetrics;

        public RecoveryResult(boolean success, double recoveryRate,
                              List<String> recoveredItems,
                              Map<String, RecoveryMetrics> fileMetrics) {
            this.success = success;
            this.recoveryRate = recoveryRate;
            this.recoveredItems = new ArrayList<>(recoveredItems);
            this.fileMetrics = new HashMap<>(fileMetrics);
        }

        public boolean isSuccess() {
            return success;
        }

        public double recoveryRate() {
            return recoveryRate;
        }

        public List<String> recoveredItems() {
            return new ArrayList<>(recoveredItems);
        }

        public Map<String, RecoveryMetrics> fileMetrics() {
            return new HashMap<>(fileMetrics);
        }

        public String summary() {
            return String.format(
                    "Parallel Recovery: %.1f%% - %d items recovered",
                    recoveryRate,
                    recoveredItems.size()
            );
        }
    }

    public static final class RecoveryMetrics {
        private final String filePath;
        private final List<String> recoveredItems;
        private final int compilationScore;
        private final boolean isValid;
        private final boolean wasRecovered;

        public RecoveryMetrics(String filePath, List<String> recoveredItems,
                               int compilationScore, boolean isValid, boolean wasRecovered) {
            this.filePath = filePath;
            this.recoveredItems = new ArrayList<>(recoveredItems);
            this.compilationScore = compilationScore;
            this.isValid = isValid;
            this.wasRecovered = wasRecovered;
        }

        public String filePath() {
            return filePath;
        }

        public List<String> recoveredItems() {
            return new ArrayList<>(recoveredItems);
        }

        public int compilationScore() {
            return compilationScore;
        }

        public boolean isValid() {
            return isValid;
        }

        public boolean wasRecovered() {
            return wasRecovered;
        }
    }
}
