package com.aquadecomp.rebuild;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApkPackerTest {

    @TempDir
    Path temp;

    private Path originalApk() throws Exception {
        Path apk = temp.resolve("original.apk");
        byte[] manifestBytes = "<?xml version=\"1.0\"?><manifest/>"
                .getBytes(StandardCharsets.UTF_8);
        try (OutputStream fos = Files.newOutputStream(apk);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            writeStored(zip, "classes.dex", "DEX-VIEJO".getBytes(StandardCharsets.UTF_8));
            writeStored(zip, "AndroidManifest.xml", manifestBytes);
            zip.putNextEntry(new ZipEntry("assets/dato.txt"));
            zip.write("contenido".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/CERT.SF"));
            zip.write("Signature-Version: 1.0".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/CERT.RSA"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        return apk;
    }

    private static void writeStored(ZipOutputStream zip, String name, byte[] data)
            throws Exception {
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    @Test
    void reemplazaDexYConservaElResto() throws Exception {
        Path original = originalApk();
        Path output = temp.resolve("nuevo.apk");
        byte[] nuevoDex = "DEX-NUEVO".getBytes(StandardCharsets.UTF_8);

        ApkPacker.repack(original, output,
                List.of(new ApkPacker.Replacement("classes.dex", nuevoDex)));

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertArrayEquals(nuevoDex, zip.getInputStream(
                    zip.getEntry("classes.dex")).readAllBytes());
            assertTrue(zip.getEntry("assets/dato.txt") != null);
            assertArrayEquals("<?xml version=\"1.0\"?><manifest/>"
                            .getBytes(StandardCharsets.UTF_8),
                    zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes());
            assertTrue(zip.getEntry("AndroidManifest.xml").getMethod() == ZipEntry.STORED);
        }
    }

    @Test
    void borraFirmasAnteriores() throws Exception {
        Path original = originalApk();
        Path output = temp.resolve("nuevo.apk");

        ApkPacker.repack(original, output,
                List.of(new ApkPacker.Replacement("classes.dex", "X".getBytes(StandardCharsets.UTF_8))));

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertNull(zip.getEntry("META-INF/MANIFEST.MF"));
            assertNull(zip.getEntry("META-INF/CERT.SF"));
            assertNull(zip.getEntry("META-INF/CERT.RSA"));
        }
    }

    @Test
    void entradasGuardadasQuedanAlineadas() throws Exception {
        Path original = originalApk();
        Path output = temp.resolve("nuevo.apk");

        ApkPacker.repack(original, output,
                List.of(new ApkPacker.Replacement("classes.dex", "DEX-NUEVO".getBytes(StandardCharsets.UTF_8))));

        byte[] bytes = Files.readAllBytes(output);
        long offset = RebuildTestSupport.storedDataOffset(bytes, "classes.dex");
        assertTrue(offset >= 0, "no se encontró el local header de classes.dex");
        assertEquals(0, offset % 4096, "classes.dex desalineado en " + offset);

        long manifestOffset = RebuildTestSupport.storedDataOffset(bytes, "AndroidManifest.xml");
        assertTrue(manifestOffset >= 0);
        assertEquals(0, manifestOffset % 4096, "AndroidManifest.xml desalineado");
    }

    @Test
    void identificaFirmasYPorRaqueDex() {
        assertTrue(ApkPacker.isSignatureFile("META-INF/CERT.SF"));
        assertTrue(ApkPacker.isSignatureFile("META-INF/CERT.RSA"));
        assertTrue(ApkPacker.isSignatureFile("META-INF/MANIFEST.MF"));
        assertTrue(ApkPacker.isSignatureFile("meta-inf/CERT.DSA"));
        assertTrue(ApkPacker.isSignatureFile("META-INF/CERT.EC"));
        assertTrue(!ApkPacker.isSignatureFile("META-INF/services/x"));
        assertTrue(!ApkPacker.isSignatureFile("res/META-INF/CERT.SF"));
        assertTrue(ApkPacker.isRootDex("classes.dex"));
        assertTrue(ApkPacker.isRootDex("classes2.dex"));
        assertTrue(!ApkPacker.isRootDex("assets/classes.dex"));
        assertTrue(!ApkPacker.isRootDex("classes.dex.bak"));
    }
}
