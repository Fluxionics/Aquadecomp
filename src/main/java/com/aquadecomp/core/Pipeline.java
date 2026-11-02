package com.aquadecomp.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Pipeline {

    public static final String VERSION = "0.1.0-beta";

    public record Options(Path apk, Path outputDir, boolean deobfuscate,
                          boolean includeResources, int threads) {
    }

    private final DecompileEngine engine = new DecompileEngine();
    private final ApkInspector inspector = new ApkInspector();
    private final EffectivenessCalculator calculator = EffectivenessCalculator.of();

    public PipelineResult run(Options opts, PipelineListener listener) {
        long start = System.currentTimeMillis();
        Path apk = opts.apk();
        Path out = opts.outputDir();

        try {
            if (!Files.isRegularFile(apk)) {
                return PipelineResult.failed(apk.toString(), out.toString(),
                        "El archivo no existe: " + apk, VERSION);
            }
            Files.createDirectories(out);

            listener.onStage("INSPECCIONANDO APK");
            listener.onProgress(2, "Leyendo estructura ZIP");
            ApkStructure structure = inspector.inspect(apk);
            listener.onLog("Entradas: " + structure.entryCount()
                    + " | DEX: " + structure.dexFiles().size()
                    + " | Libs nativas: " + structure.nativeLibs().size());
            if (!structure.detectedPackers().isEmpty()) {
                listener.onLog("⚠ Packers/protectores detectados: "
                        + String.join(", ", structure.detectedPackers()));
            }
            structure.warnings().forEach(listener::onLog);

            listener.onStage("DECODIFICANDO MANIFIESTO");
            listener.onProgress(6, "AndroidManifest.xml binario");
            ManifestInfo manifest = inspector.readManifest(apk);
            if (manifest.parsedOk()) {
                listener.onLog("Paquete: " + manifest.packageName()
                        + (manifest.versionName() != null ? " v" + manifest.versionName() : ""));
                listener.onLog("Permisos: " + manifest.permissions().size()
                        + " | Actividad principal: "
                        + (manifest.mainActivity() != null ? manifest.mainActivity() : "no detectada"));
            } else {
                listener.onLog("⚠ Manifiesto: " + manifest.parseError());
            }

            DecompileEngine.Options engineOpts = new DecompileEngine.Options(
                    opts.deobfuscate(), opts.includeResources(), opts.threads());
            DecompileEngine.EngineOutput output = engine.decompile(apk, out, engineOpts, listener);

            listener.onStage("CALCULANDO EFECTIVIDAD");
            listener.onProgress(96, "Aplicando metodología");
            EffectivenessReport eff = calculator.calculate(manifest, output.obfuscation(),
                    output.stats(), opts.includeResources());

            long duration = System.currentTimeMillis() - start;
            DecompileStats stats = new DecompileStats(
                    output.stats().classesTotal(), output.stats().classesWithCode(),
                    output.stats().classesNoCode(), output.stats().errors(),
                    output.stats().warns(), output.stats().javaFiles(),
                    output.stats().sourceLines(), output.stats().resourcesDecoded(),
                    output.stats().manifestParsed(), duration);

            listener.onProgress(100, "Listo");

            return new PipelineResult(structure, manifest, output.obfuscation(), stats,
                    eff, out.toAbsolutePath().toString(), true, null, VERSION);
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
        }
    }
}
