package com.aquadecomp.core;

import com.aquadecomp.verification.ConfidenceMap;

import java.util.Map;

public record PipelineResult(
        ApkStructure structure,
        ManifestInfo manifest,
        ObfuscationInfo obfuscation,
        DecompileStats stats,
        EffectivenessReport effectiveness,
        String inputType,
        ConfidenceMap.ConfidenceStats confidence,
        RecoveryReport recovery,
        Map<String, String> engineByFile,
        String outputDir,
        boolean success,
        String errorMessage,
        String toolVersion
) {
    public record RecoveryReport(
            int filesProcessed,
            int filesModified,
            int javacSampled,
            int javacCompiled,
            int kotlinClasses,
            int innerClasses,
            Map<String, Integer> items,
            long durationMs
    ) {
    }

    public static PipelineResult failed(String apkPath, String outputDir, String error, String version) {
        return new PipelineResult(
                ApkStructure.empty(apkPath),
                ManifestInfo.failed(error),
                ObfuscationInfo.unknown(),
                DecompileStats.empty(),
                EffectivenessReport.unknown(error),
                null, null, null,
                Map.of(), outputDir, false, error, version);
    }
}
