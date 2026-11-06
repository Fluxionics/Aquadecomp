package com.aquadecomp.mapping;

import com.aquadecomp.rebuild.RebuildTestSupport;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DexMappingRewriterTest {

    @TempDir
    static Path temp;

    private static byte[] dexBytes;
    private static MappingFile mapping;

    @BeforeAll
    static void prepararFixture() throws Exception {
        Path classes = RebuildTestSupport.compileSource(temp.resolve("fixture"), "c.java", """
                package a.b;
                public class c {
                    public static int e = 5;
                    public static int a(int x) {
                        return x + e;
                    }
                    public static int d(int x) {
                        return a(x) + 1;
                    }
                }
                """);
        Path dex = RebuildTestSupport.buildDex(classes, temp.resolve("work"));
        dexBytes = Files.readAllBytes(dex);
        mapping = MappingFile.parse(List.of(
                "com.example.Foo -> a.b.c:",
                "    int contador -> e",
                "    int calcular(int) -> a"));
    }

    private static DexFile load(byte[] bytes) throws Exception {
        Path file = temp.resolve("carga-" + System.nanoTime() + ".dex");
        Files.write(file, bytes);
        try {
            return DexFileFactory.loadDexFile(file.toFile(), Opcodes.getDefault());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static ClassDef onlyClass(DexFile dex) {
        assertEquals(1, dex.getClasses().size());
        return dex.getClasses().iterator().next();
    }

    private static Method method(ClassDef classDef, String name) {
        for (Method method : classDef.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("No existe el metodo " + name + " en " + classDef.getType());
    }

    private static java.util.stream.Stream<? extends Method> methods(ClassDef classDef) {
        return java.util.stream.StreamSupport.stream(classDef.getMethods().spliterator(), false);
    }

    private static java.util.stream.Stream<? extends org.jf.dexlib2.iface.Field> fields(
            ClassDef classDef) {
        return java.util.stream.StreamSupport.stream(classDef.getFields().spliterator(), false);
    }

    private static Set<String> methodNames(ClassDef classDef) {
        return methods(classDef).map(Method::getName).collect(Collectors.toSet());
    }

    @Test
    void restauraClaseMiembrosYReferencias() throws Exception {
        MappingFile.RenameTable table =
                mapping.renameTable(MappingFile.Direction.REVERSE);

        DexMappingRewriter.Result result = DexMappingRewriter.rewrite(dexBytes, table);
        assertEquals(1, result.classes());
        assertTrue(result.members() >= 2, "miembros renombrados: " + result.members());

        ClassDef classDef = onlyClass(load(result.dex()));
        assertEquals("Lcom/example/Foo;", classDef.getType());

        Set<String> names = methodNames(classDef);
        assertTrue(names.contains("calcular"), "metodos: " + names);
        assertTrue(names.contains("d"), "metodos: " + names);
        assertFalse(names.contains("a"), "el nombre ofuscado sigue: " + names);

        boolean fieldRenamed = fields(classDef)
                .anyMatch(f -> f.getName().equals("contador") && f.getType().equals("I"));
        assertTrue(fieldRenamed, "el campo no se renombró");

        boolean callRenamed = false;
        for (Method method : classDef.getMethods()) {
            if (method.getImplementation() == null) {
                continue;
            }
            for (Instruction instruction : method.getImplementation().getInstructions()) {
                if (!(instruction instanceof ReferenceInstruction referenceInstruction)) {
                    continue;
                }
                if (referenceInstruction.getReference() instanceof MethodReference ref
                        && ref.getDefiningClass().equals("Lcom/example/Foo;")
                        && ref.getName().equals("calcular")) {
                    callRenamed = true;
                }
                if (referenceInstruction.getReference() instanceof FieldReference ref
                        && ref.getDefiningClass().equals("Lcom/example/Foo;")
                        && ref.getName().equals("contador")) {
                    callRenamed = true;
                }
            }
        }
        assertTrue(callRenamed, "las referencias en instrucciones no se renombraron");
    }

    @Test
    void idaYVueltaConservaElOriginal() throws Exception {
        MappingFile.RenameTable reverse = mapping.renameTable(MappingFile.Direction.REVERSE);
        MappingFile.RenameTable forward = mapping.renameTable(MappingFile.Direction.FORWARD);

        byte[] restaurado = DexMappingRewriter.rewrite(dexBytes, reverse).dex();
        byte[] vuelta = DexMappingRewriter.rewrite(restaurado, forward).dex();

        ClassDef classDef = onlyClass(load(vuelta));
        assertEquals("La/b/c;", classDef.getType());
        Set<String> names = methodNames(classDef);
        assertTrue(names.contains("a"), "metodos: " + names);
        assertFalse(names.contains("calcular"), "metodos: " + names);
        assertTrue(fields(classDef).anyMatch(f -> f.getName().equals("e")));
    }

    @Test
    void tablaVaciaNoCambiaNombres() throws Exception {
        MappingFile vacio = MappingFile.parse(List.of());
        DexMappingRewriter.Result result = DexMappingRewriter.rewrite(dexBytes,
                vacio.renameTable(MappingFile.Direction.REVERSE));

        assertEquals(0, result.classes());
        assertEquals(0, result.members());

        ClassDef classDef = onlyClass(load(result.dex()));
        assertEquals("La/b/c;", classDef.getType());
        assertTrue(methodNames(classDef).contains("a"));
    }

    @Test
    void elDexConservaSuVersion() throws Exception {
        MappingFile.RenameTable table = mapping.renameTable(MappingFile.Direction.REVERSE);
        DexMappingRewriter.Result result = DexMappingRewriter.rewrite(dexBytes, table);

        String versionEntrada = new String(dexBytes, 4, 3,
                java.nio.charset.StandardCharsets.US_ASCII);
        String versionSalida = new String(result.dex(), 4, 3,
                java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(versionEntrada, versionSalida);
        assertEquals("dex\n", new String(result.dex(), 0, 4,
                java.nio.charset.StandardCharsets.US_ASCII));
    }
}
