package com.aquadecomp.nativecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhidraLocatorTest {

    @TempDir
    Path temp;

    private Path fakeInstall() throws Exception {
        Path home = temp.resolve("ghidra-fake");
        Files.createDirectories(home.resolve("support"));
        Files.writeString(home.resolve("support").resolve("analyzeHeadless.bat"),
                "@echo off\r\nexit /b 0\r\n");
        return home;
    }

    @Test
    void directorioSinGhidraNoSeDetecta() throws Exception {
        Path vacio = temp.resolve("vacio");
        Files.createDirectories(vacio);

        assertFalse(GhidraLocator.find(vacio, null, null).isPresent());
    }

    @Test
    void directorioConAnalyzeHeadlessSeDetecta() throws Exception {
        Path home = fakeInstall();

        Optional<Path> found = GhidraLocator.find(home, null, null);

        assertTrue(found.isPresent());
        assertEquals(home, found.get());
    }

    @Test
    void directorioExplicitoInvalidoSeDescarta() throws Exception {
        Path home = fakeInstall();
        Path otro = temp.resolve("otro");
        Files.createDirectories(otro);

        assertFalse(GhidraLocator.find(otro, home.toString(), null).isPresent());
        assertTrue(GhidraLocator.find(home, "C:/no-existe", null).isPresent());
    }

    @Test
    void variableDeEntornoIndicaLaInstalacion() throws Exception {
        Path home = fakeInstall();

        Optional<Path> found = GhidraLocator.find(null, home.toString(), List.of());

        assertTrue(found.isPresent());
        assertEquals(home, found.get());
    }

    @Test
    void buscaEnElPathCuandoNoHayVariable() throws Exception {
        Path home = fakeInstall();
        Path bin = home.resolve("support");
        Files.createDirectories(bin);

        Optional<Path> found = GhidraLocator.find(null, null, List.of(
                temp.resolve("no-existe").toString(), bin.toString()));

        assertTrue(found.isPresent());
        assertEquals(bin, found.get());
    }

    @Test
    void sinInstalacionNiVariableNiPathDevuelveVacio() {
        Optional<Path> found = GhidraLocator.find(null, null,
                List.of(temp.resolve("nada").toString()));

        assertFalse(found.isPresent());
    }
}
