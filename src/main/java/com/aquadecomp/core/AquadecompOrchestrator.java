package com.aquadecomp.core;

import com.aquadecomp.decompilation.DecompilerChain;
import com.aquadecomp.decompilation.DecompilerStrategy;
import com.aquadecomp.input.InputNormalizer;
import com.aquadecomp.verification.ConfidenceScorer;
import com.aquadecomp.verification.ConfidenceMap;

import java.nio.file.Path;

public final class AquadecompOrchestrator {

    private final InputNormalizer normalizer = new InputNormalizer();
    private final DecompilerChain decompilerChain = new DecompilerChain();
    private final ConfidenceScorer scorer = new ConfidenceScorer();

    public record Options(
            Path inputFile,
            Path outputDir,
            boolean deobfuscate,
            boolean includeResources,
            int threads,
            boolean showBadCode
    ) {
        public static Options defaults(Path input, Path output) {
            return new Options(input, output, true, true, 0, true);
        }
    }

    public record OrchestrationResult(
            boolean success,
            Path sourcesDir,
            Path resourcesDir,
            String selectedDecompiler,
            ConfidenceMap.ConfidenceStats confidenceStats,
            String errorMessage,
            long durationMs
    ) {
        public boolean isReliable() {
            return success && confidenceStats.averageScore() > 70;
        }
    }

    public OrchestrationResult orchestrate(Options opts, PipelineListener listener) {
        long startTime = System.currentTimeMillis();

        try {
            listener.onStage("NORMALIZANDO ENTRADA");
            listener.onProgress(2, "Detectando formato: " + opts.inputFile().getFileName());

            InputNormalizer.NormalizedInput normalized = normalizer.normalize(opts.inputFile());
            listener.onLog("Tipo: " + normalized.type().label);
            listener.onLog("DEX files: " + normalized.dexCount());
            listener.onProgress(10, "Entrada normalizada");

            listener.onStage("DESCOMPILANDO");
            listener.onProgress(15, "Iniciando cadena de descompiladores");

            DecompilerStrategy.DecompilerConfig config =
                    new DecompilerStrategy.DecompilerConfig(
                    opts.deobfuscate(),
                    opts.includeResources(),
                    opts.threads(),
                    opts.showBadCode()
            );

            DecompilerChain.ChainResult chainResult = decompilerChain.decompile(
                    opts.inputFile(),
                    opts.outputDir(),
                    config
            );

            if (!chainResult.hasSucceeded()) {
                String errorMsg = chainResult.getStatusSummary();
                listener.onLog("ERROR: " + errorMsg);
                long duration = System.currentTimeMillis() - startTime;
                return new OrchestrationResult(false, null, null, null, null, errorMsg, duration);
            }

            listener.onLog("✓ Descompilación: " + chainResult.selectedDecompiler());
            listener.onProgress(70, "Analizando resultados");

            listener.onStage("CALCULANDO CONFIANZA");
            listener.onProgress(75, "Evaluando calidad");

            scorer.scoreAll(
                    chainResult.selectedDecompiler(),
                    chainResult.sourcesDir(),
                    chainResult.resourcesDir(),
                    "JAVA"
            );

            ConfidenceMap.ConfidenceStats stats = scorer.getSummary();

            listener.onLog("Clases: " + stats.total());
            listener.onLog(String.format("Confianza: %.1f%%", stats.averageScore()));

            listener.onProgress(95, "Finalizando");

            long duration = System.currentTimeMillis() - startTime;

            return new OrchestrationResult(
                    true,
                    chainResult.sourcesDir(),
                    chainResult.resourcesDir(),
                    chainResult.selectedDecompiler(),
                    stats,
                    null,
                    duration
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new OrchestrationResult(
                    false, null, null, null, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                    duration
            );
        }
    }
}
