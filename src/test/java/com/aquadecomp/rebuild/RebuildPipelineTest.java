package com.aquadecomp.rebuild;

import com.aquadecomp.core.PipelineListener;
import com.android.apksig.ApkVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebuildPipelineTest {

    @TempDir
    Path temp;

    private Path fixtureApk;
    private Path decompileDir;

    private void prepararDescompilacion() throws Exception {
        Path classes = RebuildTestSupport.compileSource(temp.resolve("fixture"), "Hello.java", """
                package com.example;
                public class Hello {
                    public String valor() {
                        return "valor-original";
                    }
                }
                """);
        Path dex = RebuildTestSupport.buildDex(classes, temp.resolve("fixture-work"));
        fixtureApk = RebuildTestSupport.apkWithDex(temp.resolve("fixture.apk"), dex);

        decompileDir = temp.resolve("salida");
        Path sources = decompileDir.resolve("sources").resolve("com").resolve("example");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("Hello.java"), """
                package com.example;
                public class Hello {
                    public String valor() {
                        return "valor-editado";
                    }
                }
                """, StandardCharsets.UTF_8);

        Path report = decompileDir.resolve("aquedecomp-report.json");
        Files.writeString(report, "{\"structure\":{\"apkPath\":\""
                + fixtureApk.toString().replace("\\", "/") + "\"}}", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(report,
                FileTime.fromMillis(System.currentTimeMillis() - 3_600_000L));
    }

    private RebuildPipeline.Options options(Path outApk) {
        return new RebuildPipeline.Options(decompileDir, null, outApk,
                temp.resolve("android-inexistente.jar"), List.of(), false, 21);
    }

    @Test
    void recompilaFuenteEditadoYGeneraApkFirmado() throws Exception {
        prepararDescompilacion();
        Path outApk = temp.resolve("salida").resolve("reconstruido.apk");

        RebuildReport report = new RebuildPipeline().run(options(outApk),
                PipelineListener.SILENT);

        assertTrue(report.success(), report.consoleSummary());
        assertEquals("editados", report.mode());
        assertTrue(Files.isRegularFile(outApk), "no se generó el APK");
        assertEquals(1, report.replacedClasses());
        assertTrue(report.compileErrors().isEmpty());

        ApkVerifier.Result verification = new ApkVerifier.Builder(outApk.toFile())
                .setMinCheckedPlatformVersion(21)
                .build().verify();
        assertTrue(verification.isVerified(), "firma inválida");
        assertTrue(verification.isVerifiedUsingV1Scheme());
        assertTrue(verification.isVerifiedUsingV2Scheme());
        assertTrue(verification.isVerifiedUsingV3Scheme());

        byte[] dex;
        try (ZipFile zip = new ZipFile(outApk.toFile())) {
            dex = zip.getInputStream(zip.getEntry("classes.dex")).readAllBytes();
            assertNotNull(zip.getEntry("AndroidManifest.xml"));
        }
        String dexText = new String(dex, StandardCharsets.ISO_8859_1);
        assertTrue(dexText.contains("valor-editado"), "el cambio no llegó al APK");
        assertFalse(dexText.contains("valor-original"), "la versión vieja sigue presente");

        assertTrue(Files.isRegularFile(decompileDir.resolve("aquedecomp-rebuild.json")));
        assertTrue(Files.isRegularFile(decompileDir.resolve("aquedecomp-rebuild.md")));
    }

    @Test
    void fuenteConErroresNoProduceApk() throws Exception {
        prepararDescompilacion();
        Files.writeString(decompileDir.resolve("sources").resolve("com").resolve("example")
                        .resolve("Hello.java"),
                "package com.example; public class Hello { public int f() { return \"x\"; } }",
                StandardCharsets.UTF_8);
        Path outApk = temp.resolve("nunca.apk");

        RebuildReport report = new RebuildPipeline().run(options(outApk),
                PipelineListener.SILENT);

        assertFalse(report.success());
        assertFalse(report.compileErrors().isEmpty());
        assertFalse(Files.exists(outApk), "no debe existir APK con errores");
        assertNotNull(report.errorMessage());
        assertTrue(Files.isRegularFile(decompileDir.resolve("aquedecomp-rebuild.json")));
    }

    @Test
    void rechazaSalidaDesofuscada() throws Exception {
        prepararDescompilacion();
        Files.writeString(decompileDir.resolve("aquedecomp-renames.jobf"),
                "c com.example.Alpha = com.example.a\n", StandardCharsets.UTF_8);
        Path outApk = temp.resolve("nunca.apk");

        RebuildReport report = new RebuildPipeline().run(options(outApk),
                PipelineListener.SILENT);

        assertFalse(report.success());
        assertNotNull(report.errorMessage());
        assertTrue(report.errorMessage().contains("--no-deobf"),
                "mensaje sin la indicación: " + report.errorMessage());
        assertFalse(Files.exists(outApk));
    }

    @Test
    void directorioSinSourcesFallaLimpio() throws Exception {
        Path vacio = temp.resolve("vacio");
        Files.createDirectories(vacio);
        RebuildPipeline.Options opts = new RebuildPipeline.Options(vacio, null, null,
                temp.resolve("android-inexistente.jar"), List.of(), false, 21);

        RebuildReport report = new RebuildPipeline().run(opts, PipelineListener.SILENT);

        assertFalse(report.success());
        assertNotNull(report.errorMessage());
        assertTrue(report.errorMessage().contains("sources/"));
    }
}
