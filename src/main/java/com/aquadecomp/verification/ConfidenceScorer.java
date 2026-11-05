package com.aquadecomp.verification;

import com.aquadecomp.core.PipelineListener;
import com.aquadecomp.recovery.RecoveryPipeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ConfidenceScorer {

    private static final int PROGRESS_INTERVAL = 500;

    private final ConfidenceMap map = new ConfidenceMap();
    private final CompilationVerifier compilationVerifier = new CompilationVerifier();
    private final SyntheticCodeDetector syntheticDetector = new SyntheticCodeDetector();

    public void scoreAll(String decompilerUsed, Path sourcesDir,
                         Map<String, RecoveryPipeline.RecoveryMetrics> recoveryMetrics,
                         PipelineListener listener) throws IOException {
        if (!Files.isDirectory(sourcesDir)) {
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourcesDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return;
        }

        for (int i = 0; i < files.size(); i++) {
            try {
                scoreFile(files.get(i), decompilerUsed, sourcesDir, recoveryMetrics);
            } catch (IOException ignored) {
            }
            if ((i + 1) % PROGRESS_INTERVAL == 0 || i + 1 == files.size()) {
                listener.onProgress(98, "Puntuando confianza: "
                        + (i + 1) + "/" + files.size() + " archivos");
            }
        }
    }

    private void scoreFile(Path file, String decompilerUsed, Path sourcesDir,
                           Map<String, RecoveryPipeline.RecoveryMetrics> recoveryMetrics)
            throws IOException {
        String className = extractClassName(file, sourcesDir);
        String content = Files.readString(file);

        int score = 75;
        List<String> issues = new ArrayList<>();

        RecoveryPipeline.RecoveryMetrics recovery =
                recoveryMetrics.get(file.toString());

        if (recovery != null) {
            score += recovery.compilationScore() / 10;
            if (recovery.wasRecovered()) {
                issues.add("Recuperación aplicada: " + recovery.recoveredItems());
            }
        }

        SyntheticCodeDetector.SyntheticAnalysis syntheticAnalysis =
                syntheticDetector.analyze(content, className);

        if (syntheticAnalysis.isSynthetic()) {
            score += syntheticAnalysis.scoreAdjustment();
            issues.addAll(syntheticAnalysis.indicators());
        }

        CompilationVerifier.VerificationResult verification =
                compilationVerifier.verify(file);

        score += verification.scoreAdjustment();
        issues.addAll(verification.issues());

        if (content.contains("// JADX WARNING")) {
            score -= 15;
            issues.add("JADX warning presente");
        }

        if (content.contains("throw new UnsupportedOperationException")) {
            score -= 20;
            issues.add("Método no implementado");
        }

        if (content.contains("// TODO") || content.contains("// FIXME")) {
            score -= 5;
            issues.add("TODO/FIXME en código");
        }

        if (content.contains("/* missing */") || content.contains("/* synthetic */")) {
            score -= 10;
            issues.add("Código sintético o faltante");
        }

        int compilationErrors = countCompilationErrors(content);
        if (compilationErrors > 0) {
            score -= Math.min(20, compilationErrors * 5);
            issues.add("Errores de compilación: " + compilationErrors);
        }

        if (decompilerUsed.equals("CFR")) {
            score = Math.max(score - 5, 0);
        } else if (decompilerUsed.equals("Procyon")) {
            score = Math.min(score + 5, 100);
        }

        score = Math.max(0, Math.min(100, score));

        ClassConfidence cc = ClassConfidence.builder(className)
                .decompiler(decompilerUsed)
                .score(score)
                .issues(issues)
                .structurallyValid(verification.isStructurallyValid() && score > 40)
                .build();

        map.record(cc);
    }

    private int countCompilationErrors(String content) {
        int count = 0;
        String[] errors = {
                "cannot find symbol",
                "incompatible types",
                "method not found",
                "class not found",
                "invalid method signature",
                "annotation not applicable",
                "override incompatible"
        };
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        for (String error : errors) {
            if (lower.contains(error)) {
                count++;
            }
        }
        return count;
    }

    private String extractClassName(Path file, Path sourcesDir) {
        Path relative = sourcesDir.relativize(file);
        String path = relative.toString().replace('\\', '/');
        int dot = path.lastIndexOf('.');
        String className = dot > 0 ? path.substring(0, dot) : path;
        return className.replace("/", ".");
    }

    public ConfidenceMap getMap() {
        return map;
    }

    public ConfidenceMap.ConfidenceStats getSummary() {
        return map.getSummary();
    }
}
