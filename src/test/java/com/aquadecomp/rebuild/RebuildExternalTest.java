package com.aquadecomp.rebuild;

import com.aquadecomp.core.PipelineListener;
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.google.gson.Gson;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebuildExternalTest {

    @TempDir
    Path temp;

    private static final String HELLO_EDITADO = """
            package com.example;
            public class Hello {
                public String valor() {
                    return "valor-editado";
                }
            }
            """;

    private Path compilarJava(Path workDir, String archivo, String fuente,
                              List<Path> classpath) throws IOException {
        Path srcDir = workDir.resolve("src");
        Files.createDirectories(srcDir);
        Path src = srcDir.resolve(archivo);
        Files.writeString(src, fuente, StandardCharsets.UTF_8);
        Path classes = workDir.resolve("classes");
        SourceCompiler.Result result = SourceCompiler.compile(List.of(src), classes, classpath);
        if (!result.ok()) {
            throw new IllegalStateException("Fixture no compila: " + String.join("\n",
                    result.errors()));
        }
        return classes;
    }

    private Path apkConEntradas(Path apk, Map<String, byte[]> entradas) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apk))) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        }
        return apk;
    }

    private void prepararDescompilacion(Path dir, Path apk, Map<String, String> fuentes)
            throws Exception {
        for (Map.Entry<String, String> fuente : fuentes.entrySet()) {
            Path destino = dir.resolve("sources").resolve(fuente.getKey());
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, fuente.getValue(), StandardCharsets.UTF_8);
        }
        Path reporte = dir.resolve("aquedecomp-report.json");
        Files.writeString(reporte, "{\"structure\":{\"apkPath\":\""
                + apk.toString().replace("\\", "/") + "\"}}", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(reporte,
                FileTime.fromMillis(System.currentTimeMillis() - 3_600_000L));
    }

    private RebuildReport reconstruir(Path dir, Path salida) {
        RebuildPipeline.Options opts = new RebuildPipeline.Options(dir, null, salida,
                temp.resolve("android-inexistente.jar"), List.of(), false, 21);
        return new RebuildPipeline().run(opts, PipelineListener.SILENT);
    }

    private byte[] entradaDe(Path apk, String nombre) throws IOException {
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            ZipEntry entry = zip.getEntry(nombre);
            assertNotNull(entry, "falta " + nombre + " en " + apk);
            return zip.getInputStream(entry).readAllBytes();
        }
    }

    private Set<String> tiposDe(byte[] dexBytes, String temporal) throws Exception {
        Path tmp = temp.resolve(temporal);
        Files.write(tmp, dexBytes);
        try {
            DexFile dex = DexFileFactory.loadDexFile(tmp.toFile(), Opcodes.getDefault());
            return dex.getClasses().stream().map(ClassDef::getType).collect(Collectors.toSet());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String texto(byte[] dexBytes) {
        return new String(dexBytes, StandardCharsets.ISO_8859_1);
    }

    private ApkVerifier.Result verificar(Path apk) throws Exception {
        return new ApkVerifier.Builder(apk.toFile())
                .setMinCheckedPlatformVersion(21)
                .build().verify();
    }

    private static void assertFirmaCompleta(ApkVerifier.Result resultado) {
        assertTrue(resultado.isVerified(), "firma inválida");
        assertTrue(resultado.isVerifiedUsingV1Scheme(), "falta firma v1");
        assertTrue(resultado.isVerifiedUsingV2Scheme(), "falta firma v2");
        assertTrue(resultado.isVerifiedUsingV3Scheme(), "falta firma v3");
    }

    private static Path codeSource(Class<?> tipo) throws Exception {
        URI uri = tipo.getProtectionDomain().getCodeSource().getLocation().toURI();
        return Path.of(uri);
    }

    private static Path extraerClases(Path jar, String prefijo, Path destino) throws IOException {
        Files.createDirectories(destino);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefijo)
                        || !entry.getName().endsWith(".class")) {
                    continue;
                }
                Path objetivo = destino.resolve(entry.getName());
                Files.createDirectories(objetivo.getParent());
                Files.write(objetivo, zip.getInputStream(entry).readAllBytes());
            }
        }
        return destino;
    }

    private static void copiaArbol(Path origen, Path destino) throws IOException {
        try (var walk = Files.walk(origen)) {
            for (Path p : walk.toList()) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                Path objetivo = destino.resolve(origen.relativize(p).toString());
                Files.createDirectories(objetivo.getParent());
                Files.copy(p, objetivo, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path javaEjecutable() {
        Path exe = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (Files.isRegularFile(exe)) {
            return exe;
        }
        return Path.of(System.getProperty("java.home"), "bin", "java");
    }

    private X509Certificate firmarComoDesarrollador(Path entrada, Path salida) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Desarrollador de prueba, O=Empresa Ajena");
        Date notBefore = new Date(System.currentTimeMillis() - 86_400_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 3_153_600_000_000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter,
                subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());
        X509Certificate certificado =
                new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder(
                "desarrollador", keyPair.getPrivate(), List.of(certificado)).build();
        new ApkSigner.Builder(List.of(config))
                .setInputApk(entrada.toFile())
                .setOutputApk(salida.toFile())
                .setMinSdkVersion(21)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build().sign();
        return certificado;
    }

    @Test
    void apkMultidexReemplazaLaClaseEnSuDexYConservaLosDemas() throws Exception {
        Path hello = compilarJava(temp.resolve("hello"), "Hello.java", """
                package com.example;
                public class Hello {
                    public String valor() {
                        return "valor-original";
                    }
                }
                """, List.of());
        Path mundo = compilarJava(temp.resolve("mundo"), "Mundo.java", """
                package com.example;
                public class Mundo {
                    public String valor() {
                        return "mundo-original";
                    }
                }
                """, List.of());
        Path dex1 = RebuildTestSupport.buildDex(hello, temp.resolve("dex1"));
        Path dex2 = RebuildTestSupport.buildDex(mundo, temp.resolve("dex2"));

        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("classes.dex", Files.readAllBytes(dex1));
        entradas.put("classes2.dex", Files.readAllBytes(dex2));
        entradas.put("AndroidManifest.xml",
                "<manifest package=\"com.example\"/>".getBytes(StandardCharsets.UTF_8));
        Path apk = apkConEntradas(temp.resolve("multidex.apk"), entradas);

        Path dir = temp.resolve("salida-multidex");
        prepararDescompilacion(dir, apk, Map.of("com/example/Hello.java", HELLO_EDITADO));

        Path salida = dir.resolve("reconstruido.apk");
        RebuildReport report = reconstruir(dir, salida);

        assertTrue(report.success(), report.consoleSummary());
        assertEquals("editados", report.mode());
        assertEquals(2, report.dexOriginal().size());
        assertEquals(List.of("classes.dex", "classes2.dex"), ApkPacker.dexNames(salida));
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("archivos dex")),
                "sin aviso de multidex: " + report.warnings());

        byte[] dexSalida = entradaDe(salida, "classes.dex");
        byte[] dex2Salida = entradaDe(salida, "classes2.dex");
        assertArrayEquals(entradas.get("classes2.dex"), dex2Salida,
                "el segundo dex debía conservarse byte a byte");

        Set<String> tipos = tiposDe(dexSalida, "multidex-verificacion.dex");
        assertTrue(tipos.contains("Lcom/example/Hello;"), "tipos: " + tipos);
        assertFalse(tipos.contains("Lcom/example/Mundo;"),
                "la clase del segundo dex se duplicó: " + tipos);
        String cuerpo = texto(dexSalida);
        assertTrue(cuerpo.contains("valor-editado"), "el cambio no llegó al APK");
        assertFalse(cuerpo.contains("valor-original"), "la versión vieja sigue presente");

        Set<String> tipos2 = tiposDe(dex2Salida, "multidex-verificacion2.dex");
        assertTrue(tipos2.contains("Lcom/example/Mundo;"), "tipos2: " + tipos2);
        assertFalse(tipos2.contains("Lcom/example/Hello;"),
                "Hello se duplicó en el segundo dex: " + tipos2);

        assertFirmaCompleta(verificar(salida));
    }

    @Test
    void apkDeKotlinSeRecompilaConSuStdlibEmpaquetado() throws Exception {
        Path srcKt = temp.resolve("kotlin-src");
        Files.createDirectories(srcKt);
        Files.writeString(srcKt.resolve("Saludo.kt"), """
                package com.example

                class Saludo {
                    fun saludo(nombre: String): String = "hola kotlin, " + nombre
                }
                """, StandardCharsets.UTF_8);

        Path compilerJar = codeSource(K2JVMCompiler.class);
        Path stdlibJar = java.util.Arrays.stream(
                        System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .filter(p -> p.getFileName().toString().matches("kotlin-stdlib-\\d.*"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "kotlin-stdlib no está en el classpath de pruebas"));
        Path kotlinClasses = temp.resolve("kotlin-classes");

        String classpathProceso = java.util.Arrays.stream(
                        System.getProperty("java.class.path").split(File.pathSeparator))
                .filter(entrada -> {
                    String bajo = entrada.toLowerCase(java.util.Locale.ROOT);
                    return bajo.contains("kotlin") || bajo.contains("trove")
                            || bajo.contains("coroutines") || bajo.contains("annotations");
                })
                .collect(Collectors.joining(File.pathSeparator));
        assertTrue(classpathProceso.contains(compilerJar.getFileName().toString()),
                "classpath de kotlinc incompleto: " + classpathProceso);

        ProcessBuilder pb = new ProcessBuilder(javaEjecutable().toString(),
                "-cp", classpathProceso,
                "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                "-no-stdlib", "-no-reflect", "-classpath", stdlibJar.toString(),
                "-d", kotlinClasses.toString(),
                srcKt.resolve("Saludo.kt").toString());
        pb.redirectErrorStream(true);
        Process proceso = pb.start();
        String salidaKotlin = new String(proceso.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int codigo = proceso.waitFor();
        assertEquals(0, codigo, "kotlinc falló:\n" + salidaKotlin);

        Path claseKotlin = kotlinClasses.resolve("com").resolve("example").resolve("Saludo.class");
        assertTrue(Files.isRegularFile(claseKotlin), "kotlinc no produjo Saludo.class");
        String bytesClase = new String(Files.readAllBytes(claseKotlin),
                StandardCharsets.ISO_8859_1);
        assertTrue(bytesClase.contains("kotlin/Metadata"),
                "el archivo no fue compilado por el compilador de Kotlin");

        Path stdlibInterno = extraerClases(stdlibJar, "kotlin/jvm/internal/",
                temp.resolve("stdlib-interno"));
        Path programa = temp.resolve("kotlin-programa");
        copiaArbol(kotlinClasses, programa);
        copiaArbol(stdlibInterno, programa);
        Path dex = RebuildTestSupport.buildDex(programa, temp.resolve("kotlin-dex"));

        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("classes.dex", Files.readAllBytes(dex));
        entradas.put("AndroidManifest.xml",
                "<manifest package=\"com.example\"/>".getBytes(StandardCharsets.UTF_8));
        Path apk = apkConEntradas(temp.resolve("kotlin-fixture.apk"), entradas);

        Path dir = temp.resolve("salida-kotlin");
        prepararDescompilacion(dir, apk, Map.of("com/example/Saludo.java", """
                package com.example;

                import kotlin.jvm.internal.Intrinsics;

                public class Saludo {
                    public final String saludo(String nombre) {
                        Intrinsics.checkNotNullParameter(nombre, "nombre");
                        return "hola editado, " + nombre;
                    }
                }
                """));

        Path salida = dir.resolve("reconstruido.apk");
        RebuildReport report = reconstruir(dir, salida);

        assertTrue(report.success(), report.consoleSummary());
        assertEquals(1, report.replacedClasses());
        assertTrue(report.compileErrors().isEmpty());

        byte[] dexSalida = entradaDe(salida, "classes.dex");
        Set<String> tipos = tiposDe(dexSalida, "kotlin-verificacion.dex");
        assertTrue(tipos.contains("Lcom/example/Saludo;"), "tipos: " + tipos);
        assertTrue(tipos.contains("Lkotlin/jvm/internal/Intrinsics;"), "tipos: " + tipos);
        String cuerpo = texto(dexSalida);
        assertTrue(cuerpo.contains("hola editado"), "el cambio no llegó al APK");
        assertFalse(cuerpo.contains("hola kotlin"), "la versión original sigue presente");

        assertFirmaCompleta(verificar(salida));
    }

    @Test
    void dependenciaExternaSeResuelveDesdeElDexDelApk() throws Exception {
        Path gsonJar = codeSource(Gson.class);
        Path gsonClases = extraerClases(gsonJar, "com/google/gson/",
                temp.resolve("gson-clases"));

        Path appClases = compilarJava(temp.resolve("gson-app"), "Config.java", """
                package com.example;
                import com.google.gson.JsonObject;
                public class Config {
                    public String valor() {
                        JsonObject o = new JsonObject();
                        o.addProperty("clave", "valor-original");
                        return o.toString();
                    }
                }
                """, List.of(gsonJar));

        Path programa = temp.resolve("gson-programa");
        copiaArbol(appClases, programa);
        copiaArbol(gsonClases, programa);
        Path dex = RebuildTestSupport.buildDex(programa, temp.resolve("gson-dex"));

        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("classes.dex", Files.readAllBytes(dex));
        entradas.put("AndroidManifest.xml",
                "<manifest package=\"com.example\"/>".getBytes(StandardCharsets.UTF_8));
        Path apk = apkConEntradas(temp.resolve("gson-fixture.apk"), entradas);

        Path dir = temp.resolve("salida-gson");
        prepararDescompilacion(dir, apk, Map.of("com/example/Config.java", """
                package com.example;
                import com.google.gson.JsonObject;
                public class Config {
                    public String valor() {
                        JsonObject o = new JsonObject();
                        o.addProperty("clave", "valor-editado");
                        return o.toString();
                    }
                }
                """));

        Path salida = dir.resolve("reconstruido.apk");
        RebuildReport report = reconstruir(dir, salida);

        assertTrue(report.success(), report.consoleSummary());
        assertTrue(report.compileErrors().isEmpty());

        byte[] dexSalida = entradaDe(salida, "classes.dex");
        Set<String> tipos = tiposDe(dexSalida, "gson-verificacion.dex");
        assertTrue(tipos.contains("Lcom/example/Config;"), "tipos: " + tipos);
        assertTrue(tipos.contains("Lcom/google/gson/JsonObject;"), "tipos: " + tipos);
        String cuerpo = texto(dexSalida);
        assertTrue(cuerpo.contains("valor-editado"), "el cambio no llegó al APK");
        assertFalse(cuerpo.contains("valor-original"), "la versión vieja sigue presente");

        assertFirmaCompleta(verificar(salida));
    }

    @Test
    void recursosYAssetsSeConservanByteAByte() throws Exception {
        Path clases = compilarJava(temp.resolve("recursos-fixture"), "Hello.java", """
                package com.example;
                public class Hello {
                    public String valor() {
                        return "valor-original";
                    }
                }
                """, List.of());
        Path dex = RebuildTestSupport.buildDex(clases, temp.resolve("recursos-dex"));

        byte[] manifestOriginal =
                "<manifest package=\"com.example\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] arscOriginal = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x7F, 0x00, 0x42};
        byte[] stringsOriginal =
                "<string name=\"app_name\">Original</string>".getBytes(StandardCharsets.UTF_8);
        byte[] assetOriginal = "{\"clave\":\"original\"}".getBytes(StandardCharsets.UTF_8);

        Map<String, byte[]> entradas = new LinkedHashMap<>();
        entradas.put("classes.dex", Files.readAllBytes(dex));
        entradas.put("AndroidManifest.xml", manifestOriginal);
        entradas.put("resources.arsc", arscOriginal);
        entradas.put("res/values/strings.xml", stringsOriginal);
        entradas.put("assets/config.json", assetOriginal);
        Path apk = apkConEntradas(temp.resolve("recursos.apk"), entradas);

        Path dir = temp.resolve("salida-recursos");
        prepararDescompilacion(dir, apk, Map.of("com/example/Hello.java", HELLO_EDITADO));
        Files.createDirectories(dir.resolve("res").resolve("values"));
        Files.writeString(dir.resolve("res").resolve("values").resolve("strings.xml"),
                "<string name=\"app_name\">Editado por el usuario</string>",
                StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("assets"));
        Files.writeString(dir.resolve("assets").resolve("config.json"),
                "{\"clave\":\"editado\"}", StandardCharsets.UTF_8);

        Path salida = dir.resolve("reconstruido.apk");
        RebuildReport report = reconstruir(dir, salida);

        assertTrue(report.success(), report.consoleSummary());
        assertArrayEquals(manifestOriginal, entradaDe(salida, "AndroidManifest.xml"));
        assertArrayEquals(arscOriginal, entradaDe(salida, "resources.arsc"));
        assertArrayEquals(stringsOriginal, entradaDe(salida, "res/values/strings.xml"));
        assertArrayEquals(assetOriginal, entradaDe(salida, "assets/config.json"));
        assertTrue(texto(entradaDe(salida, "classes.dex")).contains("valor-editado"),
                "el cambio de código no llegó al APK");
        assertTrue(report.warnings().stream()
                        .anyMatch(w -> w.contains("Los recursos, el manifiesto y los assets")),
                "sin aviso de conservación de recursos: " + report.warnings());
    }

    @Test
    void firmaPropiaNoCoincideConElCertificadoDelDesarrollador() throws Exception {
        Path clases = compilarJava(temp.resolve("firma-fixture"), "Hello.java", """
                package com.example;
                public class Hello {
                    public String valor() {
                        return "valor-original";
                    }
                }
                """, List.of());
        Path dex = RebuildTestSupport.buildDex(clases, temp.resolve("firma-dex"));
        Path sinFirmar = RebuildTestSupport.apkWithDex(temp.resolve("firma-sin-firmar.apk"), dex);
        Path original = temp.resolve("original-del-desarrollador.apk");
        X509Certificate certificadoExterno =
                firmarComoDesarrollador(sinFirmar, original);

        ApkVerifier.Result verificacionOriginal = verificar(original);
        assertTrue(verificacionOriginal.isVerified(), "el fixture no quedó firmado");
        X509Certificate certificadoDelApk = verificacionOriginal.getSignerCertificates().get(0);
        assertTrue(certificadoDelApk.equals(certificadoExterno),
                "el APK original no conserva su certificado");
        assertTrue(certificadoDelApk.getSubjectX500Principal().getName()
                .contains("Desarrollador de prueba"));

        Path dir = temp.resolve("salida-firma");
        prepararDescompilacion(dir, original, Map.of("com/example/Hello.java", HELLO_EDITADO));
        Path salida = dir.resolve("reconstruido.apk");
        RebuildReport report = reconstruir(dir, salida);

        assertTrue(report.success(), report.consoleSummary());

        ApkVerifier.Result verificacionSalida = verificar(salida);
        assertFirmaCompleta(verificacionSalida);
        X509Certificate certificadoPropio = verificacionSalida.getSignerCertificates().get(0);
        assertTrue(certificadoPropio.getSubjectX500Principal().getName()
                .contains("Aquadecomp Rebuild"),
                "sujeto: " + certificadoPropio.getSubjectX500Principal());
        assertFalse(certificadoPropio.equals(certificadoDelApk),
                "el APK reconstruido quedó firmado con el certificado del desarrollador");
        assertFalse(certificadoPropio.getSubjectX500Principal()
                .equals(certificadoDelApk.getSubjectX500Principal()));
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("certificado propio")),
                "sin aviso de certificado propio: " + report.warnings());
    }
}
