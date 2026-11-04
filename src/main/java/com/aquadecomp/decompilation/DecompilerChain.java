package com.aquadecomp.decompilation;

import com.aquadecomp.decompilation.DecompilerStrategy.DecompilerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DecompilerChain {

    private final JadxDecompilerWrapper primary = new JadxDecompilerWrapper();
    private final List<DecompilerStrategy> engines = List.of(
            new CfrDecompiler(),
            new ProcyonDecompiler(),
            new VineflowerDecompiler()
    );

    public ChainResult decompile(Path apk, Path outputDir, DecompilerConfig config) {
        List<AttemptResult> attempts = new ArrayList<>();
        Path sourcesDir = outputDir.resolve("sources");
        Path resourcesDir = outputDir.resolve("resources");

        boolean primaryOk = false;
        int primaryClasses = 0;
        int primaryErrors = -1;
        String primaryMessage = null;
        try {
            DecompilerStrategy.DecompilationResult result = primary.decompile(apk, outputDir, config);
            if (result.success()) {
                primaryClasses = countJavaFiles(sourcesDir);
                primaryErrors = result.errors();
                primaryOk = primaryClasses > 0;
                if (!primaryOk) {
                    primaryMessage = "JADX no genero clases";
                }
            } else {
                primaryMessage = result.message();
            }
        } catch (Throwable t) {
            primaryMessage = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        attempts.add(new AttemptResult(primary.name(), primaryOk, primaryClasses, primaryMessage));

        if (primaryOk && primaryErrors == 0) {
            return new ChainResult(true, sourcesDir, resourcesDir, primary.name(),
                    attempts, primaryClasses, 0);
        }

        int rescued = 0;
        String engineSummary = "";
        Path work = createWorkDir();
        try {
            Path jar = Dex2JarConverter.convert(apk, work);
            Set<String> missing = primaryOk
                    ? missingFromJar(jar, sourcesDir)
                    : allTopLevelClasses(jar);
            if (!missing.isEmpty()) {
                RescueOutcome outcome = rescueWithJar(work, jar, outputDir, missing,
                        config, message -> {
                        }, attempts);
                rescued = outcome.rescued();
                engineSummary = outcome.summary();
            }
        } catch (Throwable t) {
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            attempts.add(new AttemptResult("dex2jar", false, 0, msg));
        } finally {
            deleteTree(work);
        }

        boolean success = primaryOk || rescued > 0;
        String selected = primaryOk
                ? primary.name() + (rescued > 0 ? " + " + engineSummary : "")
                : engineSummary;
        if (!success) {
            selected = null;
        }
        return new ChainResult(success, sourcesDir, resourcesDir, selected,
                attempts, Math.max(primaryClasses, rescued), rescued);
    }

    public ChainResult rescue(Path apk, Path outputDir, Set<String> missing,
                              DecompilerConfig config, Consumer<String> log) {
        List<AttemptResult> attempts = new ArrayList<>();
        Path sourcesDir = outputDir.resolve("sources");
        Path resourcesDir = outputDir.resolve("resources");
        if (missing.isEmpty()) {
            return new ChainResult(true, sourcesDir, resourcesDir, null,
                    attempts, 0, 0);
        }
        Path work = createWorkDir();
        try {
            Path jar = Dex2JarConverter.convert(apk, work);
            attempts.add(new AttemptResult("dex2jar", true, 0, null));
            RescueOutcome outcome = rescueWithJar(work, jar, outputDir, missing,
                    config, log, attempts);
            boolean success = outcome.rescued() > 0;
            return new ChainResult(success, sourcesDir, resourcesDir,
                    success ? outcome.summary() : null, attempts,
                    missing.size(), outcome.rescued());
        } catch (Throwable t) {
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            attempts.add(new AttemptResult("dex2jar", false, 0, msg));
            log.accept("dex2jar no pudo convertir el APK: " + msg);
            return new ChainResult(false, sourcesDir, resourcesDir, null,
                    attempts, missing.size(), 0);
        } finally {
            deleteTree(work);
        }
    }

    private RescueOutcome rescueWithJar(Path work, Path jar, Path outputDir,
                                        Set<String> missing, DecompilerConfig config,
                                        Consumer<String> log, List<AttemptResult> attempts) {
        Path sourcesDir = outputDir.resolve("sources");
        try {
            Files.createDirectories(sourcesDir);
        } catch (IOException e) {
            attempts.add(new AttemptResult("salida", false, 0, e.getMessage()));
            return new RescueOutcome(0, "");
        }

        Set<String> remaining = new LinkedHashSet<>(missing);
        int rescued = 0;
        List<String> parts = new ArrayList<>();

        for (DecompilerStrategy engine : engines) {
            if (remaining.isEmpty()) {
                break;
            }
            if (!engine.isAvailable()) {
                attempts.add(new AttemptResult(engine.name(), false, 0, "no disponible"));
                continue;
            }
            Path staging = work.resolve("rescate_" + engine.name().toLowerCase(Locale.ROOT));
            try {
                Set<String> produced = engine.rescue(jar, staging, remaining, config);
                int merged = merge(produced, staging, sourcesDir);
                if (merged > 0) {
                    rescued += merged;
                    for (String name : produced) {
                        remaining.remove(name);
                    }
                    parts.add(engine.name() + ":" + merged);
                    attempts.add(new AttemptResult(engine.name(), true, merged, null));
                    log.accept("Motor " + engine.name() + ": " + merged + " clases rescatadas");
                } else {
                    attempts.add(new AttemptResult(engine.name(), true, 0, "sin resultados"));
                }
            } catch (Throwable t) {
                String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
                attempts.add(new AttemptResult(engine.name(), false, 0, msg));
                log.accept("Motor " + engine.name() + " fallo: " + msg);
            }
        }

        if (!remaining.isEmpty()) {
            log.accept("Clases sin rescatar: " + remaining.size());
        }
        return new RescueOutcome(rescued, String.join(", ", parts));
    }

    private int merge(Set<String> produced, Path staging, Path sourcesDir) {
        int merged = 0;
        for (String name : produced) {
            String relative = name.replace('.', '/') + ".java";
            Path from = staging.resolve(relative);
            if (!Files.isRegularFile(from)) {
                continue;
            }
            Path to = sourcesDir.resolve(relative);
            if (Files.exists(to)) {
                continue;
            }
            try {
                Files.createDirectories(to.getParent());
                Files.move(from, to);
                merged++;
            } catch (IOException ignored) {
            }
        }
        return merged;
    }

    private Set<String> missingFromJar(Path jar, Path sourcesDir) throws IOException {
        Set<String> missing = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = classPath(entry);
                if (path == null) {
                    continue;
                }
                String outer = outerName(path);
                if (Files.isRegularFile(sourcesDir.resolve(path + ".java"))) {
                    continue;
                }
                if (!outer.equals(path)
                        && Files.isRegularFile(sourcesDir.resolve(outer + ".java"))) {
                    continue;
                }
                missing.add(outer.replace('/', '.'));
            }
        }
        return missing;
    }

    private Set<String> allTopLevelClasses(Path jar) throws IOException {
        Set<String> missing = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = classPath(entry);
                if (path == null) {
                    continue;
                }
                missing.add(outerName(path).replace('/', '.'));
            }
        }
        return missing;
    }

    private String classPath(ZipEntry entry) {
        String name = entry.getName();
        if (entry.isDirectory() || !name.endsWith(".class")) {
            return null;
        }
        return name.substring(0, name.length() - ".class".length());
    }

    private String outerName(String path) {
        int index = path.indexOf('$');
        return index < 0 ? path : path.substring(0, index);
    }

    private Path createWorkDir() {
        try {
            return Files.createTempDirectory("aquedecomp-cadena");
        } catch (IOException e) {
            return Path.of(System.getProperty("java.io.tmpdir"), "aquedecomp-cadena");
        }
    }

    private int countJavaFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private record RescueOutcome(int rescued, String summary) {
    }

    public record AttemptResult(
            String engineName,
            boolean successful,
            int classes,
            String message
    ) {
    }

    public record ChainResult(
            boolean success,
            Path sourcesDir,
            Path resourcesDir,
            String selectedDecompiler,
            List<AttemptResult> attempts,
            int bestClassCount,
            int rescuedCount
    ) {
        public boolean hasSucceeded() {
            return success && sourcesDir != null;
        }

        public String getStatusSummary() {
            if (!success) {
                return "Todas las estrategias fallaron (mejor: " + bestClassCount + " clases)";
            }
            return "Exito con " + selectedDecompiler + " (" + bestClassCount + " clases)";
        }
    }
}
