package com.aquadecomp.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingFileTest {

    @TempDir
    Path temp;

    private static final List<String> LINES = List.of(
            "# comentario de prueba",
            "    miembro antes de cualquier clase -> z",
            "com.example.Foo -> a.b.c:",
            "    int contador -> e",
            "    java.lang.String etiqueta -> f",
            "    int[] datos -> g",
            "    1:5:void calcular(int,java.lang.String) -> a",
            "    6:6:void ejecutar():10:10 -> b",
            "    void mezclar(a.b.d) -> h",
            "com.example.Bar -> a.b.d:",
            "    int doble(int) -> g",
            "com.example.Foo$Inner -> a.b.c$i:",
            "    com.example.Bar referencia -> j",
            "linea sin formato");

    @Test
    void cuentaClasesYMiembros() {
        MappingFile mapping = MappingFile.parse(LINES);

        assertEquals(3, mapping.classCount());
        assertEquals(8, mapping.memberCount());
    }

    @Test
    void tablaReversaRestauraNombres() {
        MappingFile.RenameTable table = MappingFile.parse(LINES)
                .renameTable(MappingFile.Direction.REVERSE);

        assertEquals("Lcom/example/Foo;", table.types().get("La/b/c;"));
        assertEquals("Lcom/example/Bar;", table.types().get("La/b/d;"));
        assertEquals("Lcom/example/Foo$Inner;", table.types().get("La/b/c$i;"));

        assertEquals("calcular", table.methodName("La/b/c;", "a",
                List.of("I", "Ljava/lang/String;"), "V"));
        assertEquals("ejecutar", table.methodName("La/b/c;", "b", List.of(), "V"));
        assertEquals("doble", table.methodName("La/b/d;", "g", List.of("I"), "I"));
        assertEquals("mezclar", table.methodName("La/b/c;", "h", List.of("La/b/d;"), "V"));

        assertEquals("contador", table.fieldName("La/b/c;", "e", "I"));
        assertEquals("etiqueta", table.fieldName("La/b/c;", "f", "Ljava/lang/String;"));
        assertEquals("datos", table.fieldName("La/b/c;", "g", "[I"));
        assertEquals("referencia", table.fieldName("La/b/c$i;", "j", "La/b/d;"));
    }

    @Test
    void tablaDirectaInvierteLosNombres() {
        MappingFile.RenameTable table = MappingFile.parse(LINES)
                .renameTable(MappingFile.Direction.FORWARD);

        assertEquals("La/b/c;", table.types().get("Lcom/example/Foo;"));
        assertEquals("La/b/d;", table.types().get("Lcom/example/Bar;"));

        assertEquals("a", table.methodName("Lcom/example/Foo;", "calcular",
                List.of("I", "Ljava/lang/String;"), "V"));
        assertEquals("h", table.methodName("Lcom/example/Foo;", "mezclar",
                List.of("Lcom/example/Bar;"), "V"));
        assertEquals("g", table.methodName("Lcom/example/Bar;", "doble", List.of("I"), "I"));

        assertEquals("e", table.fieldName("Lcom/example/Foo;", "contador", "I"));
        assertEquals("j", table.fieldName("Lcom/example/Foo$Inner;", "referencia",
                "Lcom/example/Bar;"));
    }

    @Test
    void conservaNombresDesconocidos() {
        MappingFile.RenameTable table = MappingFile.parse(LINES)
                .renameTable(MappingFile.Direction.REVERSE);

        assertEquals("x", table.methodName("Lotra/Clase;", "x", List.of(), "V"));
        assertEquals("y", table.fieldName("Lotra/Clase;", "y", "I"));
        assertNull(table.types().get("Lotra/Clase;"));
    }

    @Test
    void desambiguaMetodosConMismoNombreObfuscado() {
        MappingFile mapping = MappingFile.parse(List.of(
                "com.x.Y -> z.y:",
                "    void foo(int) -> m",
                "    void bar(java.lang.String) -> m"));

        MappingFile.RenameTable table = mapping.renameTable(MappingFile.Direction.REVERSE);

        assertEquals("foo", table.methodName("Lz/y;", "m", List.of("I"), "V"));
        assertEquals("bar", table.methodName("Lz/y;", "m",
                List.of("Ljava/lang/String;"), "V"));
    }

    @Test
    void conflictoIrresolubleConservaElNombreObfuscado() {
        MappingFile mapping = MappingFile.parse(List.of(
                "com.x.Y -> z.y:",
                "    void a(int) -> m",
                "    void b(int) -> m"));

        MappingFile.RenameTable table = mapping.renameTable(MappingFile.Direction.REVERSE);

        assertEquals("m", table.methodName("Lz/y;", "m", List.of("I"), "V"));
    }

    @Test
    void lineasMalformadasSeIgnoran() {
        MappingFile mapping = MappingFile.parse(List.of(
                "",
                "sin flecha",
                "   ",
                "# otro comentario",
                "com.example.Ok -> a.b:",
                "    campo sin tipo -> "));
        assertEquals(1, mapping.classCount());
        assertEquals(0, mapping.memberCount());
    }

    @Test
    void archivoVacioFallaConMensaje() throws Exception {
        Path vacio = temp.resolve("vacio.txt");
        Files.writeString(vacio, "# solo comentarios", StandardCharsets.UTF_8);

        java.io.IOException error = assertThrows(java.io.IOException.class,
                () -> MappingFile.parse(vacio));

        assertTrue(error.getMessage().contains("entradas de mapping"),
                "mensaje inesperado: " + error.getMessage());
    }
}
