package com.aquadecomp.core;

import java.util.List;

public record ApkStructure(
        String apkPath,
        long sizeBytes,
        int entryCount,
        List<String> dexFiles,
        List<String> nativeLibs,
        List<String> assetEntries,
        List<String> detectedPackers,
        List<String> warnings,
        boolean hasBinaryManifest,
        boolean hasResourcesTable,
        boolean hasMultiDex,
        boolean hasHiddenDex
) {
    public static ApkStructure empty(String apkPath) {
        return new ApkStructure(apkPath, 0, 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), false, false, false, false);
    }
}
