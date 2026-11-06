package com.aquadecomp.rebuild;

import com.aquadecomp.core.PipelineListener;
import com.android.apksig.ApkVerifier;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Test
    void mappingInexistenteFallaConMensaje() throws Exception {
        prepararDescompilacion();
        RebuildPipeline.Options opts = new RebuildPipeline.Options(decompileDir, null,
                temp.resolve("nunca.apk"), temp.resolve("android-inexistente.jar"),
                List.of(), false, 21, temp.resolve("no-existe.txt"));

        RebuildReport report = new RebuildPipeline().run(opts, PipelineListener.SILENT);

        assertFalse(report.success());
        assertNotNull(report.errorMessage());
        assertTrue(report.errorMessage().contains("No existe el mapping"),
                "mensaje: " + report.errorMessage());
    }

    @Test
    void rebuildConMappingVuelveANombresDelApk() throws Exception {
        Path fixtureDir = temp.resolve("fixture-mapping");
        Path classes = RebuildTestSupport.compileSource(fixtureDir, "c.java", """
                package a.b;
                public class c {
                    public static int e = 5;
                    public static int a(int x) {
                        return x + e;
                    }
                    public static int d(int x) {
                        return a(x) + 1;
                    }
                }
                """);
        Path fSrc = fixtureDir.resolve("src").resolve("f.java");
        Files.writeString(fSrc, """
                package a.b;
                public class f {
                    public static int g(int x) {
                        return x * 2;
                    }
                }
                """, StandardCharsets.UTF_8);
        com.aquadecomp.rebuild.SourceCompiler.compile(List.of(fSrc), classes, List.of());
        Path dex = RebuildTestSupport.buildDex(classes, temp.resolve("fixture-mapping-work"));
        Path apk = RebuildTestSupport.apkWithDex(temp.resolve("mapping-fixture.apk"), dex);

        Path dir = temp.resolve("salida-mapping");
        Path sources = dir.resolve("sources").resolve("com").resolve("example");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("Foo.java"), """
                package com.example;

                public class Foo {
                    public static int contador = 7;

                    public static int calcular(int x) {
                        return x + contador;
                    }

                    public static int doble(int x) {
                        return Bar.doble(x);
                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("aquedecomp-mapping.txt"), """
                com.example.Foo -> a.b.c:
                    int contador -> e
                    int calcular(int) -> a
                com.example.Bar -> a.b.f:
                    int doble(int) -> g
                """, StandardCharsets.UTF_8);
        Path reportJson = dir.resolve("aquedecomp-report.json");
        Files.writeString(reportJson, "{\"structure\":{\"apkPath\":\""
                + apk.toString().replace("\\", "/") + "\"}}", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(reportJson,
                FileTime.fromMillis(System.currentTimeMillis() - 3_600_000L));

        Path outApk = dir.resolve("reconstruido.apk");
        RebuildPipeline.Options opts = new RebuildPipeline.Options(dir, null, outApk,
                temp.resolve("android-inexistente.jar"), List.of(), false, 21, null);

        RebuildReport report = new RebuildPipeline().run(opts, PipelineListener.SILENT);

        assertTrue(report.success(), report.consoleSummary());
        assertEquals("editados", report.mode());
        assertEquals(1, report.replacedClasses());
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("mapping")),
                "sin aviso de mapping: " + report.warnings());

        DexFile resultDex = loadOutputDex(outApk);
        Set<String> types = resultDex.getClasses().stream()
                .map(ClassDef::getType).collect(Collectors.toSet());
        assertTrue(types.contains("La/b/c;"), "tipos: " + types);
        assertTrue(types.contains("La/b/f;"), "tipos: " + types);
        assertFalse(types.contains("Lcom/example/Foo;"), "tipos: " + types);
        assertFalse(types.contains("Lcom/example/Bar;"), "tipos: " + types);

        ClassDef c = resultDex.getClasses().stream()
                .filter(cd -> cd.getType().equals("La/b/c;")).findFirst().orElseThrow();
        java.util.stream.Stream<? extends Method> cMethods = java.util.stream.StreamSupport
                .stream(c.getMethods().spliterator(), false);
        Set<String> methodNames = cMethods.map(Method::getName).collect(Collectors.toSet());
        assertTrue(methodNames.contains("a"), "metodos: " + methodNames);
        assertFalse(methodNames.contains("calcular"), "metodos: " + methodNames);
        java.util.stream.Stream<? extends org.jf.dexlib2.iface.Field> cFields =
                java.util.stream.StreamSupport.stream(c.getFields().spliterator(), false);
        assertTrue(cFields.anyMatch(f -> f.getName().equals("e")));
        assertFalse(java.util.stream.StreamSupport
                .stream(c.getFields().spliterator(), false)
                .anyMatch(f -> f.getName().equals("contador")));

        boolean callRenamed = false;
        for (Method method : c.getMethods()) {
            if (method.getImplementation() == null) {
                continue;
            }
            for (var instruction : method.getImplementation().getInstructions()) {
                if (instruction instanceof ReferenceInstruction referenceInstruction
                        && referenceInstruction.getReference() instanceof MethodReference ref
                        && ref.getDefiningClass().equals("La/b/f;")
                        && ref.getName().equals("g")) {
                    callRenamed = true;
                }
            }
        }
        assertTrue(callRenamed, "la llamada a Bar.doble no quedó como La/b/f;->g");
    }

    private static DexFile loadOutputDex(Path apk) throws Exception {
        byte[] dex;
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            dex = zip.getInputStream(zip.getEntry("classes.dex")).readAllBytes();
        }
        Path tmp = apk.resolveSibling("verificacion.dex");
        Files.write(tmp, dex);
        try {
            return DexFileFactory.loadDexFile(tmp.toFile(), Opcodes.getDefault());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
