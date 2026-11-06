package com.aquadecomp.nativecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElfParserTest {

    @TempDir
    Path temp;

    @Test
    void parseaElf64De64BitsConSimbolos() throws Exception {
        ElfParser.ElfInfo elf = ElfParser.parse(ElfFixture.elf64());

        assertEquals(64, elf.bits());
        assertTrue(elf.littleEndian());
        assertEquals(183, elf.machine());
        assertEquals("aarch64", elf.architecture());
        assertEquals("arm64-v8a", elf.abi());
        assertTrue(elf.stripped(), "sin .symtab debe reportarse como stripped");

        List<String> names = ElfFixture.sectionNames(elf);
        assertTrue(names.contains(".dynsym"), "secciones: " + names);
        assertTrue(names.contains(".dynamic"), "secciones: " + names);

        assertEquals(List.of("libbar.so"), elf.needed());
        assertEquals("libfake.so", elf.soname());
        assertTrue(elf.exports().contains("Java_com_example_init"),
                "exports: " + elf.exports());
        assertTrue(elf.exports().contains("someGlobalVar"),
                "exports: " + elf.exports());
        assertEquals(2, elf.exports().size());
        assertEquals(1, elf.imports());
        assertEquals(3, elf.symbols());
    }

    @Test
    void parseaElf32De32Bits() throws Exception {
        ElfParser.ElfInfo elf = ElfParser.parse(ElfFixture.elf32());

        assertEquals(32, elf.bits());
        assertTrue(elf.littleEndian());
        assertEquals(40, elf.machine());
        assertEquals("arm", elf.architecture());
        assertEquals("armeabi-v7a", elf.abi());
        assertEquals(List.of("libbar.so"), elf.needed());
        assertEquals(2, elf.exports().size());
        assertEquals(1, elf.imports());
    }

    @Test
    void rechazaArchivoQueNoEsElf() {
        byte[] basura = "esto no es un binario ELF".getBytes(StandardCharsets.US_ASCII);

        IOExceptionFallo fallo = null;
        try {
            ElfParser.parse(basura);
        } catch (java.io.IOException e) {
            fallo = new IOExceptionFallo(e.getMessage());
        }

        assertNotNull(fallo, "debía rechazar el archivo");
        assertTrue(fallo.message.contains("ELF"), "mensaje: " + fallo.message);
    }

    @Test
    void archivoTruncadoFallaConMensaje() {
        byte[] real = ElfFixture.elf64();
        byte[] truncado = new byte[20];
        System.arraycopy(real, 0, truncado, 0, truncado.length);

        java.io.IOException error = assertThrows(java.io.IOException.class,
                () -> ElfParser.parse(truncado));

        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }

    @Test
    void parseaBibliotecaRealSiExisteEnElEquipo() throws Exception {
        Path real = Path.of("testdata", "out-app2", "resources", "lib",
                "arm64-v8a", "libandroidx.graphics.path.so");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(real),
                "no hay bibliotecas reales en este equipo");

        ElfParser.ElfInfo elf = ElfParser.parse(Files.readAllBytes(real));

        assertEquals(64, elf.bits());
        assertEquals("aarch64", elf.architecture());
        assertEquals("arm64-v8a", elf.abi());
        assertTrue(elf.symbols() > 0, "símbolos: " + elf.symbols());
        assertFalse(elf.exports().isEmpty(), "exports vacíos");
        assertFalse(elf.needed().isEmpty(), "dependencias vacías");
    }

    private record IOExceptionFallo(String message) {
    }
}
