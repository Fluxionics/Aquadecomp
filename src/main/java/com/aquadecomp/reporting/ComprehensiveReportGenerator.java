package com.aquadecomp.reporting;

import com.aquadecomp.core.DecompiledProject;
import com.aquadecomp.verification.ConfidenceLevel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComprehensiveReportGenerator {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void generateJsonReport(DecompiledProject project, Path outputFile)
            throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();

        report.put("metadata", Map.of(
                "project_name", project.projectName(),
                "created_at", project.createdAt().toString(),
                "aquadecomp_version", project.aquadecompVersion(),
                "duration_ms", project.durationMillis()
        ));

        report.put("decompilation", Map.of(
                "primary_decompiler", project.decompilerUsed(),
                "attempts_made", project.decompilerAttempts(),
                "source_directory", project.sourceDirectory().toString(),
                "resources_directory", project.resourcesDirectory().toString()
        ));

        Map<String, Object> stats = new LinkedHashMap<>();
        var confidence = project.confidenceStats();

        stats.put("total_classes", confidence.total());
        stats.put("average_score", String.format("%.2f", confidence.averageScore()));
        stats.put("quality_label", project.getQualityLabel());
        stats.put("success_ratio", String.format("%.2f%%", project.getSuccessRatio()));

        Map<String, Integer> byLevel = new LinkedHashMap<>();
        byLevel.put("excellent", confidence.excellent());
        byLevel.put("good", confidence.good());
        byLevel.put("acceptable", confidence.acceptable());
        byLevel.put("poor", confidence.poor());
        byLevel.put("unreliable", confidence.unreliable());
        byLevel.put("smali_only", confidence.smaliOnly());
        stats.put("confidence_breakdown", byLevel);

        stats.put("compilable", Map.of(
                "count", confidence.compilable(),
                "percentage", String.format("%.2f%%", confidence.getCompilablePercentage())
        ));

        stats.put("decompiler_usage", confidence.decompilerStats());
        stats.put("format_breakdown", confidence.formatStats());

        report.put("statistics", stats);

        String json = gson.toJson(report);
        Files.writeString(outputFile, json);
    }

    public void generateMarkdownReport(DecompiledProject project, Path outputFile)
            throws IOException {
        StringBuilder md = new StringBuilder();

        md.append("# Aquadecomp - Reporte de Descompilación\n\n");

        md.append("## Información del Proyecto\n");
        md.append("- **Nombre**: ").append(project.projectName()).append("\n");
        md.append("- **Fecha**: ").append(
                project.createdAt().atZone(java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ).append("\n");
        md.append("- **Versión Aquadecomp**: ").append(project.aquadecompVersion()).append("\n");
        md.append("- **Tiempo total**: ")
                .append(String.format("%.2f", project.durationMillis() / 1000.0))
                .append("s\n\n");

        md.append("## Descompilación\n");
        md.append("- **Descompilador principal**: ").append(project.decompilerUsed()).append("\n");
        md.append("- **Intentos realizados**: ").append(project.decompilerAttempts().size())
                .append("\n\n");

        var confidence = project.confidenceStats();

        md.append("## Resultados\n\n");
        md.append("### Calidad General\n");
        md.append("- **Etiqueta de calidad**: ").append(project.getQualityLabel()).append("\n");
        md.append("- **Puntuación promedio**: ")
                .append(String.format("%.2f%%", confidence.averageScore())).append("\n");
        md.append("- **Ratio de éxito**: ")
                .append(String.format("%.2f%%", project.getSuccessRatio())).append("\n\n");

        md.append("### Desglose de Confianza\n");
        md.append("| Nivel | Cantidad | Porcentaje |\n");
        md.append("|-------|----------|------------|\n");

        md.append("| Excelente | ").append(confidence.excellent()).append(" | ")
                .append(String.format("%.2f%%", getPercentage(confidence.excellent(),
                        confidence.total()))).append(" |\n");

        md.append("| Bueno | ").append(confidence.good()).append(" | ")
                .append(String.format("%.2f%%", getPercentage(confidence.good(),
                        confidence.total()))).append(" |\n");

        md.append("| Aceptable | ").append(confidence.acceptable()).append(" | ")
                .append(String.format("%.2f%%", getPercentage(confidence.acceptable(),
                        confidence.total()))).append(" |\n");

        md.append("| Pobre | ").append(confidence.poor()).append(" | ")
                .append(String.format("%.2f%%", getPercentage(confidence.poor(),
                        confidence.total()))).append(" |\n");

        md.append("| No confiable | ").append(confidence.unreliable()).append(" | ")
                .append(String.format("%.2f%%", getPercentage(confidence.unreliable(),
                        confidence.total()))).append(" |\n");

        md.append("| Solo Smali | ").append(confidence.smaliOnly()).append(" | ")
                .append(String.format("%.2f%%", getPercentage(confidence.smaliOnly(),
                        confidence.total()))).append(" |\n\n");

        md.append("### Compilabilidad\n");
        md.append("- **Clases compilables**: ").append(confidence.compilable()).append(" (")
                .append(String.format("%.2f%%", confidence.getCompilablePercentage()))
                .append(")\n\n");

        md.append("### Distribución por Descompilador\n");
        confidence.decompilerStats().forEach((decompiler, count) ->
                md.append("- ").append(decompiler).append(": ").append(count).append("\n")
        );
        md.append("\n");

        md.append("### Formatos de Salida\n");
        confidence.formatStats().forEach((format, count) ->
                md.append("- ").append(format).append(": ").append(count).append("\n")
        );

        Files.writeString(outputFile, md.toString());
    }

    private double getPercentage(int count, int total) {
        return total == 0 ? 0 : (count * 100.0) / total;
    }
}
