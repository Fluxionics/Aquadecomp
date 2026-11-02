package com.aquadecomp.core;

public record DecompileStats(
        int classesTotal,
        int classesWithCode,
        int classesNoCode,
        int errors,
        int warns,
        int javaFiles,
        long sourceLines,
        boolean resourcesDecoded,
        boolean manifestParsed,
        long durationMs
) {
    public static DecompileStats empty() {
        return new DecompileStats(0, 0, 0, 0, 0, 0, 0, false, false, 0);
    }
}
