package com.aquadecomp.cli;

import com.aquadecomp.core.Pipeline;
import com.aquadecomp.core.PipelineListener;
import com.aquadecomp.core.PipelineResult;
import com.aquadecomp.core.ReportWriter;
import com.aquadecomp.rebuild.RebuildPipeline;
import com.aquadecomp.rebuild.RebuildReport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "aquedecomp",
        version = "Aquadecomp 2.0.2",
        description = "Descompilador de APKs con análisis de ofuscación y "
                + "medición de efectividad del código recuperado.",
        footer = {
                "",
                "Sin argumentos se inicia la interfaz gráfica (equivalente: --gui).",
                "Ejemplo: java -jar aquedecomp.jar decompile app.apk -o ./salida"
        },
        subcommands = {
                AquaCli.DecompileCommand.class,
                AquaCli.InspectCommand.class,
                AquaCli.RebuildCommand.class,
                AquaCli.NativesCommand.class,
                AquaCli.NombresCommand.class,
                AquaCli.ServeCommand.class,
                AquaCli.GuiCommand.class
        }
)
public class AquaCli implements Callable<Integer> {

    @Mixin
    HelpOptions ayuda = new HelpOptions();

    @Option(names = "--gui",
            description = "Inicia la interfaz gráfica (equivale al subcomando gui).")
    boolean gui;

