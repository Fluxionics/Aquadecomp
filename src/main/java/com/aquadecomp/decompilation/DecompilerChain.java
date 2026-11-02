package com.aquadecomp.decompilation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DecompilerChain {

    private final List<DecompilerStrategy> strategies;

    public DecompilerChain() {
        this.strategies = new ArrayList<>();
        initializeStrategies();
    }

    private void initializeStrategies() {
        strategies.add(new JadxDecompilerWrapper());
        strategies.add(new VineflowerDecompiler());
        strategies.add(new ProcyonDecompiler());
        strategies.add(new CfrDecompiler());
        strategies.add(new BaksmaliDecompiler());
    }

    public ChainResult decompile(Path apk, Path outputDir,
                                 DecompilerStrategy.DecompilerConfig config) {
        List<AttemptResult> attempts = new ArrayList<>();
        Path finalSourcesDir = null;
        Path finalResourcesDir = null;
        String selectedDecompiler = null;
        int bestClassCount = 0;

        for (DecompilerStrategy strategy : strategies) {
            String strategyName = strategy.name();

            if (!strategy.isAvailable()) {
                attempts.add(new AttemptResult(strategyName, false,
                        strategyName + " no disponible", null, null, 0, 0, false));
                continue;
            }

            try {
                Path attemptDir = outputDir.resolve("attempt_" + strategyName.toLowerCase());
                Files.createDirectories(attemptDir);

                DecompilerStrategy.DecompilationResult result =
                        strategy.decompile(apk, attemptDir, config);

                if (result.success()) {
                    int classCount = countJavaFiles(result.sourcesDir());
                    
                    if (classCount > bestClassCount) {
                        bestClassCount = classCount;
                    }

                    if (classCount >= 50) {
                        if (finalSourcesDir == null) {
                            finalSourcesDir = result.sourcesDir();
                            finalResourcesDir = result.resourcesDir();
                            selectedDecompiler = strategyName;
                        }
                    }

                    attempts.add(new AttemptResult(strategyName, true, null,
                            result.sourcesDir(), result.resourcesDir(),
                            classCount, result.errors(), false));

                    if (finalSourcesDir != null) {
                        break;
                    }

                } else {
                    attempts.add(new AttemptResult(strategyName, false,
                            result.message(), null, null, 0, result.errors(), false));
                }

            } catch (OutOfMemoryError e) {
                attempts.add(new AttemptResult(strategyName, false,
                        "OutOfMemoryError", null, null, 0, 0, false));
            } catch (Exception e) {
                attempts.add(new AttemptResult(strategyName, false,
                        "Error: " + e.getMessage(), null, null, 0, -1, false));
            }
        }

        if (finalSourcesDir == null && bestClassCount > 0) {
            for (AttemptResult attempt : attempts) {
                if (attempt.successful && attempt.classesDecompiled > 0) {
                    finalSourcesDir = attempt.sourcesDir();
                    finalResourcesDir = attempt.resourcesDir();
                    selectedDecompiler = attempt.decompilerName();
                    break;
                }
            }
        }

        boolean success = finalSourcesDir != null && Files.isDirectory(finalSourcesDir);

        return new ChainResult(
                success,
                finalSourcesDir,
                finalResourcesDir,
                selectedDecompiler,
                attempts,
                bestClassCount
        );
    }

    private int countJavaFiles(Path sourcesDir) {
        try {
            return (int) Files.walk(sourcesDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    public record AttemptResult(
            String decompilerName,
            boolean successful,
            String errorMessage,
            Path sourcesDir,
            Path resourcesDir,
            int classesDecompiled,
            int errors,
            boolean timedOut
    ) {
    }

    public record ChainResult(
            boolean success,
            Path sourcesDir,
            Path resourcesDir,
            String selectedDecompiler,
            List<AttemptResult> attempts,
            int bestClassCount
    ) {
        public boolean hasSucceeded() {
            return success && sourcesDir != null;
        }

        public String getStatusSummary() {
            if (!success) {
                return "Todas las estrategias fallaron (mejor: " + bestClassCount + " clases)";
            }
            return "Éxito con " + selectedDecompiler + " (" + bestClassCount + " clases)";
        }
    }
}
