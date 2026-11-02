package com.aquadecomp.decompilation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CfrDecompiler implements DecompilerStrategy {

    private static final String NAME = "CFR";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.benf.cfr.reader.api.CfrDriver");
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
        Path srcDir = outputDir.resolve("sources_cfr");
        Files.createDirectories(srcDir);

        try {
            Class<?> driverClass = Class.forName("org.benf.cfr.reader.api.CfrDriver");
            java.lang.reflect.Constructor<?> constructor = driverClass.getConstructor();
            Object driver = constructor.newInstance();

            java.lang.reflect.Method analyseMethod = driverClass.getMethod(
                    "analyse", java.util.Collection.class
            );

            java.util.Collection<String> paths = java.util.List.of(apk.toAbsolutePath().toString());
            analyseMethod.invoke(driver, paths);

            int classesDecompiled = (int) Files.walk(srcDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();

            if (classesDecompiled == 0) {
                return DecompilationResult.failure("CFR no generó archivos Java");
            }

            return DecompilationResult.success(srcDir, outputDir.resolve("resources"), classesDecompiled, 0);

        } catch (Exception e) {
            return DecompilationResult.failure("CFR no disponible (opcional): " + e.getMessage());
        }
    }
}
