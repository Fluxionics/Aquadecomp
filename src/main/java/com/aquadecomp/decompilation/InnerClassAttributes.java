package com.aquadecomp.decompilation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class InnerClassAttributes {

    private static final String ATRIBUTO = "InnerClasses";

    private InnerClassAttributes() {
    }

    public static void add(Path jar) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var e = zip.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (var in = zip.getInputStream(entry)) {
                    entradas.put(entry.getName(), in.readAllBytes());
                }
            }
        }

        Map<String, List<Registro>> porArchivo = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : entradas.entrySet()) {
            String nombre = e.getKey();
            if (!nombre.endsWith(".class")) {
                continue;
            }
            String interno = nombre.substring(0, nombre.length() - ".class".length());
            int separador = interno.lastIndexOf('$');
            if (separador <= 0) {
                continue;
            }
            String outer = interno.substring(0, separador);
            while (outer.indexOf('$') > 0 && !entradas.containsKey(outer + ".class")) {
                outer = outer.substring(0, outer.lastIndexOf('$'));
            }
            if (!entradas.containsKey(outer + ".class")) {
                continue;
            }
            String simple = interno.substring(interno.lastIndexOf('$') + 1);
            Registro registro = new Registro(interno, outer, simple,
                    accessFlags(e.getValue()));
            porArchivo.computeIfAbsent(nombre, k -> new ArrayList<>()).add(registro);
            porArchivo.computeIfAbsent(outer + ".class", k -> new ArrayList<>()).add(registro);
        }
        if (porArchivo.isEmpty()) {
            return;
        }

        Path temporal = jar.resolveSibling(jar.getFileName() + ".tmp");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporal))) {
            for (Map.Entry<String, byte[]> e : entradas.entrySet()) {
                byte[] datos = e.getValue();
                List<Registro> registros = porArchivo.get(e.getKey());
                if (registros != null) {
                    datos = inyectar(datos, registros);
                }
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(datos);
                out.closeEntry();
            }
        }
        Files.move(temporal, jar, StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] inyectar(byte[] cf, List<Registro> registros) throws IOException {
        Cabecera cabecera = parsear(cf);
        if (cabecera.tieneAtributo) {
            return cf;
        }

        EstadoCp cp = new EstadoCp(cabecera);
        int nombreAtributo = cp.utf8(ATRIBUTO);
        List<int[]> filas = new ArrayList<>();
        for (Registro r : registros) {
            filas.add(new int[]{cp.clase(r.inner), cp.clase(r.outer),
                    cp.utf8(r.simple), r.flags});
        }

        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream(2 + filas.size() * 8);
        escribirU2(cuerpo, filas.size());
        for (int[] fila : filas) {
            escribirU2(cuerpo, fila[0]);
            escribirU2(cuerpo, fila[1]);
            escribirU2(cuerpo, fila[2]);
            escribirU2(cuerpo, fila[3]);
        }

        int attrsCount = u2(cf, cabecera.attrsOffset) + 1;
        ByteArrayOutputStream salida = new ByteArrayOutputStream(cf.length
                + cp.datos.size() + filas.size() * 8 + 10);
        salida.write(cf, 0, 8);
        escribirU2(salida, cp.siguiente);
        salida.write(cf, 10, cabecera.cpEnd - 10);
        salida.write(cp.datos.toByteArray());
        salida.write(cf, cabecera.cpEnd, cabecera.attrsOffset - cabecera.cpEnd);
        escribirU2(salida, attrsCount);
        salida.write(cf, cabecera.attrsOffset + 2, cf.length - cabecera.attrsOffset - 2);
        escribirU2(salida, nombreAtributo);
        escribirU4(salida, 2 + filas.size() * 8);
        salida.write(cuerpo.toByteArray());
        return salida.toByteArray();
    }

    private static int accessFlags(byte[] cf) throws IOException {
        return u2(cf, hallarCpEnd(cf));
    }

    private static int hallarCpEnd(byte[] cf) throws IOException {
        int cpCount = u2(cf, 8);
        int pos = 10;
        int idx = 1;
        while (idx < cpCount) {
            if (pos + 1 >= cf.length) {
                throw new IOException("Constant pool truncada en el archivo de clases");
            }
            int tag = cf[pos] & 0xFF;
            int fin;
            switch (tag) {
                case 1:
                    fin = pos + 3 + u2(cf, pos + 1);
                    break;
                case 5:
                case 6:
                    fin = pos + 9;
                    break;
                case 15:
                    fin = pos + 4;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    fin = pos + 3;
                    break;
                default:
                    fin = pos + 5;
                    break;
            }
            if (fin > cf.length) {
                throw new IOException("Entrada de constant pool fuera de rango");
            }
            if (tag == 5 || tag == 6) {
                idx += 2;
            } else {
                idx++;
            }
            pos = fin;
        }
        return pos;
    }

    private static Cabecera parsear(byte[] cf) throws IOException {
        int cpCount = u2(cf, 8);
        int[] inicio = new int[cpCount];
        byte[] tags = new byte[cpCount];
        int pos = 10;
        int idx = 1;
        while (idx < cpCount) {
            if (pos + 1 >= cf.length) {
                throw new IOException("Constant pool truncada en el archivo de clases");
            }
            int tag = cf[pos] & 0xFF;
            int fin;
            switch (tag) {
                case 1:
                    fin = pos + 3 + u2(cf, pos + 1);
                    break;
                case 5:
                case 6:
                    fin = pos + 9;
                    break;
                case 15:
                    fin = pos + 4;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    fin = pos + 3;
                    break;
                default:
                    fin = pos + 5;
                    break;
            }
            if (fin > cf.length) {
                throw new IOException("Entrada de constant pool fuera de rango");
            }
            inicio[idx] = pos;
            tags[idx] = (byte) tag;
            if (tag == 5 || tag == 6) {
                inicio[idx + 1] = -1;
                tags[idx + 1] = -1;
                idx += 2;
            } else {
                idx++;
            }
            pos = fin;
        }
        int cpEnd = pos;

        Map<String, Integer> utf8 = new LinkedHashMap<>();
        Map<Integer, String> porIndice = new LinkedHashMap<>();
        Map<String, Integer> clases = new LinkedHashMap<>();
        for (int i = 1; i < cpCount; i++) {
            if (inicio[i] < 0) {
                continue;
            }
            if (tags[i] == 1) {
                int largo = u2(cf, inicio[i] + 1);
                String texto = new String(cf, inicio[i] + 3, largo, StandardCharsets.UTF_8);
                utf8.put(texto, i);
                porIndice.put(i, texto);
            } else if (tags[i] == 7) {
                int ref = u2(cf, inicio[i] + 1);
                if (ref > 0 && inicio[ref] >= 0 && tags[ref] == 1) {
                    int largo = u2(cf, inicio[ref] + 1);
                    clases.put(new String(cf, inicio[ref] + 3, largo,
                            StandardCharsets.UTF_8), i);
                }
            }
        }

        int attrsOffset = recorrerEstructura(cf, cpEnd);
        boolean tiene = false;
        int attrsCount = u2(cf, attrsOffset);
        int p = attrsOffset + 2;
        for (int i = 0; i < attrsCount; i++) {
            if (p + 6 > cf.length) {
                throw new IOException("Atributos truncados en el archivo de clases");
            }
            int nombre = u2(cf, p);
            int largo = (int) u4(cf, p + 2);
            if (ATRIBUTO.equals(porIndice.get(nombre))) {
                tiene = true;
            }
            p += 6 + largo;
        }
        return new Cabecera(cpEnd, attrsOffset, tiene, utf8, clases, cpCount);
    }

    private static int recorrerEstructura(byte[] cf, int cpEnd) throws IOException {
        int p = cpEnd + 6;
        if (p + 2 > cf.length) {
            throw new IOException("Estructura de clases truncada");
        }
        int interfaces = u2(cf, p);
        p += 2 + interfaces * 2;
        int campos = u2(cf, p);
        p += 2;
        for (int i = 0; i < campos; i++) {
            p += 6;
            int attrs = u2(cf, p);
            p += 2;
            for (int j = 0; j < attrs; j++) {
                p += 6 + (int) u4(cf, p + 2);
            }
        }
        int metodos = u2(cf, p);
        p += 2;
        for (int i = 0; i < metodos; i++) {
            p += 6;
            int attrs = u2(cf, p);
            p += 2;
            for (int j = 0; j < attrs; j++) {
                p += 6 + (int) u4(cf, p + 2);
            }
        }
        if (p + 2 > cf.length) {
            throw new IOException("Estructura de clases truncada");
        }
        return p;
    }

    private static int u2(byte[] cf, int offset) {
        if (offset + 2 > cf.length) {
            throw new IndexOutOfBoundsException("u2 fuera de rango: " + offset);
        }
        return ((cf[offset] & 0xFF) << 8) | (cf[offset + 1] & 0xFF);
    }

    private static long u4(byte[] cf, int offset) {
        if (offset + 4 > cf.length) {
            throw new IndexOutOfBoundsException("u4 fuera de rango: " + offset);
        }
        return ((long) (cf[offset] & 0xFF) << 24) | ((cf[offset + 1] & 0xFF) << 16)
                | ((cf[offset + 2] & 0xFF) << 8) | (cf[offset + 3] & 0xFF);
    }

    private static void escribirU2(ByteArrayOutputStream out, int valor) {
        out.write((valor >> 8) & 0xFF);
        out.write(valor & 0xFF);
    }

    private static void escribirU4(ByteArrayOutputStream out, int valor) {
        out.write((valor >> 24) & 0xFF);
        out.write((valor >> 16) & 0xFF);
        out.write((valor >> 8) & 0xFF);
        out.write(valor & 0xFF);
    }

    private static final class EstadoCp {
        int siguiente;
        final Map<String, Integer> existentesUtf8;
        final Map<String, Integer> existentesClases;
        final Map<String, Integer> nuevosUtf8 = new LinkedHashMap<>();
        final Map<String, Integer> nuevosClases = new LinkedHashMap<>();
        final ByteArrayOutputStream datos = new ByteArrayOutputStream();

        EstadoCp(Cabecera cabecera) {
            this.siguiente = cabecera.cpCount;
            this.existentesUtf8 = cabecera.utf8;
            this.existentesClases = cabecera.clases;
        }

        int utf8(String texto) {
            Integer existente = existentesUtf8.get(texto);
            if (existente == null) {
                existente = nuevosUtf8.get(texto);
            }
            if (existente != null) {
                return existente;
            }
            int indice = siguiente++;
            byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
            datos.write(1);
            escribirU2(datos, bytes.length);
            datos.write(bytes, 0, bytes.length);
            nuevosUtf8.put(texto, indice);
            return indice;
        }

        int clase(String interno) {
            Integer existente = existentesClases.get(interno);
            if (existente == null) {
                existente = nuevosClases.get(interno);
            }
            if (existente != null) {
                return existente;
            }
            int nombre = utf8(interno);
            int indice = siguiente++;
            datos.write(7);
            escribirU2(datos, nombre);
            nuevosClases.put(interno, indice);
            return indice;
        }
    }

    private static final class Registro {
        final String inner;
        final String outer;
        final String simple;
        final int flags;

        Registro(String inner, String outer, String simple, int flags) {
            this.inner = inner;
            this.outer = outer;
            this.simple = simple;
            this.flags = flags;
        }
    }

    private static final class Cabecera {
        final int cpEnd;
        final int attrsOffset;
        final boolean tieneAtributo;
        final Map<String, Integer> utf8;
        final Map<String, Integer> clases;
        final int cpCount;

        Cabecera(int cpEnd, int attrsOffset, boolean tieneAtributo,
                 Map<String, Integer> utf8, Map<String, Integer> clases, int cpCount) {
            this.cpEnd = cpEnd;
            this.attrsOffset = attrsOffset;
            this.tieneAtributo = tieneAtributo;
            this.utf8 = utf8;
            this.clases = clases;
            this.cpCount = cpCount;
        }
    }
}
