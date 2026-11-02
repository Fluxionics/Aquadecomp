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
        strategies.add(new CfrDecompiler());
        strategies.add(new BaksmaliDecompiler());
    }

    public ChainResult decompile(Path apk, Path outputDir,
                                 DecompilerStrategy.DecompilerConfig config) {
        List<AttemptResult> attempts = new ArrayList<>();
        Path finalSourcesDir = null;
        Path finalResourcesDir = null;
        String selectedDecompiler = null;

        for (DecompilerStrategy strategy : strategies) {
            String strategyName = strategy.name();

            if (!strategy.isAvailable()) {
                attempts.add(new AttemptResult(strategyName, false,
                        strategyName + " no disponible", null, null, 0, 0));
                continue;
            }

            try {
                Path attemptDir = outputDir.resolve("attempt_" + strategyName.toLowerCase());
                Files.createDirectories(attemptDir);

                DecompilerStrategy.DecompilationResult result =
                        strategy.decompile(apk, attemptDir, config);

                if (result.success()) {
                    finalSourcesDir = result.sourcesDir();
                    finalResourcesDir = result.resourcesDir();
                    selectedDecompiler = strategyName;

                    attempts.add(new AttemptResult(strategyName, true, null,
                            result.sourcesDir(), result.resourcesDir(),
                            result.classesDecompiled(), result.errors()));

                    break;

                } else {
                    attempts.add(new AttemptResult(strategyName, false,
                            result.message(), null, null, 0, result.errors()));
                }

            } catch (Exception e) {
                attempts.add(new AttemptResult(strategyName, false,
                        "Exception: " + e.getMessage(), null, null, 0, -1));
            }
        }

        boolean success = finalSourcesDir != null && Files.isDirectory(finalSourcesDir);

        return new ChainResult(
                success,
                finalSourcesDir,
                finalResourcesDir,
                selectedDecompiler,
                attempts
        );
    }

    public record AttemptResult(
            String decompilerName,
            boolean successful,
            String errorMessage,
            Path sourcesDir,
            Path resourcesDir,
            int classesDecompiled,
            int errors
    ) {
    }

    public record ChainResult(
            boolean success,
            Path sourcesDir,
            Path resourcesDir,
            String selectedDecompiler,
            List<AttemptResult> attempts
    ) {
        public boolean hasSucceeded() {
            return success && sourcesDir != null;
        }

        public String getStatusSummary() {
            if (!success) {
                return "Todas las estrategias fallaron";
            }
            return "Éxito con " + selectedDecompiler;
        }
    }
}
