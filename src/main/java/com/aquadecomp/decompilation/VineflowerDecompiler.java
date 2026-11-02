package com.aquadecomp.decompilation;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class VineflowerDecompiler implements DecompilerStrategy {

    private static final String NAME = "Vineflower";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.jetbrains.java.decompiler.main.decompiler.Decompiler");
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
        Path srcDir = outputDir.resolve("sources_vineflower");
        Path resDir = outputDir.resolve("resources");
        Files.createDirectories(srcDir);

        try {
            Map<String, Object> options = new HashMap<>();
            options.put("rbr", true);
            options.put("rsy", true);
            options.put("din", true);
            options.put("dc", true);
            options.put("da", true);
            options.put("dgs", true);
            options.put("ner", true);
            options.put("den", true);
            options.put("udv", true);
            options.put("eso", true);

            Class<?> decompilerClass = Class.forName(
                    "org.jetbrains.java.decompiler.main.decompiler.Decompiler"
            );

            Object decompiler = decompilerClass.getConstructor(
                    Map.class, File.class, File.class
            ).newInstance(options, apk.toFile(), srcDir.toFile());

            java.lang.reflect.Method decompileMethod = decompilerClass.getMethod("decompileArchive");
            decompileMethod.invoke(decompiler);

            int classesDecompiled = (int) Files.walk(srcDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();

            return DecompilationResult.success(srcDir, resDir, classesDecompiled, 0);

        } catch (Exception e) {
            return DecompilationResult.failure("Vineflower error: " + e.getMessage());
        }
    }
}
