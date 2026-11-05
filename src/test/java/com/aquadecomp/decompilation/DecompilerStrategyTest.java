package com.aquadecomp.decompilation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompilerStrategyTest {

    private static final String SOURCE = """
            package com.example;

            public class Sample {
                public int add(int a, int b) {
                    return a + b;
                }

                public String greet() {
                    return "hola";
                }
            }
            """;

    @TempDir
    Path tmp;

    private Path jar;

    @BeforeEach
    void compileAndJar() throws IOException {
        Path src = tmp.resolve("src/com/example/Sample.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, SOURCE);

        Path classes = tmp.resolve("classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Se requiere un JDK completo para compilar el fixture");
        int rc = compiler.run(null, null, null,
                "-d", classes.toString(), "--release", "11", src.toString());
        assertEquals(0, rc, "el fixture no compila");

        jar = tmp.resolve("sample.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            Path classFile = classes.resolve("com/example/Sample.class");
            out.putNextEntry(new ZipEntry("com/example/Sample.class"));
            out.write(Files.readAllBytes(classFile));
            out.closeEntry();
        }
    }

    private List<DecompilerStrategy> engines() {
        return List.of(new CfrDecompiler(), new ProcyonDecompiler(), new VineflowerDecompiler());
    }

    @Test
    void allRescueEnginesAreOnTheClasspath() {
        for (DecompilerStrategy engine : engines()) {
            assertTrue(engine.isAvailable(), engine.name() + " no está disponible");
        }
    }

    @Test
    void eachEngineRescuesAKnownClass() throws Exception {
        for (DecompilerStrategy engine : engines()) {
            Path staging = tmp.resolve("salida-" + engine.name());
            Set<String> produced = engine.rescue(jar, staging,
                    Set.of("com.example.Sample"),
                    DecompilerStrategy.DecompilerConfig.defaults());

            assertTrue(produced.contains("com.example.Sample"),
                    engine.name() + " no reportó la clase rescatada");

            Path file = staging.resolve("com/example/Sample.java");
            assertTrue(Files.isRegularFile(file),
                    engine.name() + " no escribió com/example/Sample.java");

            String code = Files.readString(file);
            assertTrue(code.contains("class Sample"), engine.name() + " produjo código vacío");
            assertTrue(code.contains("add"), engine.name() + " perdió el método add");
        }
    }

    @Test
    void enginesReportNothingForClassesAbsentFromTheJar() throws Exception {
        for (DecompilerStrategy engine : engines()) {
            Path staging = tmp.resolve("ausente-" + engine.name());
            Set<String> produced = engine.rescue(jar, staging,
                    Set.of("com.example.NoExiste"),
                    DecompilerStrategy.DecompilerConfig.defaults());

            assertTrue(produced.isEmpty(),
                    engine.name() + " inventó una clase que no existe");
            assertFalse(Files.exists(staging.resolve("com/example/NoExiste.java")));
        }
    }
}
