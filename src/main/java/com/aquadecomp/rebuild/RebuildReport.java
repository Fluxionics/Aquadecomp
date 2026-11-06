package com.aquadecomp.rebuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public record RebuildReport(
        boolean success,
        String mode,
        String originalApk,
        String outputApk,
        String outputSha256,
        int sourceFiles,
        int classFiles,
        List<String> compileErrors,
        int replacedClasses,
        List<String> dexOriginal,
        int dexNuevo,
        boolean signed,
        List<String> warnings,
        long durationMs,
        String errorMessage,
        String toolVersion
) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static RebuildReport failure(String mode, String originalApk, String outputApk,
                                        int sourceFiles, List<String> compileErrors,
                                        String error, long durationMs, String version,
                                        List<String> warnings) {
        return new RebuildReport(false, mode, originalApk, outputApk, null,
                sourceFiles, 0, compileErrors, 0, List.of(), 0, false,
                warnings != null ? warnings : List.of(), durationMs, error, version);
    }

    public Path writeJson(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("aquedecomp-rebuild.json");
        Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        return file;
    }

    public Path writeMarkdown(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("aquedecomp-rebuild.md");
        Files.writeString(file, markdown(), StandardCharsets.UTF_8);
        return file;
    }

    public String markdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Aquadecomp — Reporte de recompilación\n\n");
        sb.append("- **Fecha:** ").append(java.time.Instant.now()).append('\n');
        sb.append("- **Versión Aquadecomp:** ").append(toolVersion).append('\n');
        sb.append("- **APK original:** ").append(nullSafe(originalApk)).append('\n');
        sb.append("- **Estado:** ").append(success ? "APK generado" : "falló").append('\n');
        if (mode != null) {
            sb.append("- **Modo:** ").append(mode).append('\n');
        }
        sb.append('\n');

        if (!success) {
            sb.append("## Error\n\n").append(nullSafe(errorMessage)).append("\n\n");
        } else {
            sb.append("## APK generado\n\n");
            sb.append("| Campo | Valor |\n|---|---|\n");
            sb.append("| Salida | ").append(nullSafe(outputApk)).append(" |\n");
            sb.append("| SHA-256 | ").append(nullSafe(outputSha256)).append(" |\n");
            sb.append("| Archivos fuente compilados | ").append(sourceFiles).append(" |\n");
            sb.append("| Clases generadas (.class) | ").append(classFiles).append(" |\n");
            sb.append("| Clases reemplazadas en el dex | ").append(replacedClasses).append(" |\n");
            sb.append("| Dex originales | ").append(String.join(", ", dexOriginal)).append(" |\n");
            sb.append("| Dex escritos | ").append(dexNuevo).append(" |\n");
            sb.append("| Firma | ").append(signed ? "v1+v2+v3 (certificado Aquadecomp)" : "no")
                    .append(" |\n");
            sb.append("| Duración | ")
                    .append(String.format(Locale.ROOT, "%.1f", durationMs / 1000.0))
                    .append(" s |\n\n");
        }

        if (compileErrors != null && !compileErrors.isEmpty()) {
            sb.append("## Errores de compilación\n\n```\n");
            for (String error : compileErrors) {
                sb.append(error).append('\n');
            }
            sb.append("```\n\n");
        }

        if (warnings != null && !warnings.isEmpty()) {
            sb.append("## Avisos\n\n");
            for (String warning : warnings) {
                sb.append("- ").append(warning).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Métodos\n\n")
                .append("Los archivos fuente se compilan con javac (JDK real, no una heurística) ")
                .append("usando android.jar y las clases originales del APK como classpath. ")
                .append("El bytecode resultante se convierte a dex con d8. En modo de archivos ")
                .append("editados, el dex original se desarma con baksmali, solo se reemplazan las ")
                .append("clases recompiladas y se vuelve a ensamblar con smali. El APK se reempaqueta ")
                .append("conservando recursos y manifiesto del original, y se firma con un certificado ")
                .append("propio de Aquadecomp.\n");
        return sb.toString();
    }

    public String consoleSummary() {
        StringBuilder sb = new StringBuilder();
        if (success) {
            sb.append(String.format(Locale.ROOT, "%n=== RECOMPILACIÓN OK ===%n"));
            sb.append("APK:        ").append(outputApk).append('\n');
            sb.append("Modo:       ").append(mode).append('\n');
            sb.append("Compilados: ").append(sourceFiles).append(" fuentes -> ")
                    .append(classFiles).append(" clases\n");
            sb.append("Reemplazados en dex: ").append(replacedClasses).append('\n');
            sb.append("Firma:      v1+v2+v3 (certificado propio de Aquadecomp)\n");
            if (outputSha256 != null) {
                sb.append("SHA-256:    ").append(outputSha256).append('\n');
            }
        } else {
            sb.append(String.format(Locale.ROOT, "%nFalló la recompilación: %s%n",
                    nullSafe(errorMessage)));
            if (compileErrors != null && !compileErrors.isEmpty()) {
                sb.append("Primeros errores de javac:\n");
                compileErrors.stream().limit(5).forEach(e -> sb.append("  ").append(e).append('\n'));
            }
        }
        if (warnings != null && !warnings.isEmpty()) {
            sb.append("Avisos:\n");
            warnings.forEach(w -> sb.append("  - ").append(w).append('\n'));
        }
        return sb.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}
