package com.aquadecomp.decompilation;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JadxDecompilerWrapper implements DecompilerStrategy {

    private static final String NAME = "JADX";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("jadx.api.JadxDecompiler");
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
        Path srcDir = outputDir.resolve("sources");
        Path resDir = outputDir.resolve("resources");

        JadxArgs args = new JadxArgs();
        args.setInputFile(apk.toFile());
        args.setOutDir(outputDir.toFile());
        args.setSkipSources(false);
        args.setSkipResources(!config.includeResources());
        args.setShowInconsistentCode(config.showBadCode());
        args.setUseImports(true);
        args.setReplaceConsts(true);
        args.setDeobfuscationOn(config.deobfuscate());
        args.setDeobfuscationMinLength(3);
        args.setDeobfuscationForceSave(true);

        args.setGeneratedRenamesMappingFile(
                outputDir.resolve("aquedecomp-renames.jobf").toFile()
        );

        if (config.threads() > 0) {
            args.setThreadsCount(config.threads());
        }

        try (JadxDecompiler decompiler = new JadxDecompiler(args)) {
            decompiler.load();
            int totalClasses = decompiler.getClassesWithInners().size();

            decompiler.save();

            for (JavaClass jc : decompiler.getClassesWithInners()) {
                try {
                    jc.unload();
                } catch (Exception ignored) {
                }
            }

            int errors = decompiler.getErrorsCount();
            int classesDecompiled = Math.max(0, totalClasses - errors);

            return DecompilationResult.success(srcDir, resDir, classesDecompiled, errors);
        } catch (Exception e) {
            return DecompilationResult.failure("JADX error: " + e.getMessage());
        }
    }
}
