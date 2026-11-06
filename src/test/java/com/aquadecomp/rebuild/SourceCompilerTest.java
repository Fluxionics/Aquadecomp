package com.aquadecomp.rebuild;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceCompilerTest {

    @TempDir
    Path temp;

    @Test
    void compilaFuenteValida() throws Exception {
        Path src = temp.resolve("src");
        Files.createDirectories(src);
        Path java = src.resolve("Saludo.java");
        Files.writeString(java, """
                public class Saludo {
                    public String hola() {
                        return "hola";
                    }
                }
                """, StandardCharsets.UTF_8);

        SourceCompiler.Result result = SourceCompiler.compile(List.of(java),
                temp.resolve("classes"), List.of());

        assertTrue(result.ok(), () -> String.join("\n", result.errors()));
        assertTrue(result.classCount() >= 1);
        assertTrue(Files.isRegularFile(temp.resolve("classes").resolve("Saludo.class")));
    }

    @Test
    void reportaErroresDeCompilacion() throws Exception {
        Path src = temp.resolve("src");
        Files.createDirectories(src);
        Path java = src.resolve("Roto.java");
        Files.writeString(java, """
                public class Roto {
                    public int f() {
                        return "no es un int";
                    }
                }
                """, StandardCharsets.UTF_8);

        SourceCompiler.Result result = SourceCompiler.compile(List.of(java),
                temp.resolve("classes"), List.of());

        assertFalse(result.ok());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Roto.java")),
                () -> "sin mención al archivo: " + result.errors());
    }

    @Test
    void listaArchivosJavaOrdenados() throws Exception {
        Path src = temp.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("B.java"), "class B {}", StandardCharsets.UTF_8);
        Files.writeString(src.resolve("A.java"), "class A {}", StandardCharsets.UTF_8);
        Files.writeString(src.resolve("nota.txt"), "no soy java", StandardCharsets.UTF_8);

        List<Path> files = SourceCompiler.javaFilesUnder(temp);

        assertEquals(2, files.size());
        assertTrue(files.get(0).toString().endsWith("A.java"));
        assertTrue(files.get(1).toString().endsWith("B.java"));
    }

    @Test
    void sinFuentesFallaConMensaje() throws Exception {
        SourceCompiler.Result result = SourceCompiler.compile(List.of(),
                temp.resolve("classes"), List.of());

        assertFalse(result.ok());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("No hay archivos"));
    }
}
