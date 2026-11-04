package com.aquadecomp.decompilation;

import java.nio.file.Path;
import java.util.Set;

public interface DecompilerStrategy {

    String name();

    boolean isAvailable();

    Set<String> rescue(Path jar, Path sourcesDir, Set<String> missingClasses, DecompilerConfig config)
            throws Exception;

    record DecompilationResult(
            boolean success,
            Path sourcesDir,
            Path resourcesDir,
            int classesDecompiled,
            int errors,
            String message
    ) {
        public static DecompilationResult success(Path src, Path res, int classes, int errs) {
            return new DecompilationResult(true, src, res, classes, errs, null);
        }

        public static DecompilationResult failure(String msg) {
            return new DecompilationResult(false, null, null, 0, -1, msg);
        }
    }

    record DecompilerConfig(
            boolean deobfuscate,
            boolean includeResources,
            int threads,
            boolean showBadCode
    ) {
        public static DecompilerConfig defaults() {
            return new DecompilerConfig(true, true, 0, true);
        }
    }
}
