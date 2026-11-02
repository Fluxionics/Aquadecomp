package com.aquadecomp.core;

import java.util.List;

public record EffectivenessReport(
        double overallScore,
        double coverageScore,
        double integrityScore,
        double identifierScore,
        double resourceScore,
        String coverageDetail,
        String integrityDetail,
        String identifierDetail,
        String resourceDetail,
        List<String> strengths,
        List<String> limitations,
        String methodology
) {
    public static EffectivenessReport unknown(String reason) {
        return new EffectivenessReport(0, 0, 0, 0, 0,
                reason, reason, reason, reason,
                List.of(),
                List.of("No se pudo calcular: " + reason),
                "No disponible");
    }
}
