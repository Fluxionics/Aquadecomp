package com.aquadecomp.decompilation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

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
        Path resDir = outputDir.resolve("resources");
        Files.createDirectories(srcDir);

        try {
            Class<?> driverClass = Class.forName("org.benf.cfr.reader.api.CfrDriver");
            Object driver = driverClass.getConstructor().newInstance();

            java.lang.reflect.Method decodeMethod = driverClass.getMethod(
                    "analyse", java.util.Collection.class
            );

            java.util.Collection<String> paths = java.util.List.of(apk.toString());
            decodeMethod.invoke(driver, paths);

            int classesDecompiled = (int) Files.walk(srcDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();

            return DecompilationResult.success(srcDir, resDir, classesDecompiled, 0);

        } catch (Exception e) {
            return DecompilationResult.failure("CFR error: " + e.getMessage());
        }
    }
}
