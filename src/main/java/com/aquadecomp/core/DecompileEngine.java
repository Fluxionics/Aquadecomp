package com.aquadecomp.core;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class DecompileEngine {

    public record Options(boolean deobfuscate, boolean includeResources, int threads) {
        public static Options defaults() {
            return new Options(true, true, 0);
        }
    }

    public record EngineOutput(DecompileStats stats, ObfuscationInfo obfuscation,
                               Path sourcesDir, Path resourcesDir, boolean stringEncryptionSuspected) {
    }

    private static final String ENCRYPT_PATTERNS = """
            javax.crypto.Cipher,android.util.Base64,Base64.decode,Base64.encodeToString,\
            javax.crypto.spec.SecretKeySpec,java.security.MessageDigest,\
            javax.crypto.spec.IvParameterSpec""";

    public EngineOutput decompile(Path apk, Path outDir, Options opts,
                                  PipelineListener listener) throws IOException {
        listener.onStage("PREPARANDO DESCOMPILADOR");
        listener.onProgress(5, "Configurando motor (" + apk.getFileName() + ")");

        Path srcDir = outDir.resolve("sources");
        Path resDir = outDir.resolve("resources");

        JadxArgs args = new JadxArgs();
        args.setInputFile(apk.toFile());
        args.setOutDir(outDir.toFile());
        args.setSkipSources(false);
        args.setSkipResources(!opts.includeResources());
        args.setShowInconsistentCode(true);
        args.setUseImports(true);
        args.setReplaceConsts(true);
        args.setDeobfuscationOn(opts.deobfuscate());
        args.setDeobfuscationMinLength(3);
        args.setDeobfuscationForceSave(true);

        args.setGeneratedRenamesMappingFile(outDir.resolve("aquedecomp-renames.jobf").toFile());
        if (opts.threads() > 0) {
            args.setThreadsCount(opts.threads());
        }

        long start = System.currentTimeMillis();

        try (JadxDecompiler decompiler = new JadxDecompiler(args)) {
            listener.onStage("ANALIZANDO DEX");
            decompiler.load();
            listener.onProgress(20, "DEX cargado: "
                    + decompiler.getClassesWithInners().size() + " clases");

            listener.onStage("ANALIZANDO OFUSCACIÓN");
            ObfuscationInfo obf = ObfuscationDetector.analyze(decompiler.getClasses());
            listener.onLog("Nivel de ofuscación: " + obf.level()
                    + String.format(" (%.1f%% identificadores ofuscados)", obf.obfuscatedRatio() * 100));

            listener.onStage("DESCOMPILANDO A JAVA");
            AtomicLong lastReport = new AtomicLong();
            decompiler.save(250, (current, total) -> {
                long now = System.currentTimeMillis();
                if (now - lastReport.get() >= 400) {
                    lastReport.set(now);
                    int pct = total == 0 ? 30 : 30 + (int) (60.0 * current / total);
                    listener.onProgress(Math.min(pct, 90),
                            "Descompilando " + current + "/" + total + " clases");
                }
            });

            int classesTotal = decompiler.getClassesWithInners().size();
            int classesNoCode = 0;
            for (JavaClass jc : decompiler.getClassesWithInners()) {
                try {
                    if (jc.isNoCode()) classesNoCode++;
                } catch (Exception ignored) {
                    classesNoCode++;
                }
            }
            int classesWithCode = Math.max(0, classesTotal - classesNoCode);
            int errors = decompiler.getErrorsCount();
            int warns = decompiler.getWarnsCount();

            try {
                for (JavaClass jc : decompiler.getClassesWithInners()) {
                    jc.unload();
                }
            } catch (Exception ignored) {
            }
            System.gc();

            listener.onProgress(92, "Contando resultados");
            int javaFiles = safeCount(srcDir, ".java");
            long lines = safeCountLines(srcDir);
            boolean resOk = opts.includeResources()
                    && Files.isDirectory(resDir)
                    && countFiles(resDir, "") > 0;
            boolean manifestOk = Files.isRegularFile(resDir.resolve("AndroidManifest.xml"));

            long duration = System.currentTimeMillis() - start;

            listener.onStage("ESCANEANDO CADENAS");
            boolean stringEnc = scanStringEncryption(srcDir);
            ObfuscationInfo finalObf = ObfuscationDetector.withStringEncryption(obf, stringEnc);
            if (stringEnc) listener.onLog("Posible encriptación de strings detectada");

            DecompileStats stats = new DecompileStats(classesTotal, classesWithCode,
                    classesNoCode, errors, warns, javaFiles, lines,
                    resOk, manifestOk, duration);

            listener.onProgress(97, "Descompilación terminada en "
                    + String.format("%.1f", duration / 1000.0) + "s");

            return new EngineOutput(stats, finalObf, srcDir, resDir, stringEnc);
        }
    }

    static int countFiles(Path root, String suffix) {
        if (!Files.isDirectory(root)) return 0;
        try (Stream<Path> s = Files.walk(root)) {
            return (int) s.filter(Files::isRegularFile)
                    .filter(p -> suffix.isEmpty() || p.toString().endsWith(suffix))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    static int safeCount(Path root, String suffix) {
        try {
            return countFiles(root, suffix);
        } catch (Throwable t) {
            return 0;
        }
    }

    static long safeCountLines(Path root) {
        if (!Files.isDirectory(root)) return 0;
        long total = 0;
        try (Stream<Path> s = Files.walk(root)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            for (Path p : files) {
                total += countNewlines(p);
            }
        } catch (Throwable t) {
            return total;
        }
        return total;
    }

    private static long countNewlines(Path file) {
        long n = 0;
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int r;
            while ((r = in.read(buf)) > 0) {
                for (int i = 0; i < r; i++) {
                    if (buf[i] == '\n') n++;
                }
            }
        } catch (IOException e) {

        }
        return n;
    }

    private boolean scanStringEncryption(Path srcDir) {
        if (!Files.isDirectory(srcDir)) return false;
        String[] patterns = ENCRYPT_PATTERNS.split(",");
        int hits = 0;
        try (Stream<Path> s = Files.walk(srcDir)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .limit(3000)
                    .toList();
            for (Path p : files) {
                try {
                    if (Files.size(p) > 8_000_000) continue;
                } catch (IOException ignored) {
                }
                String content;
                try {
                    content = Files.readString(p);
                } catch (IOException | OutOfMemoryError e) {
                    continue;
                }
                for (String pat : patterns) {
                    if (content.contains(pat)) {
                        hits++;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }

        return hits >= 3;
    }
}
