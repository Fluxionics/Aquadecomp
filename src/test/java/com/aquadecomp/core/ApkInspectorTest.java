package com.aquadecomp.core;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApkInspectorTest {

    @TempDir
    Path tmp;

    private final ApkInspector inspector = new ApkInspector();

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
    void inspectsStructureOfAStandardApk() throws IOException {
        Path apk = zip("app.apk", entries(
                "classes.dex", new byte[]{1},
                "classes2.dex", new byte[]{2},
                "AndroidManifest.xml", new byte[]{1},
                "resources.arsc", new byte[]{1},
                "lib/arm64-v8a/libnative.so", new byte[]{1},
                "assets/config.json", "{}".getBytes(StandardCharsets.UTF_8)));

        ApkStructure s = inspector.inspect(apk);

        assertEquals(6, s.entryCount());
        assertEquals(2, s.dexFiles().size());
        assertTrue(s.dexFiles().contains("classes.dex"));
        assertEquals(1, s.nativeLibs().size());
        assertEquals(1, s.assetEntries().size());
        assertTrue(s.hasBinaryManifest());
        assertTrue(s.hasResourcesTable());
        assertTrue(s.hasMultiDex());
        assertFalse(s.detectedPackers().stream().anyMatch(p -> p.contains("360")));
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("Multidex")));
    }

    @Test
    void warnsWhenDexIsMissing() throws IOException {
        Path apk = zip("sin-dex.apk", entries("AndroidManifest.xml", new byte[]{1}));

        ApkStructure s = inspector.inspect(apk);

        assertTrue(s.dexFiles().isEmpty());
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("No se encontró classes.dex")));
        assertFalse(s.hasMultiDex());
    }

    @Test
    void detectsKnownPackerSignatures() throws IOException {
        Path apk = zip("packeado.apk", entries(
                "classes.dex", new byte[]{1},
                "lib/arm64-v8a/libjiagu.so", new byte[]{1}));

        ApkStructure s = inspector.inspect(apk);

        assertEquals(1, s.detectedPackers().size());
        assertTrue(s.detectedPackers().get(0).contains("360"));
    }

    @Test
    void warnsAboutEmbeddedExecutableInAssets() throws IOException {
        Path apk = zip("oculto.apk", entries(
                "classes.dex", new byte[]{1},
                "assets/payload.dex", new byte[]{1}));

        ApkStructure s = inspector.inspect(apk);

        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("Ejecutable embebido")));
        assertEquals(1, s.dexFiles().size());
    }

    @Test
    void bundleLayoutDexFilesAreCounted() throws IOException {
        Path aab = zip("app.aab", entries(
                "base/manifest/AndroidManifest.xml", new byte[]{1},
                "base/dex/classes.dex", new byte[]{1},
                "base/dex/classes2.dex", new byte[]{2}));

        ApkStructure s = inspector.inspect(aab);

        assertEquals(2, s.dexFiles().size());
        assertTrue(s.dexFiles().contains("base/dex/classes.dex"));
        assertFalse(s.warnings().stream().anyMatch(w -> w.contains("No se encontró classes.dex")));
    }

    @Test
    void missingFileThrowsIo() {
        IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> inspector.inspect(tmp.resolve("no-apk.apk")));
        assertTrue(e.getMessage().contains("No existe"));
    }

    @Test
    void manifestWithoutEntryFailsCleanly() throws IOException {
        Path apk = zip("sin-manifiesto.apk", entries("classes.dex", new byte[]{1}));

        ManifestInfo m = inspector.readManifest(apk);

        assertFalse(m.parsedOk());
        assertTrue(m.parseError().contains("no contiene AndroidManifest.xml"));
    }

    @Test
    void garbageManifestFailsCleanly() throws IOException {
        Path apk = zip("basura.apk", entries(
                "AndroidManifest.xml", "esto no es AXML".getBytes(StandardCharsets.UTF_8)));

        ManifestInfo m = inspector.readManifest(apk);

        assertFalse(m.parsedOk());
        assertTrue(m.parseError().contains("XML binario") || m.parseError().contains("Error"));
    }

    @Test
    void emptyManifestFailsCleanly() throws IOException {
        Path apk = zip("vacio.apk", entries("AndroidManifest.xml", new byte[0]));

        ManifestInfo m = inspector.readManifest(apk);

        assertFalse(m.parsedOk());
        assertTrue(m.parseError().contains("vacío"));
    }
}
