package com.aquadecomp.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SyntheticCodeDetector {

    private static final Pattern BRIDGE_METHOD = Pattern.compile(
            "(//\\s*Bridge\\s*method|synthetic\\s+method|\\$\\d+)"
    );

    private static final Pattern LAMBDA_SYNTHETIC = Pattern.compile(
            "\\$\\$Lambda\\$|lambda\\$"
    );

    private static final Pattern GENERATED_DAGGER = Pattern.compile(
            "Dagger[A-Z].*Factory|MembersInjector_Factory|_Factory"
    );

    private static final Pattern GENERATED_RETROFIT = Pattern.compile(
            "ServiceMethod|CallAdapter|ResponseConverter|HttpServiceMethod"
    );

    private static final Pattern PROTECTION_PATTERN = Pattern.compile(
            "new byte\\[\\]\\s*\\{|0x[0-9a-fA-F]+|\\(byte\\)"
    );

    private static final Pattern EMPTY_SYNTHETIC = Pattern.compile(
            "\\{\\s*\\}|throw new Runtime"
    );

    public SyntheticAnalysis analyze(String source, String className) {
        List<String> indicators = new ArrayList<>();
        double confidence = 0.0;
        SyntheticType type = SyntheticType.LEGITIMATE;

        if (source.contains("/* synthetic */") || source.contains("// synthetic")) {
            indicators.add("Marcador synthetic detectado");
            confidence += 0.2;
        }

        if (BRIDGE_METHOD.matcher(source).find()) {
            indicators.add("Bridge method");
            confidence += 0.15;
        }

        if (LAMBDA_SYNTHETIC.matcher(source).find()) {
            indicators.add("Lambda generado");
            confidence += 0.1;
        }

        if (GENERATED_DAGGER.matcher(className).find() ||
            GENERATED_DAGGER.matcher(source).find()) {
            indicators.add("Código generado por Dagger");
            type = SyntheticType.GENERATED;
            confidence += 0.3;
        }

        if (GENERATED_RETROFIT.matcher(className).find() ||
            GENERATED_RETROFIT.matcher(source).find()) {
            indicators.add("Código generado por Retrofit");
            type = SyntheticType.GENERATED;
            confidence += 0.3;
        }

        if (PROTECTION_PATTERN.matcher(source).find()) {
            long byteArrayCount = source.split("new byte\\[\\]").length - 1;
            if (byteArrayCount > 3) {
                indicators.add("Múltiples arrays de bytes (probablemente encriptación)");
                type = SyntheticType.PROTECTED;
                confidence += 0.4;
            }
        }

        if (EMPTY_SYNTHETIC.matcher(source).find()) {
            int emptyBlocks = countOccurrences(source, "{}");
            if (emptyBlocks > 2) {
                indicators.add("Múltiples bloques vacíos");
                type = SyntheticType.PROTECTED;
                confidence += 0.25;
            }
        }

        if (source.contains("UnsupportedOperationException") && 
            source.contains("synthetic")) {
            indicators.add("Método no implementado sintético");
            type = SyntheticType.PROTECTED;
            confidence += 0.35;
        }

        if (countOccurrences(source, "a, b, c") > 0 || 
            hasObfuscatedNames(source)) {
            indicators.add("Nomenclatura ofuscada detectada");
            type = SyntheticType.PROTECTED;
            confidence += 0.2;
        }

        confidence = Math.min(1.0, confidence);

        return new SyntheticAnalysis(
                confidence > 0.5,
                type,
                confidence,
                indicators
        );
    }

    private boolean hasObfuscatedNames(String source) {
        int obfuscatedVarCount = 0;
        for (String word : source.split("[\\s\\(\\)\\[\\];,]")) {
            if (word.matches("^[a-z]$") || word.matches("^[a-z]{2,3}$")) {
                obfuscatedVarCount++;
            }
        }
        return obfuscatedVarCount > 10;
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    public enum SyntheticType {
        LEGITIMATE,
        PROTECTED,
        GENERATED
    }

    public record SyntheticAnalysis(
            boolean isSynthetic,
            SyntheticType type,
            double confidence,
            List<String> indicators
    ) {
        public int scoreAdjustment() {
            return switch (type) {
                case LEGITIMATE -> 0;
                case PROTECTED -> (int) (-30 * confidence);
                case GENERATED -> -5;
            };
        }
    }
}
