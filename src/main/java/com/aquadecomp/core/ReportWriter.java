package com.aquadecomp.core;

import com.aquadecomp.verification.ConfidenceMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReportWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path writeJson(PipelineResult result, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Path file = outDir.resolve("aquedecomp-report.json");
        Files.writeString(file, GSON.toJson(result), StandardCharsets.UTF_8);
        return file;
    }

    public static Path writeMarkdown(PipelineResult result, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Path file = outDir.resolve("aquedecomp-report.md");
        Files.writeString(file, markdown(result), StandardCharsets.UTF_8);
        return file;
    }

    private static void appendRecovery(StringBuilder sb, PipelineResult r) {
        PipelineResult.RecoveryReport rec = r.recovery();
        if (rec == null) {
            return;
        }
        sb.append("## Recuperación de código\n\n");
        sb.append("| Métrica | Valor |\n|---|---:|\n");
        sb.append("| Archivos procesados | ").append(rec.filesProcessed()).append(" |\n");
        sb.append("| Archivos modificados | ").append(rec.filesModified()).append(" |\n");
        for (Map.Entry<String, Integer> item : rec.items().entrySet()) {
            sb.append("| ").append(cell(item.getKey())).append(" | ")
                    .append(item.getValue()).append(" |\n");
        }
        if (rec.javacSampled() > 0) {
            sb.append("| Muestra javac sin errores de sintaxis | ")
                    .append(rec.javacCompiled()).append(" / ")
                    .append(rec.javacSampled()).append(" |\n");
        }
        sb.append("| Clases con metadatos Kotlin | ").append(rec.kotlinClasses()).append(" |\n");
        sb.append("| Clases internas detectadas | ").append(rec.innerClasses()).append(" |\n");
        sb.append("| Duración | ").append(String.format(Locale.ROOT, "%.1f s",
                rec.durationMs() / 1000.0)).append(" |\n\n");
        if (rec.filesModified() > 0) {
            sb.append("Los archivos modificados conservan una copia con extensión `.bak`.\n\n");
        }
        if (rec.javacSampled() == 0) {
            sb.append("Muestra javac no disponible: `javac` no está en el PATH.\n\n");
        }
    }

    private static void appendConfidence(StringBuilder sb, PipelineResult r) {
        ConfidenceMap.ConfidenceStats c = r.confidence();
        if (c == null || c.total() == 0) {
            return;
        }
        sb.append("## Confianza por clase\n\n");
        sb.append("| Nivel | Clases |\n|---|---:|\n");
        sb.append("| Excelente | ").append(c.excellent()).append(" |\n");
        sb.append("| Bueno | ").append(c.good()).append(" |\n");
        sb.append("| Aceptable | ").append(c.acceptable()).append(" |\n");
        sb.append("| Pobre | ").append(c.poor()).append(" |\n");
        sb.append("| No confiable | ").append(c.unreliable()).append(" |\n");
        sb.append("| **Total** | ").append(c.total()).append(" |\n\n");
        sb.append(String.format(Locale.ROOT,
                "- Puntaje medio: %.1f/100%n", c.averageScore()));
        sb.append(String.format(Locale.ROOT,
                "- Fiables (excelente + bueno + aceptable): %.1f%%%n", c.getReliablePercentage()));
        sb.append(String.format(Locale.ROOT,
                "- Estructura válida (heurística, no compilación real): %d/%d (%.1f%%)%n",
                c.structurallyValid(), c.total(), c.getStructurallyValidPercentage()));
        sb.append("- Motor que generó cada archivo: ").append(engineBreakdown(c)).append('\n');
        sb.append('\n');
    }

    public static String engineBreakdown(ConfidenceMap.ConfidenceStats c) {
        if (c == null || c.decompilerStats().isEmpty()) {
            return "sin datos";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : c.enginesOrdered()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append(' ').append(e.getValue());
        }
        return sb.toString();
    }

    static String markdown(PipelineResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Aquadecomp — Reporte de descompilación\n\n");
        sb.append("- **Fecha:** ").append(Instant.now()).append('\n');
        sb.append("- **APK:** ").append(r.structure().apkPath()).append('\n');
        sb.append("- **Tamaño:** ").append(String.format(Locale.ROOT, "%.2f MB",
                r.structure().sizeBytes() / 1024.0 / 1024.0)).append('\n');
        sb.append("- **Versión Aquadecomp:** ").append(r.toolVersion()).append('\n');
        if (r.inputType() != null) {
            sb.append("- **Entrada:** ").append(r.inputType()).append('\n');
        }
        sb.append('\n');

        if (!r.success()) {
            sb.append("## Error\n\n").append(r.errorMessage()).append('\n');
            return sb.toString();
        }

        EffectivenessReport e = r.effectiveness();
        sb.append("## Efectividad global: ").append(e.overallScore()).append("/100\n\n");
        sb.append("| Dimensión | Puntaje | Detalle |\n|---|---:|---|\n");
        sb.append("| Cobertura | ").append(e.coverageScore()).append(" | ").append(cell(e.coverageDetail())).append(" |\n");
        sb.append("| Integridad | ").append(e.integrityScore()).append(" | ").append(cell(e.integrityDetail())).append(" |\n");
        sb.append("| Identificadores | ").append(e.identifierScore()).append(" | ").append(cell(e.identifierDetail())).append(" |\n");
        String resScore = e.resourceScore() < 0 ? "N/A" : String.valueOf(e.resourceScore());
        sb.append("| Recursos | ").append(resScore).append(" | ").append(cell(e.resourceDetail())).append(" |\n\n");

        ManifestInfo m = r.manifest();
        sb.append("## Aplicación\n\n");
        if (m.parsedOk()) {
            sb.append("- **Paquete:** ").append(m.packageName()).append('\n');
            if (m.versionName() != null) sb.append("- **Versión:** ").append(m.versionName());
            if (m.versionCode() != null) sb.append(" (").append(m.versionCode()).append(')');
            sb.append('\n');
            if (m.mainActivity() != null) sb.append("- **Actividad principal:** ").append(m.mainActivity()).append('\n');
            sb.append("- **Permisos:** ").append(m.permissions().size()).append('\n');
            if (!m.permissions().isEmpty()) {
                for (String p : m.permissions()) sb.append("  - `").append(p).append("`\n");
            }
            sb.append("- **Debuggable:** ").append(m.debuggable()).append('\n');
        } else {
            sb.append("- Aviso, no se pudo leer: ").append(m.parseError()).append('\n');
        }
        sb.append('\n');

        ObfuscationInfo o = r.obfuscation();
        sb.append("## Ofuscación detectada: ").append(o.level()).append("\n\n");
        for (String s : o.signals()) sb.append("- ").append(s).append('\n');
        if (!r.structure().detectedPackers().isEmpty()) {
            sb.append("\n**Packers/protectores:** ");
            sb.append(String.join(", ", r.structure().detectedPackers())).append('\n');
        }
        sb.append('\n');

        DecompileStats st = r.stats();
        sb.append("## Estadísticas\n\n");
        sb.append("| Métrica | Valor |\n|---|---:|\n");
        sb.append("| Clases totales | ").append(st.classesTotal()).append(" |\n");
        sb.append("| Clases con código | ").append(st.classesWithCode()).append(" |\n");
        sb.append("| Clases sin código | ").append(st.classesNoCode()).append(" |\n");
        sb.append("| Errores / advertencias | ").append(st.errors()).append(" / ").append(st.warns()).append(" |\n");
        sb.append("| Archivos .java | ").append(st.javaFiles()).append(" |\n");
        sb.append("| Líneas de código | ").append(st.sourceLines()).append(" |\n");
        sb.append("| Duración | ").append(String.format(Locale.ROOT, "%.1f s", st.durationMs() / 1000.0)).append(" |\n\n");

        appendRecovery(sb, r);
        appendConfidence(sb, r);

        sb.append("## Fortalezas\n\n");
        for (String s : e.strengths()) sb.append("- ").append(s).append('\n');
        sb.append("\n## Limitaciones\n\n");
        for (String s : e.limitations()) sb.append("- ").append(s).append('\n');

        sb.append("\n## Metodología del puntaje\n\n").append(e.methodology()).append('\n');
        return sb.toString();
    }

    public static String consoleSummary(PipelineResult r) {
        if (!r.success()) {
            return "Falló: " + r.errorMessage();
        }
        EffectivenessReport e = r.effectiveness();
        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("╔══════════════════════════════════════════════════╗\n");
        sb.append(String.format(Locale.ROOT,
                "║  EFECTIVIDAD GLOBAL: %5.1f / 100                 ║%n", e.overallScore()));
        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append(String.format(Locale.ROOT, "║  Cobertura      : %5.1f  %-23s║%n",
                e.coverageScore(), r.stats().classesWithCode() + "/" + r.stats().classesTotal() + " clases"));
        sb.append(String.format(Locale.ROOT, "║  Integridad     : %5.1f  %-23s║%n",
                e.integrityScore(), r.stats().errors() + " errores"));
        sb.append(String.format(Locale.ROOT, "║  Identificadores: %5.1f  %-23s║%n",
                e.identifierScore(), "ofuscación " + r.obfuscation().level()));
        if (e.resourceScore() >= 0) {
            sb.append(String.format(Locale.ROOT, "║  Recursos       : %5.1f  %-23s║%n",
                    e.resourceScore(), r.stats().resourcesDecoded() ? "OK" : "parcial"));
        }
        sb.append("╠══════════════════════════════════════════════════╣\n");
        sb.append(String.format(Locale.ROOT, "║  Salida: %-43s ║%n", shorten(r.outputDir(), 43)));
        sb.append("╚══════════════════════════════════════════════════╝\n");
        if (r.confidence() != null && r.confidence().total() > 0) {
            sb.append(String.format(Locale.ROOT,
                    "Confianza media: %.1f/100 (%d clases)%n",
                    r.confidence().averageScore(), r.confidence().total()));
            sb.append("Motor por archivo: ").append(engineBreakdown(r.confidence())).append('\n');
        }
        if (r.recovery() != null) {
            sb.append(String.format(Locale.ROOT,
                    "Recuperación: %d/%d archivos modificados",
                    r.recovery().filesModified(), r.recovery().filesProcessed()));
            if (r.recovery().javacSampled() > 0) {
                sb.append(String.format(Locale.ROOT,
                        " | javac: %d/%d sin errores de sintaxis",
                        r.recovery().javacCompiled(), r.recovery().javacSampled()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "·");
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
    }

    private ReportWriter() {
    }
}
