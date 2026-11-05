package com.aquadecomp.core;

import com.aquadecomp.input.InputNormalizer;
import com.aquadecomp.verification.ConfidenceMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineFailureTest {

    @TempDir
    Path tmp;

    private PipelineResult run(Path input) {
        Pipeline pipeline = new Pipeline();
        Path out = tmp.resolve("salida-" + input.getFileName());
        return pipeline.run(new Pipeline.Options(input, out, true, true, 0),
                PipelineListener.SILENT);
    }

    @Test
    void missingFileFailsCleanly() {
        PipelineResult r = run(tmp.resolve("no-existe.apk"));

        assertFalse(r.success());
        assertTrue(r.errorMessage().contains("no existe"));
        assertNull(r.inputType());
        assertNull(r.confidence());
        assertNull(r.recovery());
        assertNotNull(r.structure());
        assertNotNull(r.manifest());
        assertNotNull(r.stats());
        assertNotNull(r.effectiveness());
    }

    @Test
    void plainTextRenamedToApkFailsCleanly() throws IOException {
        Path text = tmp.resolve("texto.apk");
        Files.writeString(text, "esto no es un APK", StandardCharsets.UTF_8);

        PipelineResult r = run(text);

        assertFalse(r.success());
        assertTrue(r.errorMessage().contains("ZIP"));
        assertNull(r.confidence());
    }

    @Test
    void zipWithoutAndroidContentFailsCleanly() throws IOException {
        Path zip = tmp.resolve("archivo.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("readme.txt"));
            out.write("hola".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        PipelineResult r = run(zip);

        assertFalse(r.success());
        assertTrue(r.errorMessage().contains("soportado"));
    }

    @Test
    void emptyFileFailsCleanly() throws IOException {
        Path empty = tmp.resolve("vacio.apk");
        Files.write(empty, new byte[0]);

        PipelineResult r = run(empty);

        assertFalse(r.success());
        assertTrue(r.errorMessage().contains("ZIP"));
    }

    @Test
    void failedResultStillProducesReadableReports() throws IOException {
        PipelineResult r = run(tmp.resolve("no-existe.apk"));
        Path out = tmp.resolve("reportes-fallo");
        Files.createDirectories(out);

        Path json = ReportWriter.writeJson(r, out);
        Path md = ReportWriter.writeMarkdown(r, out);

        assertTrue(Files.isRegularFile(json));
        assertTrue(Files.isRegularFile(md));

        String jsonText = Files.readString(json);
        assertTrue(jsonText.contains("\"success\": false"));
        assertFalse(jsonText.contains("\"confidence\""));
        assertFalse(jsonText.contains("\"recovery\""));

        String mdText = Files.readString(md);
        assertTrue(mdText.contains(r.errorMessage()));
        assertFalse(mdText.contains("## Recuperación de código"));
        assertFalse(mdText.contains("## Confianza por clase"));
    }

    @Test
    void failureReportCreatesMissingOutputDirectory() throws IOException {
        PipelineResult r = run(tmp.resolve("no-existe.apk"));
        Path out = tmp.resolve("directorio-sin-crear");

        Path json = ReportWriter.writeJson(r, out);
        Path md = ReportWriter.writeMarkdown(r, out);

        assertTrue(Files.isRegularFile(json));
        assertTrue(Files.isRegularFile(md));
        assertTrue(Files.readString(json).contains("\"success\": false"));
    }

    @Test
    void failedResultCarriesVersion() {
        PipelineResult r = run(tmp.resolve("no-existe.apk"));
        assertEquals(Pipeline.VERSION, r.toolVersion());
    }

    @Test
    void inputTypeIsReportedOnValidationSuccessOnly() throws IOException {
        Path apk = tmp.resolve("ok.apk");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(apk))) {
            out.putNextEntry(new ZipEntry("classes.dex"));
            out.write(new byte[]{0x64, 0x65, 0x78, 0x0a});
            out.closeEntry();
        }

        InputNormalizer.NormalizedInput input = new InputNormalizer().normalize(apk);
        assertEquals(InputNormalizer.InputType.APK, input.type());
        assertEquals(1, input.dexEntries());
    }

    @Test
    void failedResultHasUnknownEffectiveness() {
        PipelineResult r = run(tmp.resolve("no-existe.apk"));
        assertEquals(0.0, r.effectiveness().overallScore(), 0.001);
        assertTrue(r.effectiveness().limitations().stream()
                .anyMatch(s -> s.contains("No se pudo calcular")));
    }

    @Test
    void failedResultHasEmptyConfidenceShapeInMarkdownWithoutNpe() throws IOException {
        PipelineResult r = new PipelineResult(
                ApkStructure.empty("x.apk"),
                ManifestInfo.failed("fallo"),
                ObfuscationInfo.unknown(),
                DecompileStats.empty(),
                EffectivenessReport.unknown("fallo"),
                null, null, null,
                Map.of(), tmp.toString(), false, "fallo", Pipeline.VERSION);

        ConfidenceMap.ConfidenceStats stats = r.confidence();
        assertNull(stats);

        Path out = tmp.resolve("reportes-manual");
        Files.createDirectories(out);
        Path md = ReportWriter.writeMarkdown(r, out);
        assertTrue(Files.size(md) > 0);
    }

    @Test
    void recoveryReportIsSerializableWhenPresent() {
        PipelineResult.RecoveryReport rec = new PipelineResult.RecoveryReport(
                10, 2, 5, 4, 3, 1, Map.of("Cadenas descifradas", 7), 1234);
        assertEquals(10, rec.filesProcessed());
        assertEquals(2, rec.filesModified());
        assertEquals(5, rec.javacSampled());
        assertEquals(4, rec.javacCompiled());
        assertEquals(7, rec.items().get("Cadenas descifradas"));
        assertEquals(1234, rec.durationMs());
    }
}
