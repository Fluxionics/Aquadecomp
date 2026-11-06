package com.aquadecomp.nativecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAnalyzerTest {

    @TempDir
    Path temp;

    private Path apkConBibliotecas() throws Exception {
        Path apk = temp.resolve("nativo.apk");
        try (OutputStream fos = Files.newOutputStream(apk);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("<manifest package=\"com.example\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write("dex\n035\0".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("lib/arm64-v8a/libfake.so"));
            zip.write(ElfFixture.elf64());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("lib/armeabi-v7a/libother.so"));
            zip.write(ElfFixture.elf32());
            zip.closeEntry();
        }
        return apk;
    }

    @Test
    void listaBibliotecasConSuArquitectura() throws Exception {
        NativeAnalyzer.Report report = NativeAnalyzer.analyze(apkConBibliotecas(),
                null, false, null, null);

        assertEquals(2, report.libraries());
        assertEquals(2, report.items().size());

        NativeAnalyzer.Library primera = report.items().get(0);
        assertEquals("lib/arm64-v8a/libfake.so", primera.entry());
        assertEquals("arm64-v8a", primera.abi());
        assertNull(primera.error());
        assertNotNull(primera.elf());
        assertEquals(64, primera.elf().bits());

        NativeAnalyzer.Library segunda = report.items().get(1);
        assertEquals("lib/armeabi-v7a/libother.so", segunda.entry());
        assertEquals("armeabi-v7a", segunda.abi());
        assertEquals(32, segunda.elf().bits());

        assertFalse(report.ghidra().requested(), "Ghidra no debe ejecutarse sin petición");
        assertTrue(report.warnings().isEmpty(), "avisos: " + report.warnings());
    }

    @Test
    void filtroReduceLaLista() throws Exception {
        NativeAnalyzer.Report report = NativeAnalyzer.analyze(apkConBibliotecas(),
                "other", false, null, null);

        assertEquals(1, report.libraries());
        assertEquals("lib/armeabi-v7a/libother.so", report.items().get(0).entry());
    }

    @Test
    void filtroSinCoincidenciasAvisa() throws Exception {
        NativeAnalyzer.Report report = NativeAnalyzer.analyze(apkConBibliotecas(),
                "inexistente", false, null, null);

        assertEquals(0, report.libraries());
        assertTrue(report.warnings().get(0).contains("inexistente"),
                "avisos: " + report.warnings());
    }

    @Test
    void paqueteSinBibliotecasAvisa() throws Exception {
        Path apk = temp.resolve("sin-nativos.apk");
        try (OutputStream fos = Files.newOutputStream(apk);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("<manifest package=\"com.example\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write("dex\n035\0".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }

        NativeAnalyzer.Report report = NativeAnalyzer.analyze(apk, null, false,
                null, null);

        assertEquals(0, report.libraries());
        assertTrue(report.warnings().get(0).contains("no contiene bibliotecas"),
                "avisos: " + report.warnings());
    }

    @Test
    void bibliotecaCorruptaNoDetieneElAnalisis() throws Exception {
        Path apk = temp.resolve("corrupta.apk");
        try (OutputStream fos = Files.newOutputStream(apk);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("<manifest package=\"com.example\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("lib/arm64-v8a/librot.so"));
            zip.write("esto no es ELF".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }

        NativeAnalyzer.Report report = NativeAnalyzer.analyze(apk, null, false,
                null, null);

        assertEquals(1, report.libraries());
        NativeAnalyzer.Library lib = report.items().get(0);
        assertNull(lib.elf());
        assertNotNull(lib.error());
        assertTrue(lib.error().contains("ELF"), "error: " + lib.error());
        assertEquals("arm64-v8a", lib.abi());
    }

    @Test
    void pideGhidraSinInstalacionYLoDiceConClaridad() throws Exception {
        NativeAnalyzer.Report report = NativeAnalyzer.analyze(apkConBibliotecas(),
                null, true, temp.resolve("ghidra-inexistente"), null);

        assertTrue(report.ghidra().requested());
        assertFalse(report.ghidra().available());
        assertTrue(report.ghidra().message().contains("Ghidra no está disponible"),
                "mensaje: " + report.ghidra().message());
        assertTrue(report.ghidra().message().contains("análisis ELF"),
                "mensaje: " + report.ghidra().message());
        assertNull(report.ghidra().outputFile());
    }
}
