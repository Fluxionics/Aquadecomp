package com.aquadecomp.rebuild;

import com.aquadecomp.core.ObfuscationDetector;
import com.aquadecomp.core.PipelineListener;
import com.aquadecomp.decompilation.Dex2JarConverter;
import com.aquadecomp.mapping.DexMappingRewriter;
import com.aquadecomp.mapping.MappingApplier;
import com.aquadecomp.mapping.MappingFile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class RebuildPipeline {

    public record Options(Path decompileDir, Path originalApk, Path outputApk,
                          Path androidJar, List<String> files, boolean wholeApp, int minApi,
                          Path mapping) {
        public Options(Path decompileDir, Path originalApk, Path outputApk,
                       Path androidJar, List<String> files, boolean wholeApp, int minApi) {
            this(decompileDir, originalApk, outputApk, androidJar, files, wholeApp,
                    minApi, null);
        }

        public static Options of(Path decompileDir) {
            return new Options(decompileDir, null, null, null, List.of(), false, 21, null);
        }
    }

    private static final String MODE_FULL = "app-completa";
    private static final String MODE_EDITED = "editados";
    private static final String MODE_EXPLICIT = "explicitos";

    public RebuildReport run(Options opts, PipelineListener listener) {
        long start = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();
        List<String> compileErrors = new ArrayList<>();
        String mode = MODE_FULL;
        Path original = null;
        int sourceCount = 0;
        Path work = null;

        try {
            Path sources = opts.decompileDir().resolve("sources");
            if (!Files.isDirectory(sources)) {
                throw new IOException("El directorio no parece una salida de descompilación "
                        + "(falta sources/): " + opts.decompileDir());
            }

            listener.onStage("VALIDANDO DESCOMPILACIÓN");
            listener.onProgress(5, "APK original y renombres");
            original = resolveOriginal(opts);
            rejectRenamedOutput(opts.decompileDir());

            MappingFile mapping = loadMapping(opts);
            if (mapping != null) {
                listener.onLog("Mapping: " + mapping.classCount() + " clases, "
                        + mapping.memberCount() + " miembros");
            }

            mode = decideMode(opts, sources);
            List<Path> units = unitsFor(mode, sources, opts);
            sourceCount = units.size();
            listener.onLog("Modo: " + mode + " | archivos a compilar: " + sourceCount);

            listener.onStage("PREPARANDO CLASSPATH");
            listener.onProgress(15, "android.jar y clases originales");
            work = Files.createTempDirectory("aquedecomp-rebuild-");

            Path android = AndroidJar.bestEffort(opts.androidJar(), listener);
            if (android == null) {
                warnings.add("android.jar no disponible: si el código referencia APIs de Android, "
                        + "javac fallará por tipos que no resuelven");
            }

            Path originalJar = null;
            Path classpathApk = original;
            if (mapping != null) {
                classpathApk = work.resolve("original-restaurado.apk");
                MappingApplier.Applied restored =
                        MappingApplier.apply(original, mapping, classpathApk);
                listener.onLog("Classpath restaurado desde el mapping: "
                        + restored.classes() + " clases, " + restored.members() + " miembros");
            }
            try {
                originalJar = Dex2JarConverter.convert(classpathApk, work.resolve("origen"));
            } catch (IOException e) {
                if (!MODE_FULL.equals(mode)) {
                    throw new IOException("No se pudieron extraer las clases originales del APK "
                            + "(necesarias para resolver referencias): " + e.getMessage(), e);
                }
                warnings.add("No se extrajeron las clases originales (dex2jar): " + e.getMessage());
            }

            listener.onStage("COMPILANDO CON JAVAC");
            listener.onProgress(30, "javac real sobre " + sourceCount + " archivos");
            Path classesDir = work.resolve("classes");
            List<Path> classpath = new ArrayList<>();
            if (android != null) {
                classpath.add(android);
            }
            if (originalJar != null) {
                classpath.add(originalJar);
            }
            SourceCompiler.Result compiled =
                    SourceCompiler.compile(units, classesDir, classpath);
            compileErrors.addAll(compiled.errors());
            if (!compiled.ok()) {
                throw new CompileFailed("javac encontró errores en la salida; no se generó ningún APK");
            }
            listener.onLog("javac: " + sourceCount + " fuentes -> "
                    + compiled.classCount() + " clases");

            Set<String> classNames = classNamesUnder(classesDir);
            MappingFile.RenameTable forward = mapping != null
                    ? mapping.renameTable(MappingFile.Direction.FORWARD) : null;
            if (forward != null) {
                classNames = classNames.stream()
                        .map(name -> obfuscatedName(forward, name))
                        .collect(java.util.stream.Collectors.toSet());
            }

            listener.onStage("GENERANDO DEX (d8)");
            listener.onProgress(65, "convirtiendo " + compiled.classCount() + " clases a dex");
            Path programJar = DexBuilder.jarClasses(classesDir, work.resolve("compiladas.jar"));
            List<Path> dexes = DexBuilder.build(programJar, android, work.resolve("dex"),
                    opts.minApi());
            if (forward != null) {
                listener.onStage("APLICANDO MAPPING AL DEX");
                Path mappedDir = work.resolve("dex-mapeado");
                Files.createDirectories(mappedDir);
                List<Path> mappedDexes = new ArrayList<>();
                int renamed = 0;
                for (Path dex : dexes) {
                    DexMappingRewriter.Result result =
                            DexMappingRewriter.rewrite(Files.readAllBytes(dex), forward);
                    Path target = mappedDir.resolve(dex.getFileName().toString());
                    Files.write(target, result.dex());
                    mappedDexes.add(target);
                    renamed += result.classes();
                }
                dexes = mappedDexes;
                listener.onLog("Dex recompilado renombrado a los nombres del APK original: "
                        + renamed + " clases");
            }

            listener.onStage("EMPACETANDO APK");
            listener.onProgress(85, "reemplazando dex en el APK original");
            List<String> dexOriginal = ApkPacker.dexNames(original);
            List<ApkPacker.Replacement> replacements;
            int replacedCount;

            if (MODE_FULL.equals(mode)) {
                replacements = new ArrayList<>();
                for (Path dex : dexes) {
                    replacements.add(new ApkPacker.Replacement(
                            dex.getFileName().toString(), Files.readAllBytes(dex)));
                }
                replacedCount = classNames.size();
            } else {
                listener.onLog("Fusionando con baksmali/smali: solo se reemplazan las clases "
                        + "recompiladas");
                DexMerge.MergeResult merged = DexMerge.merge(original, dexes, classNames,
                        work, work.resolve("fusionado.dex"));
                replacements = List.of(new ApkPacker.Replacement("classes.dex",
                        Files.readAllBytes(merged.dex())));
                replacedCount = merged.classesReplaced();
                if (dexOriginal.size() > 1) {
                    warnings.add("El APK original tenía " + dexOriginal.size()
                            + " archivos dex; el resultado fusionado queda en un solo classes.dex");
                }
            }

            Path unsigned = work.resolve("sin-firmar.apk");
            ApkPacker.repack(original, unsigned, replacements);

            listener.onStage("FIRMANDO APK");
            listener.onProgress(95, "firma v1+v2+v3");
            Path output = opts.outputApk() != null ? opts.outputApk()
                    : defaultOutput(opts.decompileDir(), original);
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ApkSignerTool.sign(unsigned, output, opts.minApi());

            warnings.add("Los recursos, el manifiesto y los assets se conservan del APK original: "
                    + "este rebuild solo aplica cambios de código");
            warnings.add("El APK se firmó con un certificado propio de Aquadecomp, distinto del "
                    + "original del desarrollador; para instalarlo puede ser necesario "
                    + "desinstalar la app previa o usar adb install -r");
            if (MODE_EDITED.equals(mode)) {
                warnings.add("Se reemplazaron solo " + replacedCount
                        + " clases recompiladas; el resto del bytecode proviene del APK original");
            }
            if (mapping != null) {
                warnings.add("Se aplicó mapping.txt: los nombres originales se restauraron "
                        + "para compilar y el dex recompilado se renombró a los nombres "
                        + "del APK original");
            }

            RebuildReport report = new RebuildReport(true, mode, original.toString(),
                    output.toAbsolutePath().toString(), sha256(output),
                    sourceCount, compiled.classCount(), List.of(),
                    replacedCount, dexOriginal, replacements.size(), true,
                    warnings, elapsed(start), null,
                    com.aquadecomp.core.Pipeline.VERSION);
            writeReports(report, opts.decompileDir(), listener);
            return report;

        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName();
            RebuildReport report = RebuildReport.failure(mode,
                    original != null ? original.toString() : null, null,
                    sourceCount, compileErrors, message, elapsed(start),
                    com.aquadecomp.core.Pipeline.VERSION, warnings);
            writeReports(report, opts.decompileDir(), listener);
            listener.onLog("Recompilación fallida: " + message);
            return report;
        } finally {
            if (work != null) {
                deleteRecursively(work);
            }
        }
    }

    private static Path resolveOriginal(Options opts) throws IOException {
        if (opts.originalApk() != null) {
            if (!Files.isRegularFile(opts.originalApk())) {
                throw new IOException("No existe el APK original indicado: " + opts.originalApk());
            }
            return opts.originalApk().toAbsolutePath().normalize();
        }

        Path reportJson = opts.decompileDir().resolve("aquedecomp-report.json");
        if (Files.isRegularFile(reportJson)) {
            try (Reader reader = Files.newBufferedReader(reportJson)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("structure") && root.getAsJsonObject("structure").has("apkPath")) {
                    String apkPath = root.getAsJsonObject("structure")
                            .get("apkPath").getAsString();
                    Path candidate = Path.of(apkPath);
                    if (Files.isRegularFile(candidate)) {
                        return candidate.toAbsolutePath().normalize();
                    }
                    throw new IOException("El APK original ya no existe en '" + apkPath
                            + "'; indícalo con --original <ruta>");
                }
            }
        }
        throw new IOException("No se encontró el APK original: falta aquedecomp-report.json "
                + "en la salida de descompilación; indícalo con --original <apk>");
    }

    private static void rejectRenamedOutput(Path decompileDir) throws IOException {
        ObfuscationDetector.Renames renames = ObfuscationDetector.loadRenames(
                decompileDir.resolve("aquedecomp-renames.jobf"));
        if (renames.totalIds() > 0) {
            throw new IOException("Esta descompilación renombró " + renames.totalIds()
                    + " identificadores (desofuscación activa): los nombres de los archivos no "
                    + "coinciden con las clases del APK original y el resultado no podría "
                    + "ejecutarse. Descompila de nuevo sin desofuscación: "
                    + "java -jar aquedecomp.jar decompile <apk> -o <salida> --no-deobf");
        }
    }

    private static MappingFile loadMapping(Options opts) throws IOException {
        Path file = opts.mapping();
        if (file == null) {
            Path candidate = opts.decompileDir().resolve(MappingFile.COPIED_NAME);
            if (Files.isRegularFile(candidate)) {
                file = candidate;
            }
        }
        if (file == null) {
            return null;
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("No existe el mapping indicado: " + file);
        }
        return MappingFile.parse(file);
    }

    private static String obfuscatedName(MappingFile.RenameTable forward, String original) {
        String descriptor = "L" + original.replace('.', '/') + ";";
        String renamed = forward.types().get(descriptor);
        if (renamed == null) {
            return original;
        }
        return renamed.substring(1, renamed.length() - 1).replace('/', '.');
    }

    private static String decideMode(Options opts, Path sources) throws IOException {
        if (opts.wholeApp()) {
            return MODE_FULL;
        }
        if (opts.files() != null && !opts.files().isEmpty()) {
            return MODE_EXPLICIT;
        }
        long cutoff = decompileCutoff(opts.decompileDir());
        if (cutoff > 0) {
            try (Stream<Path> walk = Files.walk(sources)) {
                boolean edited = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .anyMatch(p -> lastModified(p) > cutoff);
                if (edited) {
                    return MODE_EDITED;
                }
            }
        }
        return MODE_FULL;
    }

    private static List<Path> unitsFor(String mode, Path sources, Options opts)
            throws IOException {
        if (MODE_EXPLICIT.equals(mode)) {
            List<Path> units = new ArrayList<>();
            for (String file : opts.files()) {
                Path path = Path.of(file);
                if (!path.isAbsolute()) {
                    path = sources.resolve(file);
                }
                if (!Files.isRegularFile(path)) {
                    throw new IOException("No existe el archivo indicado: " + path);
                }
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(sources.toAbsolutePath().normalize())) {
                    throw new IOException("El archivo debe estar dentro de sources/: " + path);
                }
                units.add(normalized);
            }
            if (units.isEmpty()) {
                throw new IOException("--archivos no contiene ningún archivo válido");
            }
            return units;
        }

        List<Path> all = SourceCompiler.javaFilesUnder(sources);
        if (all.isEmpty()) {
            throw new IOException("No hay archivos .java en " + sources);
        }
        if (MODE_EDITED.equals(mode)) {
            long cutoff = decompileCutoff(opts.decompileDir());
            List<Path> edited = all.stream()
                    .filter(p -> lastModified(p) > cutoff)
                    .toList();
            if (edited.isEmpty()) {
                throw new IOException("No se detectaron archivos modificados desde la "
                        + "descompilación; usa --app para recompilar la aplicación completa o "
                        + "--archivos para indicar cuáles");
            }
            return edited;
        }
        return all;
    }

    private static long decompileCutoff(Path decompileDir) {
        Path reportJson = decompileDir.resolve("aquedecomp-report.json");
        if (Files.isRegularFile(reportJson)) {
            return lastModified(reportJson);
        }
        Path jobf = decompileDir.resolve("aquedecomp-renames.jobf");
        if (Files.isRegularFile(jobf)) {
            return lastModified(jobf);
        }
        return 0;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static Set<String> classNamesUnder(Path classesDir) throws IOException {
        if (!Files.isDirectory(classesDir)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(classesDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .map(p -> classesDir.relativize(p).toString().replace('\\', '/'))
                    .map(s -> s.substring(0, s.length() - ".class".length()).replace('/', '.'))
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static Path defaultOutput(Path decompileDir, Path original) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return decompileDir.resolve(base + "-rebuilt.apk");
    }

    private static void writeReports(RebuildReport report, Path decompileDir,
                                     PipelineListener listener) {
        if (!Files.isDirectory(decompileDir)) {
            return;
        }
        try {
            report.writeJson(decompileDir);
            report.writeMarkdown(decompileDir);
        } catch (IOException e) {
            listener.onLog("Aviso: no se pudo escribir el informe de recompilación: "
                    + e.getMessage());
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
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

    private static final class CompileFailed extends RuntimeException {
        private CompileFailed(String message) {
            super(message);
        }
    }
}
