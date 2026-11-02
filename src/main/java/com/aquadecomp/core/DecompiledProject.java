package com.aquadecomp.core;

import com.aquadecomp.verification.ConfidenceMap;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record DecompiledProject(
        String projectName,
        Path sourceDirectory,
        Path resourcesDirectory,
        Path manifestPath,
        String decompilerUsed,
        ConfidenceMap.ConfidenceStats confidenceStats,
        long durationMillis,
        Instant createdAt,
        String aquadecompVersion,
        List<String> decompilerAttempts
) {
    public boolean isHighQuality() {
        return confidenceStats.averageScore() >= 80;
    }

    public boolean isMediumQuality() {
        return confidenceStats.averageScore() >= 60 && confidenceStats.averageScore() < 80;
    }

    public boolean isLowQuality() {
        return confidenceStats.averageScore() < 60;
    }

    public String getQualityLabel() {
        double score = confidenceStats.averageScore();
        if (score >= 90) return "Excelente";
        if (score >= 75) return "Muy bueno";
        if (score >= 60) return "Aceptable";
        if (score >= 40) return "Pobre";
        return "Muy pobre";
    }

    public double getSuccessRatio() {
        int total = confidenceStats.total();
        if (total == 0) return 0;
        int successful = confidenceStats.excellent() + confidenceStats.good()
                + confidenceStats.acceptable();
        return (successful * 100.0) / total;
    }
}
