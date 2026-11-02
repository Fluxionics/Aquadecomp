package com.aquadecomp.core;

public record PipelineResult(
        ApkStructure structure,
        ManifestInfo manifest,
        ObfuscationInfo obfuscation,
        DecompileStats stats,
        EffectivenessReport effectiveness,
        String outputDir,
        boolean success,
        String errorMessage,
        String toolVersion
) {
    public static PipelineResult failed(String apkPath, String outputDir, String error, String version) {
        return new PipelineResult(
                ApkStructure.empty(apkPath),
                ManifestInfo.failed(error),
                ObfuscationInfo.unknown(),
                DecompileStats.empty(),
                EffectivenessReport.unknown(error),
                outputDir, false, error, version);
    }
}
