package com.aquadecomp.nativecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhidraRunnerTest {

    @TempDir
    Path temp;

    private Path fakeGhidra() throws Exception {
        Path home = temp.resolve("ghidra");
        Files.createDirectories(home.resolve("support"));
        Files.writeString(home.resolve("support").resolve("analyzeHeadless.bat"),
                "@echo off\r\n"
                        + "echo === fakeFunction @ 0x1000> \"%~7\"\r\n"
                        + "echo int fakeFunction(void) { return 1; }>> \"%~7\"\r\n"
                        + "exit /b 0\r\n",
                StandardCharsets.US_ASCII);
        return home;
    }

    @Test
    void ejecutaAnalyzeHeadlessFalsoYLeeLaSalida() throws Exception {
        Path home = fakeGhidra();
        Path so = temp.resolve("libfake.so");
        Files.write(so, ElfFixture.elf64());
        Path out = temp.resolve("salida");

        GhidraRunner.Result result = GhidraRunner.decompile(home, so, out,
                Duration.ofSeconds(60));

        assertNotNull(result.outputFile());
        assertTrue(Files.isRegularFile(result.outputFile()));
        String content = Files.readString(result.outputFile());
        assertTrue(content.contains("fakeFunction"), "salida: " + content);
        assertEquals(1, result.functions());
        assertTrue(Files.isRegularFile(out.resolve("DecompileFunctions.java")),
                "el script de Ghidra debe extraerse");
    }

    @Test
    void falloConCodigoDeSalidaDistintoDeCeroIncluyeElLog() throws Exception {
        Path home = temp.resolve("ghidra-malo");
        Files.createDirectories(home.resolve("support"));
        Files.writeString(home.resolve("support").resolve("analyzeHeadless.bat"),
                "@echo off\r\necho fallo interno de prueba\r\nexit /b 3\r\n",
                StandardCharsets.US_ASCII);
        Path so = temp.resolve("libfake.so");
        Files.write(so, ElfFixture.elf64());

        java.io.IOException error = assertThrows(java.io.IOException.class,
                () -> GhidraRunner.decompile(home, so, temp.resolve("salida2"),
                        Duration.ofSeconds(60)));

        assertTrue(error.getMessage().contains("código 3"),
                "mensaje: " + error.getMessage());
        assertTrue(error.getMessage().contains("fallo interno de prueba"),
                "mensaje: " + error.getMessage());
    }

    @Test
    void sinAnalyzeHeadlessFallaAntesDeEjecutar() throws Exception {
        Path vacio = temp.resolve("sin-soporte");
        Files.createDirectories(vacio.resolve("support"));
        Path so = temp.resolve("libfake.so");
        Files.write(so, ElfFixture.elf64());

        java.io.IOException error = assertThrows(java.io.IOException.class,
                () -> GhidraRunner.decompile(vacio, so, temp.resolve("salida3"),
                        Duration.ofSeconds(10)));

        assertTrue(error.getMessage().contains("analyzeHeadless"),
                "mensaje: " + error.getMessage());
    }

    @Test
    void cuentaFuncionesPorMarcador() {
        assertEquals(0, GhidraRunner.countFunctions(""));
        assertEquals(2, GhidraRunner.countFunctions(
                "=== a @ 0x1\nint a(){}\n\n=== b @ 0x2\nint b(){}\n"));
    }
}
