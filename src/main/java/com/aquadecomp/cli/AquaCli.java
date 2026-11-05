package com.aquadecomp.cli;

import com.aquadecomp.core.Pipeline;
import com.aquadecomp.core.PipelineListener;
import com.aquadecomp.core.PipelineResult;
import com.aquadecomp.core.ReportWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "aquedecomp",
        mixinStandardHelpOptions = true,
        version = "Aquadecomp 1.0.0",
        description = "Descompilador de APKs con análisis de ofuscación y "
                + "medición de efectividad del código recuperado.",
        subcommands = {
                AquaCli.DecompileCommand.class,
                AquaCli.InspectCommand.class
        }
)
public class AquaCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static int execute(String[] args) {
        return new CommandLine(new AquaCli()).execute(args);
    }

    @Command(name = "decompile",
            mixinStandardHelpOptions = true,
            description = "Descompila un APK a código fuente Java + recursos, "
                    + "y genera un reporte de efectividad.")
    static class DecompileCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "APK", description = "Ruta del archivo .apk a descompilar")
        Path apk;

        @Option(names = {"-o", "--out"}, paramLabel = "DIR",
                description = "Directorio de salida (por defecto: ./aquadecomp-out/<nombre-apk>)")
        Path out;

        @Option(names = "--no-deobf", description = "Desactivar la desofuscación de nombres")
        boolean noDeobf;

        @Option(names = "--no-resources", description = "No extraer recursos (solo código Java)")
        boolean noResources;

        @Option(names = "--threads", paramLabel = "N",
                description = "Número de hilos (por defecto: automático)")
        int threads = 0;

        @Option(names = {"-q", "--quiet"}, description = "Solo mostrar el resumen final")
        boolean quiet;

        @Option(names = "--no-md", description = "No generar reporte Markdown")
        boolean noMd;

        @Option(names = "--no-json", description = "No generar reporte JSON")
        boolean noJson;

        @Override
        public Integer call() {
            Path output = out != null ? out
                    : Path.of("aquadecomp-out", stripExtension(apk.getFileName().toString()));

            PipelineListener listener = quiet ? PipelineListener.SILENT : PipelineListener.CONSOLE;
            Pipeline pipeline = new Pipeline();
            Pipeline.Options opts = new Pipeline.Options(apk, output, !noDeobf, !noResources, threads);

            PipelineResult result = pipeline.run(opts, listener);

            try {
                if (!noJson) ReportWriter.writeJson(result, output);
                if (!noMd) ReportWriter.writeMarkdown(result, output);
            } catch (Exception e) {
                System.err.println("Aviso: no se pudo escribir el reporte: " + e.getMessage());
            }

            System.out.println(ReportWriter.consoleSummary(result));

            if (result.success() && !quiet) {
                System.out.println("\nArchivos de reporte en: " + output.toAbsolutePath());
                System.out.println("Código fuente en:       " + output.resolve("sources").toAbsolutePath());
            }
            return result.success() ? 0 : 1;
        }

        private static String stripExtension(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
    }

    @Command(name = "inspect",
            mixinStandardHelpOptions = true,
            description = "Solo inspecciona el APK: estructura, manifiesto, "
                    + "permisos y detectores de packers/ofuscación (sin descompilar).")
    static class InspectCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "APK", description = "Ruta del archivo .apk")
        Path apk;

        @Override
        public Integer call() {
            com.aquadecomp.core.ApkInspector inspector = new com.aquadecomp.core.ApkInspector();
            com.aquadecomp.input.InputNormalizer.NormalizedInput input = null;
            try {
                input = new com.aquadecomp.input.InputNormalizer().normalize(apk);
                Path target = input.analysisTarget();
                var structure = inspector.inspect(target);
                var manifest = inspector.readManifest(target);

                System.out.println("\n=== Aquadecomp inspect ===");
                System.out.println("APK:        " + apk);
                System.out.println("Formato:    " + input.type().label
                        + (input.isContainer() ? " (analizado sobre su APK interno)" : ""));
                System.out.println(String.format("Tamaño:     %.2f MB",
                        structure.sizeBytes() / 1024.0 / 1024.0));
                System.out.println("Entradas:   " + structure.entryCount());
                System.out.println("DEX:        " + structure.dexFiles());
                System.out.println("Libs:       " + structure.nativeLibs().size() + " .so");
                System.out.println("Assets:     " + structure.assetEntries().size());

                System.out.println("\n-- Manifiesto --");
                if (manifest.parsedOk()) {
                    System.out.println("Paquete:    " + manifest.packageName());
                    System.out.println("Versión:    " + manifest.versionName()
                            + " (" + manifest.versionCode() + ")");
                    System.out.println("Principal:  " + manifest.mainActivity());
                    System.out.println("Permisos (" + manifest.permissions().size() + "):");
                    manifest.permissions().forEach(p -> System.out.println("   - " + p));
                } else {
                    System.out.println("Error: " + manifest.parseError());
                }

                System.out.println("\n-- Protección --");
                if (structure.detectedPackers().isEmpty()) {
                    System.out.println("Sin packers conocidos detectados");
                } else {
                    structure.detectedPackers().forEach(p -> System.out.println("   Aviso: " + p));
                }
                if (!structure.warnings().isEmpty()) {
                    System.out.println("-- Avisos --");
                    structure.warnings().forEach(w -> System.out.println("   · " + w));
                }
                System.out.println();
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            } finally {
                if (input != null && input.primaryApk() != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(input.primaryApk());
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
