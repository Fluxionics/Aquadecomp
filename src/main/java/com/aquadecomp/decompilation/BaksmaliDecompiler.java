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
            Runtime.getRuntime().exec(new String[]{"baksmali", "--version"});
            return true;
        } catch (Exception e) {
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
        Path resDir = outputDir.resolve("resources");
        Files.createDirectories(smaliDir);

        try {
            ProcessBuilder pb = new ProcessBuilder("baksmali", "d", "-o", smaliDir.toString(), apk.toString());
            pb.directory(outputDir.toFile());
            Process proc = pb.start();
            int exit = proc.waitFor();

            if (exit != 0) {
                return DecompilationResult.failure("Baksmali exit code: " + exit);
            }

            int classesDecompiled = (int) Files.walk(smaliDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".smali"))
                    .count();

            return DecompilationResult.success(smaliDir, resDir, classesDecompiled, 0);

        } catch (Exception e) {
            return DecompilationResult.failure("Baksmali error: " + e.getMessage());
        }
    }
}
