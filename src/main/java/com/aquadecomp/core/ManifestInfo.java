package com.aquadecomp.core;

import java.util.List;

public record ManifestInfo(
        String packageName,
        String versionName,
        String versionCode,
        String applicationClass,
        boolean debuggable,
        boolean allowBackup,
        List<String> permissions,
        List<String> activities,
        String mainActivity,
        boolean parsedOk,
        String parseError
) {
    public static ManifestInfo failed(String error) {
        return new ManifestInfo(null, null, null, null, false, false,
                List.of(), List.of(), null, false, error);
    }
}
