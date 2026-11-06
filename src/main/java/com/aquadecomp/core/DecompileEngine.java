package com.aquadecomp.core;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;

import com.aquadecomp.decompilation.DecompilerChain;
import com.aquadecomp.decompilation.DecompilerStrategy;
import com.aquadecomp.mapping.MappingApplier;
import com.aquadecomp.mapping.MappingFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class DecompileEngine {

    public record Options(boolean deobfuscate, boolean includeResources, int threads,
                          Path mapping) {
        public Options(boolean deobfuscate, boolean includeResources, int threads) {
            this(deobfuscate, includeResources, threads, null);
        }

        public static Options defaults() {
            return new Options(true, true, 0, null);
        }
    }

    public record EngineOutput(DecompileStats stats, ObfuscationInfo obfuscation,
                               Path sourcesDir, Path resourcesDir, boolean stringEncryptionSuspected,
                               Map<String, String> engineByFile) {
    }

    private static final String ENCRYPT_PATTERNS = """
            javax.crypto.Cipher,android.util.Base64,Base64.decode,Base64.encodeToString,\
            javax.crypto.spec.SecretKeySpec,java.security.MessageDigest,\
            javax.crypto.spec.IvParameterSpec""";

    public EngineOutput decompile(Path apk, Path rescueFrom, Path outDir, Options opts,
                                  PipelineListener listener) throws IOException {
        Path jadxInput = apk;
        Path rescueInput = rescueFrom;
        Path mappingTemp = null;
        if (opts.mapping() != null) {
            listener.onStage("APLICANDO MAPPING");
            MappingFile mapping = MappingFile.parse(opts.mapping());
            listener.onLog("Mapping cargado: " + mapping.classCount() + " clases, "
                    + mapping.memberCount() + " miembros");
            mappingTemp = Files.createTempDirectory("aquadecomp-mapping-");
            MappingApplier.Applied applied = MappingApplier.apply(apk, mapping,
                    mappingTemp.resolve(apk.getFileName().toString()));
            jadxInput = applied.apk();
            if (sameFile(apk, rescueFrom)) {
                rescueInput = jadxInput;
            } else {
                rescueInput = MappingApplier.apply(rescueFrom, mapping,
                        mappingTemp.resolve("rescue-" + rescueFrom.getFileName())).apk();
            }
            if (applied.classes() == 0) {
                listener.onLog("Aviso: el mapping no coincide con ninguna clase del APK");
            } else {
                listener.onLog("Nombres restaurados en el dex: " + applied.classes()
                        + " clases, " + applied.members() + " miembros");
            }
            Files.createDirectories(outDir);
            Files.copy(opts.mapping(), outDir.resolve(MappingFile.COPIED_NAME),
                    StandardCopyOption.REPLACE_EXISTING);
            if (opts.deobfuscate()) {
                listener.onLog("Para poder recompilar después, usa --no-deobf junto "
                        + "con --mapping");
            }
        }
        try {
            return decompileInternal(jadxInput, rescueInput, outDir, opts, listener);
        } finally {
            if (mappingTemp != null) {
                deleteRecursively(mappingTemp);
            }
        }
    }

    private static boolean sameFile(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private EngineOutput decompileInternal(Path apk, Path rescueFrom, Path outDir, Options opts,
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

            Set<String> missing = new LinkedHashSet<>();
            for (JavaClass jc : decompiler.getClassesWithInners()) {
                try {
                    String top = jc.getTopParentClass().getFullName();
                    if (top != null && !top.isEmpty()
                            && !Files.isRegularFile(srcDir.resolve(top.replace('.', '/') + ".java"))) {
                        missing.add(top);
                    }
                } catch (Throwable ignored) {
                }
            }

            try {
                for (JavaClass jc : decompiler.getClassesWithInners()) {
                    jc.unload();
                }
            } catch (Exception ignored) {
            }
            System.gc();

            Map<String, String> engineByFile = new LinkedHashMap<>();
            if (!missing.isEmpty()) {
                listener.onStage("RESCATANDO CLASES");
                listener.onLog("JADX dejo " + missing.size()
                        + " clases sin archivo; probando CFR, Procyon y Vineflower");
                try {
                    DecompilerChain chain = new DecompilerChain();
                    DecompilerChain.ChainResult rescue = chain.rescue(rescueFrom, outDir, missing,
                            new DecompilerStrategy.DecompilerConfig(
                                    opts.deobfuscate(), opts.includeResources(),
                                    opts.threads(), true),
                            listener::onLog);
                    if (rescue.rescuedCount() > 0) {
                        engineByFile.putAll(rescue.engineByFile());
                        listener.onLog("Clases rescatadas: " + rescue.rescuedCount()
                                + "/" + missing.size() + " (" + rescue.selectedDecompiler() + ")");
                    } else {
                        listener.onLog("Sin rescate: " + rescue.getStatusSummary());
                    }
                } catch (Throwable t) {
                    listener.onLog("Rescate no disponible: " + t.getClass().getSimpleName()
                            + ": " + t.getMessage());
                }
            }

            listener.onProgress(92, "Contando resultados");
            int javaFiles = safeCount(srcDir, ".java");
            long lines = safeCountLines(srcDir);
            boolean resOk = opts.includeResources()
                    && Files.isDirectory(resDir)
                    && countFiles(resDir, "") > 0;
            boolean manifestOk = Files.isRegularFile(resDir.resolve("AndroidManifest.xml"));

            long duration = System.currentTimeMillis() - start;

            listener.onStage("ANALIZANDO OFUSCACIÓN");
            ObfuscationDetector.Renames renames = ObfuscationDetector.loadRenames(
                    outDir.resolve("aquedecomp-renames.jobf"));
            ObfuscationInfo obf = ObfuscationDetector.analyze(decompiler.getClassesWithInners(), renames);
            listener.onLog("Nivel de ofuscación: " + obf.level()
                    + String.format(" (%.1f%% ofuscados, %.1f%% renombrados por desofuscación)",
                    obf.obfuscatedRatio() * 100, obf.substitutedRatio() * 100));

            listener.onStage("ESCANEANDO CADENAS");
            boolean stringEnc = scanStringEncryption(srcDir);
            ObfuscationInfo finalObf = ObfuscationDetector.withStringEncryption(obf, stringEnc);
            if (stringEnc) listener.onLog("Posible encriptación de strings detectada");

            DecompileStats stats = new DecompileStats(classesTotal, classesWithCode,
                    classesNoCode, errors, warns, javaFiles, lines,
                    resOk, manifestOk, duration);

            listener.onProgress(97, "Descompilación terminada en "
                    + String.format("%.1f", duration / 1000.0) + "s");

            return new EngineOutput(stats, finalObf, srcDir, resDir, stringEnc, engineByFile);
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
