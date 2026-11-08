package com.aquadecomp.rebuild;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DexMergeTest {

    @TempDir
    Path temp;

    private Path apkConDosClases() throws Exception {
        Path classes = RebuildTestSupport.compileSource(temp, "Alpha.java", """
                class Alpha {
                    String valor() {
                        return "alpha-original";
                    }
                }
                class Beta {
                    String valor() {
                        return "beta-original";
                    }
                }
                """);
        Path dex = RebuildTestSupport.buildDex(classes, temp.resolve("work"));
        return RebuildTestSupport.apkWithDex(temp.resolve("base.apk"), dex);
    }

    @Test
    void reemplazaUnaClaseConservaLasDemas() throws Exception {
        Path apk = apkConDosClases();

        Path clasesNuevas = temp.resolve("nuevas");
        Files.createDirectories(clasesNuevas);
        Path src = temp.resolve("nuevasrc");
        Files.createDirectories(src);
        Path java = src.resolve("Beta.java");
        Files.writeString(java, """
                class Beta {
                    String valor() {
                        return "beta-modificada";
                    }
                }
                """, StandardCharsets.UTF_8);
        SourceCompiler.compile(List.of(java), clasesNuevas, List.of());
        Path dexNuevo = RebuildTestSupport.buildDex(clasesNuevas, temp.resolve("work2"));

        DexMerge.MergeResult result = DexMerge.merge(apk, List.of(dexNuevo),
                Set.of("Beta"), temp);

        assertEquals(1, result.classesReplaced());
        assertEquals(1, result.replacements().size());
        ApkPacker.Replacement replacement = result.replacements().get(0);
        assertEquals("classes.dex", replacement.name());

        byte[] bytes = replacement.data();
        Path outDex = temp.resolve("reemplazado.dex");
        Files.write(outDex, bytes);
        var dex = DexFileFactory.loadDexFile(outDex.toFile(), Opcodes.getDefault());
        Set<String> names = dex.getClasses().stream()
                .map(ClassDef::getType)
                .collect(Collectors.toSet());
        assertTrue(names.contains("LAlpha;"), "Alpha desapareció: " + names);
        assertTrue(names.contains("LBeta;"), "Beta desapareció: " + names);

        assertTrue(RebuildTestSupport.containsBytes(bytes, "beta-modificada"),
                "el cambio no llegó al dex reemplazado");
        assertFalse(RebuildTestSupport.containsBytes(bytes, "beta-original"),
                "la versión vieja de Beta sigue en el dex");
        assertTrue(RebuildTestSupport.containsBytes(bytes, "alpha-original"),
                "Alpha debía conservarse intacto");
    }

    @Test
    void fallaSiNingunaClaseSeIntegra() throws Exception {
        Path apk = apkConDosClases();
        Path dexNuevo = RebuildTestSupport.buildDex(
                RebuildTestSupport.compileSource(temp.resolve("x"), "Gamma.java", "class Gamma {}"),
                temp.resolve("work3"));

        Exception error = null;
        try {
            DexMerge.merge(apk, List.of(dexNuevo), Set.of("NoExiste"), temp);
        } catch (Exception e) {
            error = e;
        }

        assertTrue(error != null, "debió fallar sin clases integrables");
        assertTrue(error.getMessage().contains("Ninguna clase"));
    }

    @Test
    void listaDexDeRaizDelApk() throws Exception {
        Path apk = temp.resolve("multi.apk");
        RebuildTestSupport.apkWith(apk,
                "classes.dex", "uno",
                "classes2.dex", "dos",
                "lib/armeabi/classes.dex", "no-cuenta",
                "assets/classes.dex", "tampoco");

        List<String> names = DexMerge.rootDexEntries(apk);

        assertEquals(List.of("classes.dex", "classes2.dex"), names);
    }

    @Test
    void detectaVersionDeDex() throws Exception {
        Path dex035 = temp.resolve("v035.dex");
        byte[] head = "dex\n035\0".getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[112];
        System.arraycopy(head, 0, body, 0, 8);
        Files.write(dex035, body);

        Opcodes opcodes = DexMerge.opcodesFor(dex035);
        assertTrue(opcodes.api >= 21, "api esperada >= 21, fue " + opcodes.api);

        Path basura = temp.resolve("basura.dex");
        Files.write(basura, "esto no es un dex".getBytes(StandardCharsets.US_ASCII));
        assertEquals(Opcodes.getDefault().api, DexMerge.opcodesFor(basura).api);
    }
}
