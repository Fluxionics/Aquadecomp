package com.aquadecomp.core;

import com.aquadecomp.input.InputNormalizer;
import com.aquadecomp.recovery.RecoveryPipeline;
import com.aquadecomp.verification.ConfidenceMap;
import com.aquadecomp.verification.ConfidenceScorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class Pipeline {

    public static final String VERSION = "2.0.0";

    public record Options(Path apk, Path outputDir, boolean deobfuscate,
                          boolean includeResources, int threads, Path mapping) {
        public Options(Path apk, Path outputDir, boolean deobfuscate,
                       boolean includeResources, int threads) {
            this(apk, outputDir, deobfuscate, includeResources, threads, null);
        }
    }

    private final DecompileEngine engine = new DecompileEngine();
    private final ApkInspector inspector = new ApkInspector();
    private final EffectivenessCalculator calculator = EffectivenessCalculator.of();

    public PipelineResult run(Options opts, PipelineListener listener) {
        long start = System.currentTimeMillis();
        Path apk = opts.apk();
        Path out = opts.outputDir();
        InputNormalizer.NormalizedInput input = null;

        try {
            if (!Files.isRegularFile(apk)) {
                return PipelineResult.failed(apk.toString(), out.toString(),
                        "El archivo no existe: " + apk, VERSION);
            }

            listener.onStage("NORMALIZANDO ENTRADA");
            listener.onProgress(1, "Validando formato de entrada");
            input = new InputNormalizer().normalize(apk);
            listener.onLog("Formato: " + input.type().label
                    + " (" + input.type().description + ")");
            if (input.isContainer()) {
                listener.onLog("Contenedor: se analiza el APK interno del paquete");
            } else {
                listener.onLog("DEX en el paquete: " + input.dexEntries());
            }

            Files.createDirectories(out);

            listener.onStage("INSPECCIONANDO APK");
            listener.onProgress(2, "Leyendo estructura ZIP");
            ApkStructure structure = inspector.inspect(input.analysisTarget());
            listener.onLog("Entradas: " + structure.entryCount()
                    + " | DEX: " + structure.dexFiles().size()
                    + " | Libs nativas: " + structure.nativeLibs().size());
            if (!structure.detectedPackers().isEmpty()) {
                listener.onLog("Aviso: Packers/protectores detectados: "
                        + String.join(", ", structure.detectedPackers()));
            }
            structure.warnings().forEach(listener::onLog);

            listener.onStage("DECODIFICANDO MANIFIESTO");
            listener.onProgress(6, "AndroidManifest.xml binario");
            ManifestInfo manifest = inspector.readManifest(input.analysisTarget());
            if (manifest.parsedOk()) {
                listener.onLog("Paquete: " + manifest.packageName()
                        + (manifest.versionName() != null ? " v" + manifest.versionName() : ""));
                listener.onLog("Permisos: " + manifest.permissions().size()
                        + " | Actividad principal: "
                        + (manifest.mainActivity() != null ? manifest.mainActivity() : "no detectada"));
            } else {
                listener.onLog("Aviso: Manifiesto: " + manifest.parseError());
            }

            DecompileEngine.Options engineOpts = new DecompileEngine.Options(
                    opts.deobfuscate(), opts.includeResources(), opts.threads(),
                    opts.mapping());
            DecompileEngine.EngineOutput output = engine.decompile(
                    input.file(), input.analysisTarget(), out, engineOpts, listener);

            if (!manifest.parsedOk()) {
                ManifestInfo decoded = DecodedManifestReader.read(
                        DecodedManifestReader.find(out.resolve("resources")));
                if (decoded.parsedOk()) {
                    manifest = decoded;
                    listener.onLog("Manifiesto leído del XML decodificado: paquete "
                            + decoded.packageName());
                }
            }

            listener.onStage("RECUPERANDO CÓDIGO");
            listener.onProgress(97, "Descifrando cadenas y analizando clases");
            RecoveryPipeline recovery = new RecoveryPipeline();
            RecoveryPipeline.RecoveryResult rec = recovery.recover(
                    output.sourcesDir(), listener);
            listener.onLog("Archivos: " + rec.filesProcessed()
                    + " | modificados: " + rec.filesModified());
            if (rec.javacSampled() > 0) {
                listener.onLog("Muestra javac: " + rec.javacCompiled() + "/"
                        + rec.javacSampled() + " sin errores de sintaxis");
            } else {
                listener.onLog("Muestra javac: no disponible (javac no está en el PATH)");
            }
            if (rec.kotlinClasses() > 0) {
                listener.onLog("Clases con metadatos Kotlin: " + rec.kotlinClasses());
            }
            if (rec.innerClasses() > 0) {
                listener.onLog("Clases internas detectadas: " + rec.innerClasses());
            }

            listener.onStage("PUNTUANDO CONFIANZA");
            listener.onProgress(98, "Analizando cada clase");
            Map<String, String> engineByFile = completeEngineMap(
                    output.sourcesDir(), output.engineByFile());
            ConfidenceScorer scorer = new ConfidenceScorer();
            scorer.scoreAll(engineByFile, output.sourcesDir(), rec.fileMetrics(), listener);
            ConfidenceMap.ConfidenceStats confidence = scorer.getSummary();
            listener.onLog("Clases puntuadas: " + confidence.total()
                    + " | media: " + String.format(Locale.ROOT, "%.1f", confidence.averageScore())
                    + " | estructura válida: " + confidence.structurallyValid());
            listener.onLog("Motor por archivo: " + ReportWriter.engineBreakdown(confidence));

            listener.onStage("CALCULANDO EFECTIVIDAD");
            listener.onProgress(99, "Aplicando metodología");
            EffectivenessReport eff = calculator.calculate(manifest, output.obfuscation(),
                    output.stats(), opts.includeResources());

            long duration = System.currentTimeMillis() - start;
            DecompileStats stats = new DecompileStats(
                    output.stats().classesTotal(), output.stats().classesWithCode(),
                    output.stats().classesNoCode(), output.stats().errors(),
                    output.stats().warns(), output.stats().javaFiles(),
                    output.stats().sourceLines(), output.stats().resourcesDecoded(),
                    output.stats().manifestParsed(), duration);

            PipelineResult.RecoveryReport recoveryReport = new PipelineResult.RecoveryReport(
                    rec.filesProcessed(), rec.filesModified(),
                    rec.javacSampled(), rec.javacCompiled(),
                    rec.kotlinClasses(), rec.innerClasses(),
                    rec.items(), rec.durationMs());

            listener.onProgress(100, "Listo");

            return new PipelineResult(structure, manifest, output.obfuscation(), stats,
                    eff, input.type().label, confidence, recoveryReport, engineByFile,
                    out.toAbsolutePath().toString(), true, null, VERSION);
        } catch (IOException e) {
            return PipelineResult.failed(apk.toString(), out.toString(),
                    "Error de E/S: " + e.getMessage(), VERSION);
        } catch (Throwable t) {
            String msg;
            if (t instanceof OutOfMemoryError) {
                msg = "Memoria agotada. El APK es grande: reintenta con más heap, "
                        + "p. ej.: java -Xmx6g -jar aquedecomp.jar decompile <apk> -o <salida>";
            } else {
                msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            return PipelineResult.failed(apk.toString(), out.toString(), msg, VERSION);
        } finally {
            deleteExtracted(input);
        }
    }

    private static void deleteExtracted(InputNormalizer.NormalizedInput input) {
        if (input == null || input.primaryApk() == null) {
            return;
        }
        try {
            Files.deleteIfExists(input.primaryApk());
        } catch (IOException ignored) {
        }
    }

    private static Map<String, String> completeEngineMap(Path sourcesDir,
                                                         Map<String, String> rescued) {
        Map<String, String> all = new LinkedHashMap<>();
        if (Files.isDirectory(sourcesDir)) {
            try (Stream<Path> walk = Files.walk(sourcesDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> {
                            String relative = sourcesDir.relativize(p)
                                    .toString().replace('\\', '/');
                            all.put(relative, "JADX");
                        });
            } catch (IOException ignored) {
            }
        }
        all.putAll(rescued);
        return all;
    }
}
