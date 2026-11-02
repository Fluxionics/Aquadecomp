package com.aquadecomp.verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ConfidenceScorer {

    private final ConfidenceMap map = new ConfidenceMap();
    private final CompilationVerifier compilationVerifier = new CompilationVerifier();
    private final SyntheticCodeDetector syntheticDetector = new SyntheticCodeDetector();

    public void scoreAll(String decompilerUsed, Path sourcesDir, Path resourcesDir,
                         String sourceFormat) throws IOException {
        if (!Files.isDirectory(sourcesDir)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(sourcesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".smali"))
                    .forEach(file -> {
                        try {
                            scoreFile(file, decompilerUsed, sourcesDir, sourceFormat);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    private void scoreFile(Path file, String decompilerUsed, Path sourcesDir,
                           String sourceFormat) throws IOException {
        String className = extractClassName(file, sourcesDir);
        String content = Files.readString(file);

        int baseScore = sourceFormat.equalsIgnoreCase("SMALI") ? 10 : 75;
        int score = baseScore;
        List<String> issues = new ArrayList<>();

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
        } else if (decompilerUsed.equals("Baksmali")) {
            score = Math.max(score - 15, 0);
        }

        score = Math.max(0, Math.min(100, score));

        ClassConfidence cc = ClassConfidence.builder(className)
                .decompiler(decompilerUsed)
                .score(score)
                .issues(issues)
                .compiles(verification.isStructurallyValid() && score > 40)
                .format(sourceFormat)
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
        for (String error : errors) {
            if (content.toLowerCase().contains(error)) {
                count++;
            }
        }
        return count;
    }

    private String extractClassName(Path file, Path sourcesDir) {
        Path relative = sourcesDir.relativize(file);
        String path = relative.toString().replace("\\", "/");
        String className = path.substring(0, path.lastIndexOf('.'));
        return className.replace("/", ".");
    }

    public ConfidenceMap getMap() {
        return map;
    }

    public ConfidenceMap.ConfidenceStats getSummary() {
        return map.getSummary();
    }
}
