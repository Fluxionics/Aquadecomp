package com.aquadecomp.decompilation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProcyonDecompiler implements DecompilerStrategy {

    private static final String NAME = "Procyon";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.strobel.decompiler.Decompiler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public DecompilationResult decompile(Path apk, Path outputDir, DecompilerConfig config)
            throws Exception {
        if (!Files.isRegularFile(apk)) {
            return DecompilationResult.failure("APK no encontrado: " + apk);
        }

        Files.createDirectories(outputDir);
        Path srcDir = outputDir.resolve("sources_procyon");
        Files.createDirectories(srcDir);

        try {
            Class<?> decompilerClass = Class.forName("com.strobel.decompiler.Decompiler");
            java.lang.reflect.Method decompileMethod = decompilerClass.getMethod(
                    "decompileArchive", java.io.File.class, java.io.File.class
            );

            decompileMethod.invoke(null, apk.toFile(), srcDir.toFile());

            int classesDecompiled = (int) Files.walk(srcDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();

            if (classesDecompiled == 0) {
                return DecompilationResult.failure("Procyon no generó archivos Java");
            }

            return DecompilationResult.success(srcDir, outputDir.resolve("resources"), 
                    classesDecompiled, 0);

        } catch (Exception e) {
            return DecompilationResult.failure("Procyon no disponible (opcional): " + e.getMessage());
        }
    }
}