    @Override
    public Integer call() {
        if (gui) {
            return launchGui();
        }
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static int execute(String[] args) {
        return new CommandLine(new AquaCli()).execute(args);
    }

    static int launchGui() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println("Error: no hay entorno gráfico disponible; "
                    + "usa los subcomandos de la CLI.");
            return 1;
        }
        com.aquadecomp.gui.AquaGui.launch(new String[0]);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
        return 0;
    }

    static class HelpOptions {

        @Option(names = {"-h", "--help"}, usageHelp = true,
                description = "Muestra esta ayuda y sale.")
        boolean help;

        @Option(names = {"-V", "--version"}, versionHelp = true,
                description = "Muestra la información de versión y sale.")
        boolean version;
    }

    @Command(name = "decompile",
            description = "Descompila un APK a código fuente Java + recursos, "
                    + "y genera un reporte de efectividad.")
    static class DecompileCommand implements Callable<Integer> {

        @Mixin
        HelpOptions ayuda = new HelpOptions();

        @Parameters(index = "0", paramLabel = "APK", description = "Ruta del archivo .apk a descompilar")
        Path apk;

        @Option(names = {"-o", "--out"}, paramLabel = "DIR",
                description = "Directorio de salida (por defecto: ./aquadecomp-out/<nombre-apk>)")
        Path out;

        @Option(names = "--no-deobf", description = "Desactivar la desofuscación de nombres")
        boolean noDeobf;

        @Option(names = "--mapping", paramLabel = "FILE",
                description = "mapping.txt de ProGuard/R8 para restaurar nombres originales")
        Path mapping;

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
            Pipeline.Options opts = new Pipeline.Options(apk, output, !noDeobf,
                    !noResources, threads, mapping);

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

    @Command(name = "rebuild",
            description = "Recompila los fuentes de una descompilación (javac + d8) y "
                    + "genera un APK firmado con los cambios de código.")
    static class RebuildCommand implements Callable<Integer> {

        @Mixin
        HelpOptions ayuda = new HelpOptions();

        @Parameters(index = "0", paramLabel = "SALIDA",
                description = "Directorio de una descompilación (el que contiene sources/)")
        Path dir;

        @Option(names = {"-o", "--out"}, paramLabel = "APK",
                description = "APK de salida (por defecto: <salida>/<original>-rebuilt.apk)")
        Path out;

        @Option(names = "--original", paramLabel = "APK",
                description = "APK original (por defecto: el registrado en el informe de la descompilación)")
        Path original;

        @Option(names = "--android-jar", paramLabel = "JAR",
                description = "android.jar local (por defecto: caché de Aquadecomp o descarga automática)")
        Path androidJar;

        @Option(names = "--app", description = "Recompilar la aplicación completa (falla si algún archivo no compila)")
        boolean app;

        @Option(names = "--archivos", paramLabel = "A,B", split = ",",
                description = "Archivos explícitos a recompilar (rutas dentro de sources/)")
        List<String> archivos = new ArrayList<>();

        @Option(names = "--min-api", paramLabel = "N",
                description = "API mínima del dex generado (por defecto: 21)")
        int minApi = 21;

        @Option(names = "--mapping", paramLabel = "FILE",
                description = "mapping.txt (por defecto: aquedecomp-mapping.txt de la descompilación)")
        Path mapping;

        @Option(names = {"-q", "--quiet"}, description = "Solo mostrar el resumen final")
        boolean quiet;

        @Override
        public Integer call() {
            PipelineListener listener = quiet ? PipelineListener.SILENT : PipelineListener.CONSOLE;
            RebuildPipeline.Options opts = new RebuildPipeline.Options(
                    dir, original, out, androidJar,
                    archivos != null ? archivos : List.of(), app, minApi, mapping);
            RebuildReport report = new RebuildPipeline().run(opts, listener);
            System.out.println(report.consoleSummary());
            return report.success() ? 0 : 1;
        }
    }

    @Command(name = "natives",
            description = "Analiza las bibliotecas .so del paquete: ELF real "
                    + "(símbolos, secciones, dependencias) y Ghidra headless si está instalado.")
    static class NativesCommand implements Callable<Integer> {

        @Mixin
        HelpOptions ayuda = new HelpOptions();

        @Parameters(index = "0", paramLabel = "APK", description = "Ruta del archivo .apk")
        Path apk;

        @Option(names = "--so", paramLabel = "TEXTO",
                description = "Filtra bibliotecas cuyo nombre contenga el texto")
        String so;

        @Option(names = "--decompile", description = "Solicitar desensamblado con Ghidra "
                + "(si no está instalado, se indica y solo se hace análisis ELF)")
        boolean decompile;

        @Option(names = "--ghidra", paramLabel = "DIR",
                description = "Directorio de instalación de Ghidra (por defecto: "
                        + "GHIDRA_INSTALL_DIR o PATH)")
        Path ghidra;

        @Option(names = "--out", paramLabel = "DIR",
                description = "Directorio para la salida de Ghidra (por defecto: temporal)")
        Path out;

        @Option(names = "--limit", paramLabel = "N",
                description = "Máximo de exports impresos por biblioteca (por defecto: 10)")
        int limit = 10;

        @Override
        public Integer call() {
            try {
                com.aquadecomp.nativecode.NativeAnalyzer.Report report =
                        com.aquadecomp.nativecode.NativeAnalyzer.analyze(
                                apk, so, decompile, ghidra, out);
                System.out.println("\n=== Bibliotecas nativas ===");
                System.out.println("APK:      " + apk);
                System.out.println(" .so:     " + report.libraries());
                if (report.libraries() == 0) {
                    report.warnings().forEach(w -> System.out.println("Aviso: " + w));
                }
                for (com.aquadecomp.nativecode.NativeAnalyzer.Library lib : report.items()) {
                    System.out.println("\n" + lib.entry());
                    if (lib.error() != null) {
                        System.out.println("  Error: " + lib.error());
                        continue;
                    }
                    var elf = lib.elf();
                    System.out.println("  ABI:         " + elf.abi()
                            + " (" + elf.bits() + " bits, "
                            + (elf.littleEndian() ? "little" : "big") + " endian)");
                    System.out.println("  Arquitectura: " + elf.architecture()
                            + "  e_machine=" + elf.machine());
                    System.out.println("  Estado:      " + (elf.stripped()
                            ? "sin symtab (stripped)" : "con symtab"));
                    System.out.println("  Secciones:   " + elf.sections().size());
                    System.out.println("  Símbolos:    " + elf.symbols()
                            + " (exports " + elf.exports().size()
                            + ", imports " + elf.imports() + ")");
                    if (elf.soname() != null) {
                        System.out.println("  SONAME:      " + elf.soname());
                    }
                    if (!elf.needed().isEmpty()) {
                        System.out.println("  Necesita:    " + String.join(", ",
                                elf.needed()));
                    }
                    if (!elf.exports().isEmpty()) {
                        int shown = Math.min(limit, elf.exports().size());
                        System.out.println("  Primeros exports (" + shown + "/"
                                + elf.exports().size() + "):");
                        for (int i = 0; i < shown; i++) {
                            System.out.println("    - " + elf.exports().get(i));
                        }
                    }
                }
                if (!report.warnings().isEmpty() && report.libraries() > 0) {
                    System.out.println("\nAvisos:");
                    report.warnings().forEach(w -> System.out.println("  - " + w));
                }
                var ghidra = report.ghidra();
                System.out.println("\n-- Ghidra --");
                System.out.println(ghidra.message());
                if (ghidra.outputFile() != null) {
                    System.out.println("Salida:  " + ghidra.outputFile().toAbsolutePath());
                    System.out.println("Funciones descompiladas: " + ghidra.functions());
                }
                return decompile && !ghidra.available() ? 1 : 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "serve",
            description = "Levanta la API REST local (JSON) para descompilar, "
                    + "inspeccionar y recompilar por HTTP.")
    static class ServeCommand implements Callable<Integer> {

        @Mixin
        HelpOptions ayuda = new HelpOptions();

        @Option(names = "--host", paramLabel = "HOST",
                description = "Dirección de escucha (por defecto: 127.0.0.1)")
        String host = com.aquadecomp.api.ApiServer.DEFAULT_HOST;

        @Option(names = "--port", paramLabel = "N",
                description = "Puerto (por defecto: 8080; 0 elige uno libre)")
        int port = com.aquadecomp.api.ApiServer.DEFAULT_PORT;

        @Override
        public Integer call() throws Exception {
            com.aquadecomp.api.ApiServer server =
                    new com.aquadecomp.api.ApiServer(host, port);
            server.start();
            System.out.println("Aquadecomp API en http://" + host + ":" + server.port());
            System.out.println("  GET  /            índice de endpoints");
            System.out.println("  GET  /health      estado");
            System.out.println("  POST /api/inspect  {\"apk\": \"ruta\"}");
            System.out.println("  POST /api/decompile {\"apk\": \"ruta\", ...}");
            System.out.println("  POST /api/rebuild  {\"decompileDir\": \"ruta\", ...}");
            if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
                System.out.println("Aviso: el servidor escucha en " + host
                        + " y no tiene autenticación; no lo expongas a redes ajenas.");
            }
            System.out.println("Ctrl+C para detener.");
            Thread.currentThread().join();
            return 0;
        }
    }

    @Command(name = "gui",
            description = "Inicia la interfaz gráfica (también: opción --gui "
                    + "o sin argumentos).")
    static class GuiCommand implements Callable<Integer> {

        @Mixin
        HelpOptions ayuda = new HelpOptions();

        @Override
        public Integer call() {
            return launchGui();
        }
    }

    @Command(name = "nombres",
            description = "Infiere nombres descriptivos para identificadores ofuscados "
                    + "a partir de las fuentes descompiladas (solo sugerencias, no renombra).")
    static class NombresCommand implements Callable<Integer> {

        @Mixin
        HelpOptions ayuda = new HelpOptions();

        @Parameters(index = "0", paramLabel = "DIR",
                description = "Directorio con los fuentes .java descompilados")
        Path dir;

        @Option(names = "--out", paramLabel = "ARCHIVO",
                description = "Ruta para escribir el informe JSON con las sugerencias")
        Path out;

        @Option(names = "--limit", paramLabel = "N",
                description = "Máximo de sugerencias impresas (por defecto: 40)")
        int limit = 40;

        @Override
        public Integer call() {
            try {
                com.aquadecomp.naming.NameReport report =
                        com.aquadecomp.naming.NameInferer.infer(dir);
                System.out.println("\n=== Inferencia de nombres ===");
                System.out.println("Directorio:   " + report.sourceDir());
                System.out.println("Archivos:     " + report.filesScanned());
                System.out.println("Sugerencias:  " + report.suggestions().size());
                int shown = 0;
                for (com.aquadecomp.naming.NameSuggestion s : report.suggestions()) {
                    if (shown >= limit) {
                        break;
                    }
                    System.out.println("  [" + s.kind() + "] " + s.current() + " -> "
                            + s.suggested() + "  (confianza " + s.confidence() + ", "
                            + s.file() + ")");
                    for (String signal : s.signals()) {
                        System.out.println("      señal: " + signal);
                    }
                    shown++;
                }
                if (report.suggestions().size() > shown) {
                    System.out.println("  ... y " + (report.suggestions().size() - shown)
                            + " más (usa --limit o --out)");
                }
                if (report.suggestions().isEmpty()) {
                    System.out.println("No se detectaron identificadores que parezcan "
                            + "ofuscados.");
                }
                for (String note : report.notes()) {
                    System.out.println("Nota: " + note);
                }
                if (out != null) {
                    java.nio.file.Files.writeString(out, report.toJson(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    System.out.println("Informe JSON: " + out.toAbsolutePath());
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "inspect",
            description = "Solo inspecciona el APK: estructura, manifiesto, "
                    + "permisos y detectores de packers/ofuscación (sin descompilar).")
    static class InspectCommand implements Callable<Integer> {

        @Mixin
        HelpOptions ayuda = new HelpOptions();

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
