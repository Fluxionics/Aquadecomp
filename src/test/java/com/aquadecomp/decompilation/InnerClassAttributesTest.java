package com.aquadecomp.decompilation;

import com.aquadecomp.rebuild.SourceCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InnerClassAttributesTest {

    @TempDir
    Path temp;

    @Test
    void anadeInnerClassesYjavacResuelveElTipoAnidado() throws Exception {
        Path jar = temp.resolve("fabricado.jar");
        escribirJar(jar, Map.of(
                "Exterior.class", claseVacia("Exterior", 0x0021),
                "Exterior$Nivel.class", claseVacia("Exterior$Nivel", 0x0019)));
        assertFalse(contiene(jar, "Exterior.class", "InnerClasses"),
                "el jar de prueba ya traía el atributo");

        InnerClassAttributes.add(jar);

        assertTrue(contiene(jar, "Exterior.class", "InnerClasses"),
                "el atributo InnerClasses no se añadió");
        assertTrue(contiene(jar, "Exterior$Nivel.class", "InnerClasses"),
                "la inner class no recibió su entrada");

        Path src = temp.resolve("Consumidor.java");
        Files.writeString(src, "class Consumidor { Exterior.Nivel nivel; }\n",
                StandardCharsets.UTF_8);
        SourceCompiler.Result r = SourceCompiler.compile(List.of(src),
                temp.resolve("salida"), List.of(jar));
        assertTrue(r.ok(), String.join("\n", r.errors()));
    }

    @Test
    void noDuplicaElAtributoYaExistente() throws Exception {
        Path clases = temp.resolve("javac");
        Files.createDirectories(clases);
        Path src = temp.resolve("Exterior.java");
        Files.writeString(src, """
                public class Exterior {
                    public static class Nivel {
                    }
                }
                """, StandardCharsets.UTF_8);
        SourceCompiler.Result compilado =
                SourceCompiler.compile(List.of(src), clases, List.of());
        assertTrue(compilado.ok(), String.join("\n", compilado.errors()));

        Path jar = temp.resolve("javac.jar");
        empaquetar(jar, clases);
        assertTrue(contiene(jar, "Exterior.class", "InnerClasses"),
                "javac debía generar el atributo InnerClasses");
        byte[] exteriorAntes = leerEntrada(jar, "Exterior.class");
        byte[] nivelAntes = leerEntrada(jar, "Exterior$Nivel.class");

        InnerClassAttributes.add(jar);

        assertTrue(java.util.Arrays.equals(exteriorAntes, leerEntrada(jar, "Exterior.class")),
                "Exterior.class fue modificado pese a tener ya el atributo");
        assertTrue(java.util.Arrays.equals(nivelAntes, leerEntrada(jar, "Exterior$Nivel.class")),
                "Exterior$Nivel.class fue modificado pese a tener ya el atributo");
        Path consumidor = temp.resolve("Consumidor2.java");
        Files.writeString(consumidor, "class Consumidor2 { Exterior.Nivel nivel; }\n",
                StandardCharsets.UTF_8);
        SourceCompiler.Result r = SourceCompiler.compile(List.of(consumidor),
                temp.resolve("salida2"), List.of(jar));
        assertTrue(r.ok(), String.join("\n", r.errors()));
    }

    @Test
    void dejaIntactosLosArchivosSinAnidados() throws Exception {
        Path clases = temp.resolve("sola");
        Files.createDirectories(clases);
        Path src = temp.resolve("Sola.java");
        Files.writeString(src, "public class Sola { }\n", StandardCharsets.UTF_8);
        SourceCompiler.Result compilado =
                SourceCompiler.compile(List.of(src), clases, List.of());
        assertTrue(compilado.ok(), String.join("\n", compilado.errors()));

        Path jar = temp.resolve("sola.jar");
        empaquetar(jar, clases);
        byte[] antes = leerEntrada(jar, "Sola.class");

        InnerClassAttributes.add(jar);

        byte[] despues = leerEntrada(jar, "Sola.class");
        assertTrue(java.util.Arrays.equals(antes, despues),
                "un archivo sin anidados fue modificado");
    }

    private static byte[] claseVacia(String nombre, int flags) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(61);
        out.writeShort(5);
        out.writeByte(1);
        out.writeUTF("java/lang/Object");
        out.writeByte(7);
        out.writeShort(1);
        out.writeByte(1);
        out.writeUTF(nombre);
        out.writeByte(7);
        out.writeShort(3);
        out.writeShort(flags);
        out.writeShort(4);
        out.writeShort(2);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(0);
        out.flush();
        return bos.toByteArray();
    }

    private static void escribirJar(Path jar, Map<String, byte[]> entradas)
            throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> e : entradas.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
    }

    private static void empaquetar(Path jar, Path dirClases) throws IOException {
        Map<String, byte[]> entradas = new java.util.LinkedHashMap<>();
        try (var walk = Files.walk(dirClases)) {
            List<Path> ficheros = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class")).toList();
            for (Path fichero : ficheros) {
                String nombre = dirClases.relativize(fichero).toString().replace('\\', '/');
                entradas.put(nombre, Files.readAllBytes(fichero));
            }
        }
        escribirJar(jar, entradas);
    }

    private static byte[] leerEntrada(Path jar, String nombre) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(nombre);
            assertTrue(entry != null, "falta la entrada " + nombre);
            return zip.getInputStream(entry).readAllBytes();
        }
    }

    private static boolean contiene(Path jar, String nombre, String texto)
            throws IOException {
        byte[] datos = leerEntrada(jar, nombre);
        return new String(datos, StandardCharsets.ISO_8859_1).contains(texto);
    }
}
