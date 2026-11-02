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
        return switch (score) {
            case 100, 95, 90 -> EXCELLENT;
            case 89, 88, 87, 86, 85, 84, 83, 82, 81, 80 -> GOOD;
            case 79, 78, 77, 76, 75, 74, 73, 72, 71, 70 -> ACCEPTABLE;
            case 69, 68, 67, 66, 65, 64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53, 52, 51, 50 ->
                    POOR;
            case 49, 48, 47, 46, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25 ->
                    UNRELIABLE;
            default -> SMALI_FALLBACK;
        };
    }
}
