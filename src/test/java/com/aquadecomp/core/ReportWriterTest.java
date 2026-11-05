package com.aquadecomp.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.aquadecomp.verification.ConfidenceMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    @TempDir
    Path tmp;

    private PipelineResult successResult() {
        ApkStructure structure = new ApkStructure("app.apk", 5 * 1024 * 1024, 10,
                List.of("classes.dex"), List.of(), List.of(), List.of(), List.of(),
                true, true, false, false);
        ManifestInfo manifest = new ManifestInfo("com.example", "1.2.3", "123",
                "com.example.App", false, true,
                List.of("android.permission.INTERNET"),
                List.of("com.example.MainActivity"), "com.example.MainActivity",
                true, null);
        ObfuscationInfo obf = new ObfuscationInfo(100, 30, 1000, 400, 500, 200, 5, 0,
                0.3, 0.0, "MEDIA", List.of("Paquetes ofuscados"), false);
        DecompileStats stats = new DecompileStats(100, 98, 2, 1, 3, 98, 9800,
                true, true, 45230);
        EffectivenessReport eff = EffectivenessCalculator.of()
                .calculate(manifest, obf, stats, true);

        ConfidenceMap map = new ConfidenceMap();
        map.record(com.aquadecomp.verification.ClassConfidence.builder("com.example.A")
                .decompiler("JADX").score(95).structurallyValid(true).build());
        map.record(com.aquadecomp.verification.ClassConfidence.builder("com.example.B")
                .decompiler("JADX").score(80).structurallyValid(true).build());
        ConfidenceMap.ConfidenceStats confidence = map.getSummary();
        PipelineResult.RecoveryReport recovery = new PipelineResult.RecoveryReport(
                98, 4, 40, 36, 50, 12, Map.of("Cadenas descifradas", 9), 209043);

        return new PipelineResult(structure, manifest, obf, stats, eff,
                "APK", confidence, recovery,
                Map.of("com/example/A.java", "JADX", "com/example/B.java", "CFR"),
                tmp.toString(), true, null, "1.0.0");
    }

    @Test
    void markdownContainsAllSectionsOnSuccess() throws IOException {
        PipelineResult r = successResult();
        Path out = tmp.resolve("salida-md");
        Files.createDirectories(out);

        Path md = ReportWriter.writeMarkdown(r, out);
        String text = Files.readString(md);

        assertTrue(text.contains("# Aquadecomp — Reporte de descompilación"));
        assertTrue(text.contains("- **Versión Aquadecomp:** 1.0.0"));
        assertTrue(text.contains("- **Entrada:** APK"));
        assertTrue(text.contains("## Efectividad global:"));
        assertTrue(text.contains("## Aplicación"));
        assertTrue(text.contains("com.example"));
        assertTrue(text.contains("## Ofuscación detectada: MEDIA"));
        assertTrue(text.contains("## Estadísticas"));
        assertTrue(text.contains("## Recuperación de código"));
        assertTrue(text.contains("## Confianza por clase"));
        assertTrue(text.contains("## Fortalezas"));
        assertTrue(text.contains("## Limitaciones"));
        assertTrue(text.contains("## Metodología del puntaje"));
        assertTrue(text.contains("Motor que generó cada archivo"));
        assertTrue(text.contains("android.permission.INTERNET"));
        assertFalse(text.contains("## Error"));
    }

    @Test
    void markdownOmitsRecoveryAndConfidenceWhenAbsent() throws IOException {
        PipelineResult r = new PipelineResult(
                ApkStructure.empty("a.apk"),
                ManifestInfo.failed("fallo"),
                ObfuscationInfo.unknown(),
                DecompileStats.empty(),
                EffectivenessReport.unknown("fallo"),
                null, null, null,
                Map.of(), tmp.toString(), false, "fallo", "1.0.0");
        Path out = tmp.resolve("salida-md2");
        Files.createDirectories(out);

        String text = Files.readString(ReportWriter.writeMarkdown(r, out));

        assertTrue(text.contains("## Error"));
        assertTrue(text.contains("fallo"));
        assertFalse(text.contains("## Recuperación de código"));
        assertFalse(text.contains("## Confianza por clase"));
        assertFalse(text.contains("- **Entrada:**"));
    }

    @Test
    void jsonContainsFlowSectionsOnSuccess() throws IOException {
        PipelineResult r = successResult();
        Path out = tmp.resolve("salida-json");
        Files.createDirectories(out);

        Path json = ReportWriter.writeJson(r, out);
        JsonObject root = new Gson().fromJson(Files.readString(json), JsonObject.class);

        assertTrue(root.has("structure"));
        assertTrue(root.has("manifest"));
        assertTrue(root.has("obfuscation"));
        assertTrue(root.has("stats"));
        assertTrue(root.has("effectiveness"));
        assertTrue(root.has("inputType"));
        assertTrue(root.has("confidence"));
        assertTrue(root.has("recovery"));
        assertTrue(root.has("engineByFile"));
        assertTrue(root.has("outputDir"));
        assertTrue(root.has("success"));
        assertTrue(root.has("toolVersion"));
        assertEquals("APK", root.get("inputType").getAsString());
        assertTrue(root.get("success").getAsBoolean());
        assertEquals("1.0.0", root.get("toolVersion").getAsString());
        assertEquals("CFR", root.getAsJsonObject("engineByFile")
                .get("com/example/B.java").getAsString());
        assertEquals("JADX", root.getAsJsonObject("engineByFile")
                .get("com/example/A.java").getAsString());

        JsonObject recovery = root.getAsJsonObject("recovery");
        assertEquals(98, recovery.get("filesProcessed").getAsInt());
        assertEquals(40, recovery.get("javacSampled").getAsInt());
        assertEquals(36, recovery.get("javacCompiled").getAsInt());

        JsonObject confidence = root.getAsJsonObject("confidence");
        assertEquals(2, confidence.get("total").getAsInt());
        assertEquals(87.5, confidence.get("averageScore").getAsDouble(), 0.001);
    }

    @Test
    void jsonOmitsNullFlowSectionsOnFailure() throws IOException {
        PipelineResult r = new PipelineResult(
                ApkStructure.empty("a.apk"),
                ManifestInfo.failed("fallo"),
                ObfuscationInfo.unknown(),
                DecompileStats.empty(),
                EffectivenessReport.unknown("fallo"),
                null, null, null,
                Map.of(), tmp.toString(), false, "fallo", "1.0.0");
        Path out = tmp.resolve("salida-json2");
        Files.createDirectories(out);

        Path json = ReportWriter.writeJson(r, out);
        JsonObject root = new Gson().fromJson(Files.readString(json), JsonObject.class);

        assertFalse(root.has("inputType"));
        assertFalse(root.has("confidence"));
        assertFalse(root.has("recovery"));
        assertFalse(root.get("success").getAsBoolean());
        assertEquals("fallo", root.get("errorMessage").getAsString());
    }

    @Test
    void consoleSummaryShowsKeyFigures() {
        String summary = ReportWriter.consoleSummary(successResult());
        assertTrue(summary.contains("EFECTIVIDAD GLOBAL"));
        assertTrue(summary.contains("Confianza media"));
        assertTrue(summary.contains("Motor por archivo"));
        assertTrue(summary.contains("Recuperación: 4/98 archivos modificados"));
        assertTrue(summary.contains("javac: 36/40 sin errores de sintaxis"));
    }

    @Test
    void consoleSummaryOnFailureIsPlain() {
        PipelineResult r = new PipelineResult(
                ApkStructure.empty("a.apk"),
                ManifestInfo.failed("fallo"),
                ObfuscationInfo.unknown(),
                DecompileStats.empty(),
                EffectivenessReport.unknown("fallo"),
                null, null, null,
                Map.of(), tmp.toString(), false, "fallo", "1.0.0");
        String summary = ReportWriter.consoleSummary(r);
        assertTrue(summary.startsWith("Falló:"));
        assertTrue(summary.contains("fallo"));
    }

    @Test
    void markdownEscapesPipesInDetails() throws IOException {
        PipelineResult r = successResult();
        Path out = tmp.resolve("salida-pipes");
        Files.createDirectories(out);
        String text = Files.readString(ReportWriter.writeMarkdown(r, out));

        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.startsWith("| Cobertura |") || line.startsWith("| Integridad |")
                    || line.startsWith("| Identificadores |") || line.startsWith("| Recursos |")) {
                long separators = line.chars().filter(c -> c == '|').count();
                assertEquals(4, separators, "Fila de tabla mal formada: " + line);
            }
        }
    }
}
