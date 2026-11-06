package com.aquadecomp.rebuild;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DexBuilderTest {

    @TempDir
    Path temp;

    @Test
    void convierteClasesADex() throws Exception {
        Path classes = RebuildTestSupport.compileSource(temp, "Calculadora.java", """
                public class Calculadora {
                    public int suma(int a, int b) {
                        return a + b;
                    }
                }
                """);

        Path dex = RebuildTestSupport.buildDex(classes, temp.resolve("work"));

        assertTrue(Files.isRegularFile(dex));
        byte[] head = Files.readAllBytes(dex);
        assertEquals("dex\n", new String(head, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void empaquetaTodasLasClasesEnElJar() throws Exception {
        Path classes = RebuildTestSupport.compileSource(temp, "Dos.java", """
                class Dos {}
                class Tres {}
                """);
        Files.writeString(temp.resolve("src").resolve("Extra.java"),
                "class Extra {}", StandardCharsets.UTF_8);
        SourceCompiler.compile(List.of(temp.resolve("src").resolve("Extra.java")),
                classes, List.of());

        Path jar = DexBuilder.jarClasses(classes, temp.resolve("prog.jar"));

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertTrue(zip.getEntry("Dos.class") != null);
            assertTrue(zip.getEntry("Tres.class") != null);
            assertTrue(zip.getEntry("Extra.class") != null);
        }
    }
}
