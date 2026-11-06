package com.aquadecomp.mapping;

import com.aquadecomp.rebuild.RebuildTestSupport;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingApplierTest {

    @TempDir
    Path temp;

    private static final List<String> MAPPING = List.of(
            "com.example.Foo -> a.b.c:",
            "    int contador -> e",
            "    int calcular(int) -> a");

    private Path fixtureApk() throws Exception {
        Path classes = RebuildTestSupport.compileSource(temp.resolve("fixture"), "c.java", """
                package a.b;
                public class c {
                    public static int e = 5;
                    public static int a(int x) {
                        return x + e;
                    }
                }
                """);
        Path dex = RebuildTestSupport.buildDex(classes, temp.resolve("work"));
        return RebuildTestSupport.apkWithDex(temp.resolve("base.apk"), dex);
    }

    private static DexFile loadDexFromApk(Path apk) throws Exception {
        byte[] dex;
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            dex = zip.getInputStream(zip.getEntry("classes.dex")).readAllBytes();
        }
        Path file = apk.resolveSibling("lectura.dex");
        Files.write(file, dex);
        try {
            return DexFileFactory.loadDexFile(file.toFile(), Opcodes.getDefault());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void restauraNombresEnElApk() throws Exception {
        Path apk = fixtureApk();
        Path salida = temp.resolve("mapeado.apk");

        MappingApplier.Applied applied = MappingApplier.apply(apk,
                MappingFile.parse(MAPPING), salida);

        assertTrue(Files.isRegularFile(salida));
        assertEquals(1, applied.classes());
        assertTrue(applied.members() >= 2, "miembros: " + applied.members());

        DexFile dex = loadDexFromApk(salida);
        assertEquals(1, dex.getClasses().size());
        ClassDef classDef = dex.getClasses().iterator().next();
        assertEquals("Lcom/example/Foo;", classDef.getType());
        assertTrue(java.util.stream.StreamSupport
                .stream(classDef.getMethods().spliterator(), false)
                .anyMatch(m -> m.getName().equals("calcular")));

        try (ZipFile zip = new ZipFile(salida.toFile())) {
            assertTrue(zip.getEntry("AndroidManifest.xml") != null,
                    "el manifiesto debe conservarse");
        }
    }

    @Test
    void fallaSiElApkNoTieneDex() throws Exception {
        Path sinDex = temp.resolve("sin-dex.apk");
        try (OutputStream fos = Files.newOutputStream(sinDex);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("<manifest/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        java.io.IOException error = assertThrows(java.io.IOException.class,
                () -> MappingApplier.apply(sinDex, MappingFile.parse(MAPPING),
                        temp.resolve("salida.apk")));

        assertTrue(error.getMessage().contains("dex"),
                "mensaje sin indicación de dex: " + error.getMessage());
    }
}
