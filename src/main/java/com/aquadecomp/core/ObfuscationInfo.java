package com.aquadecomp.core;

import java.util.List;

public record ObfuscationInfo(
        int totalClasses,
        int obfuscatedClasses,
        int totalMethods,
        int obfuscatedMethods,
        int totalFields,
        int obfuscatedFields,
        int obfuscatedPackages,
        int substitutedIds,
        double obfuscatedRatio,
        double substitutedRatio,
        String level,
        List<String> signals,
        boolean likelyStringEncryption
) {
    public static ObfuscationInfo unknown() {
        return new ObfuscationInfo(0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0, "UNKNOWN",
                List.of("No se pudo analizar el DEX"), false);
    }

    public double identifierLossRatio() {
        return Math.min(1.0, obfuscatedRatio + substitutedRatio);
    }
}
