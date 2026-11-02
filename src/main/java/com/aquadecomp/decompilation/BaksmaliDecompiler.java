package com.aquadecomp.decompilation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BaksmaliDecompiler implements DecompilerStrategy {

    private static final String NAME = "Baksmali";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.jf.baksmali.Baksmali");
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
        Path smaliDir = outputDir.resolve("smali");
        Files.createDirectories(smaliDir);

        try {
            Class<?> baksmaliClass = Class.forName("org.jf.baksmali.Baksmali");
            java.lang.reflect.Method disassembleMethod = baksmaliClass.getMethod(
                    "disassemble", java.io.File.class, java.io.File.class
            );

            disassembleMethod.invoke(null, apk.toFile(), smaliDir.toFile());

            int classesDecompiled = (int) Files.walk(smaliDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".smali"))
                    .count();

            return DecompilationResult.success(smaliDir, outputDir.resolve("resources"),
                    classesDecompiled, 0);

        } catch (Exception e) {
            return DecompilationResult.failure(
                    "Baksmali no disponible (opcional, último recurso): " + e.getMessage()
            );
        }
    }
}
