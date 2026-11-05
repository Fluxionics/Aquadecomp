package com.aquadecomp.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputNormalizerTest {

    @TempDir
    Path tmp;

    private final InputNormalizer normalizer = new InputNormalizer();

    private Path file(String name, byte[] content) throws IOException {
        Path path = tmp.resolve(name);
        Files.write(path, content);
        return path;
    }

    private Path zip(String name, Map<String, byte[]> entries) throws IOException {
        Path path = tmp.resolve(name);
        try (OutputStream out = Files.newOutputStream(path);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return path;
    }

    private static Map<String, byte[]> entries(Object... pairs) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (byte[]) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void detectTypeByExtension() {
        assertEquals(InputNormalizer.InputType.APK, InputNormalizer.detectType(Path.of("a.apk")));
        assertEquals(InputNormalizer.InputType.AAB, InputNormalizer.detectType(Path.of("a.aab")));
        assertEquals(InputNormalizer.InputType.XAPK, InputNormalizer.detectType(Path.of("a.xapk")));
        assertEquals(InputNormalizer.InputType.APKS, InputNormalizer.detectType(Path.of("a.apks")));
        assertEquals(InputNormalizer.InputType.UNKNOWN, InputNormalizer.detectType(Path.of("a.txt")));
        assertEquals(InputNormalizer.InputType.APK, InputNormalizer.detectType(Path.of("A.APK")));
    }

    @Test
    void missingFileIsRejected() throws IOException {
        Path missing = tmp.resolve("no-existe.apk");
        IOException e = assertThrows(IOException.class, () -> normalizer.normalize(missing));
        assertTrue(e.getMessage().contains("Archivo no encontrado"));
    }

    @Test
    void emptyFileIsRejected() throws IOException {
        Path empty = file("vacio.apk", new byte[0]);
        IOException e = assertThrows(IOException.class, () -> normalizer.normalize(empty));
        assertTrue(e.getMessage().contains("No es un archivo ZIP"));
    }

    @Test
    void plainTextRenamedToApkIsRejected() throws IOException {
        Path text = file("texto.apk", "esto no es un zip".getBytes(StandardCharsets.UTF_8));
        IOException e = assertThrows(IOException.class, () -> normalizer.normalize(text));
        assertTrue(e.getMessage().contains("No es un archivo ZIP"));
    }

    @Test
    void zipWithoutAndroidContentAndUnknownExtensionIsRejected() throws IOException {
        Path zip = zip("archivo.zip", entries("readme.txt", "hola".getBytes(StandardCharsets.UTF_8)));
        IOException e = assertThrows(IOException.class, () -> normalizer.normalize(zip));
        assertTrue(e.getMessage().contains("Formato no soportado"));
    }

    @Test
    void apkWithDexIsAccepted() throws IOException {
        Path apk = zip("app.apk", entries(
                "classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a},
                "classes2.dex", new byte[]{0x64, 0x65, 0x78, 0x0a},
                "AndroidManifest.xml", new byte[]{0x50, 0x4b, 0x03, 0x04}));

        InputNormalizer.NormalizedInput input = normalizer.normalize(apk);
        assertEquals(InputNormalizer.InputType.APK, input.type());
        assertEquals(2, input.dexEntries());
        assertFalse(input.isContainer());
    }

    @Test
    void apkWithoutDexIsStillAcceptedByExtension() throws IOException {
        Path apk = zip("sin-dex.apk", entries("AndroidManifest.xml", new byte[]{1, 2, 3}));

        InputNormalizer.NormalizedInput input = normalizer.normalize(apk);
        assertEquals(InputNormalizer.InputType.APK, input.type());
        assertEquals(0, input.dexEntries());
    }

    @Test
    void unknownExtensionWithAndroidContentIsAcceptedAsApk() throws IOException {
        Path zip = zip("paquete.bin", entries("classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a}));

        InputNormalizer.NormalizedInput input = normalizer.normalize(zip);
        assertEquals(InputNormalizer.InputType.APK, input.type());
        assertEquals(1, input.dexEntries());
    }

    @Test
    void xapkContainerIsDetected() throws IOException {
        Path xapk = zip("paquete.xapk", entries(
                "base.apk", "PK".getBytes(StandardCharsets.UTF_8),
                "AndroidManifest.xml", new byte[]{1}));

        InputNormalizer.NormalizedInput input = normalizer.normalize(xapk);
        assertEquals(InputNormalizer.InputType.XAPK, input.type());
        assertTrue(input.isContainer());
    }

    @Test
    void apksContainerIsDetected() throws IOException {
        Path apks = zip("splits.apks", entries(
                "base-master.apk", "PK".getBytes(StandardCharsets.UTF_8)));

        InputNormalizer.NormalizedInput input = normalizer.normalize(apks);
        assertEquals(InputNormalizer.InputType.APKS, input.type());
        assertTrue(input.isContainer());
    }

    @Test
    void aabIsDetectedByExtension() throws IOException {
        Path aab = zip("app.aab", entries(
                "base/dex/classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a}));

        InputNormalizer.NormalizedInput input = normalizer.normalize(aab);
        assertEquals(InputNormalizer.InputType.AAB, input.type());
        assertFalse(input.isContainer());
        assertEquals(1, input.dexEntries());
    }

    @Test
    void dexEntryNameMatchingIsBaseNameOnly() throws IOException {
        Path apk = zip("app.apk", entries(
                "assets/misclasses.dex.bak", new byte[]{1},
                "classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a}));

        InputNormalizer.NormalizedInput input = normalizer.normalize(apk);
        assertEquals(1, input.dexEntries());
    }

    @Test
    void apkHasNoExtractedPrimaryApk() throws IOException {
        Path apk = zip("app.apk", entries("classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a}));

        InputNormalizer.NormalizedInput input = normalizer.normalize(apk);

        assertNull(input.primaryApk());
        assertEquals(input.file(), input.analysisTarget());
        assertFalse(input.isContainer());
    }

    @Test
    void xapkExposesItsPrimaryApkForAnalysis() throws IOException {
        byte[] inner = Files.readAllBytes(zip("interno.apk",
                entries("classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a})));
        Path xapk = zip("paquete.xapk", entries(
                "base.apk", inner,
                "manifest.json", "{\"version\":2}".getBytes(StandardCharsets.UTF_8)));

        InputNormalizer.NormalizedInput input = normalizer.normalize(xapk);

        assertEquals(InputNormalizer.InputType.XAPK, input.type());
        assertTrue(input.isContainer());
        assertNotNull(input.primaryApk());
        assertEquals(input.primaryApk(), input.analysisTarget());
        try (java.util.zip.ZipFile extracted = new java.util.zip.ZipFile(input.primaryApk().toFile())) {
            assertNotNull(extracted.getEntry("classes.dex"));
        } finally {
            Files.deleteIfExists(input.primaryApk());
        }
    }

    @Test
    void apksFallsBackToTheFirstApkEntry() throws IOException {
        byte[] inner = Files.readAllBytes(zip("interno.apk",
                entries("classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a})));
        Path apks = zip("splits.apks", entries("otro-master.apk", inner));

        InputNormalizer.NormalizedInput input = normalizer.normalize(apks);

        assertNotNull(input.primaryApk());
        assertTrue(Files.isRegularFile(input.primaryApk()));
        Files.deleteIfExists(input.primaryApk());
    }

    @Test
    void containerWithoutAnyApkInsideKeepsTheOriginalFile() throws IOException {
        Path xapk = zip("vacio.xapk", entries(
                "manifest.json", "{\"version\":2}".getBytes(StandardCharsets.UTF_8)));

        InputNormalizer.NormalizedInput input = normalizer.normalize(xapk);

        assertNull(input.primaryApk());
        assertEquals(input.file(), input.analysisTarget());
    }
}
