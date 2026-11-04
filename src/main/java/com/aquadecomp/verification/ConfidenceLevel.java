package com.aquadecomp.verification;

public enum ConfidenceLevel {
    EXCELLENT("Excelente", 100),
    GOOD("Bueno", 85),
    ACCEPTABLE("Aceptable", 70),
    POOR("Pobre", 50),
    UNRELIABLE("No confiable", 25),
    SMALI_FALLBACK("Solo Smali", 10);

    public final String label;
    public final int baseScore;

    ConfidenceLevel(String label, int baseScore) {
        this.label = label;
        this.baseScore = baseScore;
    }

    public static ConfidenceLevel fromScore(int score) {
        if (score >= 90) {
            return EXCELLENT;
        }
        if (score >= 75) {
            return GOOD;
        }
        if (score >= 60) {
            return ACCEPTABLE;
        }
        if (score >= 40) {
            return POOR;
        }
        if (score >= 25) {
            return UNRELIABLE;
        }
        return SMALI_FALLBACK;
    }
}
