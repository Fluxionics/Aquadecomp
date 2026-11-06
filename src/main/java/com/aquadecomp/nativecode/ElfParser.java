package com.aquadecomp.nativecode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ElfParser {

    public record Section(String name, long size) {
    }

    public record ElfInfo(int bits, boolean littleEndian, int machine,
                          String architecture, String abi, long entryPoint,
                          boolean stripped, List<Section> sections,
                          List<String> needed, String soname,
                          List<String> exports, int imports, int symbols) {
    }

    private static final int SHT_STRTAB = 3;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_DYNSYM = 11;
    private static final int DT_NULL = 0;
    private static final int DT_NEEDED = 1;
    private static final int DT_SONAME = 14;

    private final byte[] data;
    private final boolean little;
    private final boolean is64;

    private ElfParser(byte[] data, boolean little, boolean is64) {
        this.data = data;
        this.little = little;
        this.is64 = is64;
    }

    public static ElfInfo parse(byte[] data) throws IOException {
        if (data.length < 16 || (data[0] & 0xFF) != 0x7F || data[1] != 'E'
                || data[2] != 'L' || data[3] != 'F') {
            throw new IOException("No es un archivo ELF vÃ¡lido (falta la firma 7F 45 4C 46)");
        }
        int bits = data[4] & 0xFF;
        int encoding = data[5] & 0xFF;
        if (bits != 1 && bits != 2) {
            throw new IOException("Clase ELF desconocida: " + bits
                    + " (se esperaba 1 para 32 bits o 2 para 64 bits)");
        }
        if (encoding != 1 && encoding != 2) {
            throw new IOException("CodificaciÃ³n ELF desconocida: " + encoding);
        }
        return new ElfParser(data, encoding == 1, bits == 2).read();
    }

    private ElfInfo read() throws IOException {
        int machine = u2(18);
        long entry = is64 ? u8(24) : u4(24);
        long shoff = is64 ? u8(40) : u4(32);
        int shentsize = u2(is64 ? 58 : 46);
        int shnum = u2(is64 ? 60 : 48);
        int shstrndx = u2(is64 ? 62 : 50);

        List<Section> sections = new ArrayList<>();
        int[] sectionTypes = new int[shnum];
        long[] sectionOffsets = new long[shnum];
        long[] sectionSizes = new long[shnum];
        int[] sectionLinks = new int[shnum];

        if (shoff > 0 && shnum > 0) {
            int expected = is64 ? 64 : 40;
            if (shentsize < expected) {
                throw new IOException("TamaÃ±o de secciÃ³n ELF invÃ¡lido: " + shentsize);
            }
            if (shoff > Integer.MAX_VALUE || shnum > 4096) {
                throw new IOException("Tabla de secciones fuera de rango");
            }
            for (int i = 0; i < shnum; i++) {
                int base = (int) shoff + i * shentsize;
                if (is64) {
                    sectionTypes[i] = (int) u4(base + 4);
                    sectionOffsets[i] = u8(base + 24);
                    sectionSizes[i] = u8(base + 32);
                    sectionLinks[i] = (int) u4(base + 40);
                } else {
                    sectionTypes[i] = (int) u4(base + 4);
                    sectionOffsets[i] = u4(base + 16);
                    sectionSizes[i] = u4(base + 20);
                    sectionLinks[i] = (int) u4(base + 24);
                }
            }
            byte[] names = stringTable(shstrndx, sectionTypes, sectionOffsets,
                    sectionSizes, sectionLinks);
            for (int i = 0; i < shnum; i++) {
                String name = stringAt(names, (int) u4((int) shoff + i * shentsize));
                sections.add(new Section(name, sectionSizes[i]));
            }
        }

        int dynsymIndex = indexOf(sections, ".dynsym");
        int symtabIndex = indexOf(sections, ".symtab");
        int dynstrIndex = indexOf(sections, ".dynstr");
        if (dynsymIndex >= 0) {
            int link = sectionLinks[dynsymIndex];
            if (link >= 0 && link < shnum && sectionTypes[link] == SHT_STRTAB) {
                dynstrIndex = link;
            }
        }

        byte[] dynstr = dynstrIndex >= 0
                ? slice(sectionOffsets[dynstrIndex], sectionSizes[dynstrIndex])
                : new byte[0];

        List<String> exports = new ArrayList<>();
        int imports = 0;
        int symbols = 0;
        if (dynsymIndex >= 0 && dynstrIndex >= 0) {
            long offset = sectionOffsets[dynsymIndex];
            long size = sectionSizes[dynsymIndex];
            int entsize = is64 ? 24 : 16;
            if (offset <= Integer.MAX_VALUE && size <= Integer.MAX_VALUE) {
                for (long pos = 0; pos + entsize <= size; pos += entsize) {
                    int base = (int) offset + (int) pos;
                    int nameOffset = (int) u4(base);
                    int info = is64 ? u1(base + 4) : u1(base + 12);
                    int shndx = is64 ? u2(base + 6) : u2(base + 14);
                    String name = stringAt(dynstr, nameOffset);
                    if (name.isEmpty()) {
                        continue;
                    }
                    symbols++;
                    if (shndx == 0) {
                        imports++;
                        continue;
                    }
                    int bind = info >>> 4;
                    int type = info & 0x0F;
                    if ((bind == 1 || bind == 2) && (type == 1 || type == 2)) {
                        exports.add(name);
                    }
                }
            }
        }

        List<String> needed = new ArrayList<>();
        String soname = null;
        int dynamicIndex = indexOf(sections, ".dynamic");
        if (dynamicIndex >= 0 && dynstr.length > 0) {
            long offset = sectionOffsets[dynamicIndex];
            long size = sectionSizes[dynamicIndex];
            int entsize = is64 ? 16 : 8;
            if (offset <= Integer.MAX_VALUE && size <= Integer.MAX_VALUE) {
                for (long pos = 0; pos + entsize <= size; pos += entsize) {
                    int base = (int) offset + (int) pos;
                    int tag = (int) u4(base);
                    if (tag == DT_NULL) {
                        break;
                    }
                    long value = is64 ? u8(base + 8) : u4(base + 4);
                    if (tag == DT_NEEDED) {
                        String name = stringAt(dynstr, (int) value);
                        if (!name.isEmpty()) {
                            needed.add(name);
                        }
                    } else if (tag == DT_SONAME) {
                        soname = stringAt(dynstr, (int) value);
                    }
                }
            }
        }

        boolean stripped = shnum > 0 && symtabIndex < 0;

        return new ElfInfo(is64 ? 64 : 32, little, machine,
                architecture(machine), abi(machine), entry, stripped,
                sections, needed, soname, exports, imports, symbols);
    }

    private byte[] stringTable(int index, int[] types, long[] offsets, long[] sizes,
                               int[] links) throws IOException {
        if (index == 0xFFFF && types.length > 0) {
            index = links[0];
        }
        if (index < 0 || index >= types.length) {
            return new byte[0];
        }
        if (types[index] != SHT_STRTAB) {
            return new byte[0];
        }
        return slice(offsets[index], sizes[index]);
    }

    private byte[] slice(long offset, long size) throws IOException {
        if (offset < 0 || size < 0 || offset > Integer.MAX_VALUE || size > Integer.MAX_VALUE
                || offset + size > data.length) {
            throw new IOException("La tabla de sÃ­mbolos estÃ¡ fuera del archivo ELF"
                    + " (archivo truncado o daÃ±ado)");
        }
        byte[] out = new byte[(int) size];
        System.arraycopy(data, (int) offset, out, 0, (int) size);
        return out;
    }

    private static int indexOf(List<Section> sections, String name) {
        for (int i = 0; i < sections.size(); i++) {
            if (name.equals(sections.get(i).name())) {
                return i;
            }
        }
        return -1;
    }

    private static String stringAt(byte[] table, int offset) {
        if (offset < 0 || offset >= table.length) {
            return "";
        }
        int end = offset;
        while (end < table.length && table[end] != 0) {
            end++;
        }
        return new String(table, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String architecture(int machine) {
        return switch (machine) {
            case 3 -> "x86";
            case 40 -> "arm";
            case 62 -> "x86_64";
            case 183 -> "aarch64";
            case 243 -> "riscv";
            case 8 -> "mips";
            default -> "desconocida (e_machine=" + machine + ")";
        };
    }

    private static String abi(int machine) {
        return switch (machine) {
            case 3 -> "x86";
            case 40 -> "armeabi-v7a";
            case 62 -> "x86_64";
            case 183 -> "arm64-v8a";
            default -> "desconocida";
        };
    }

    private int u1(int offset) throws IOException {
        check(offset, 1);
        return data[offset] & 0xFF;
    }

    private int u2(int offset) throws IOException {
        check(offset, 2);
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return little ? b0 | (b1 << 8) : (b0 << 8) | b1;
    }

    private long u4(int offset) throws IOException {
        check(offset, 4);
        long b0 = data[offset] & 0xFFL;
        long b1 = data[offset + 1] & 0xFFL;
        long b2 = data[offset + 2] & 0xFFL;
        long b3 = data[offset + 3] & 0xFFL;
        return little ? b0 | (b1 << 8) | (b2 << 16) | (b3 << 24)
                : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private long u8(int offset) throws IOException {
        long first = u4(offset);
        long second = u4(offset + 4);
        long high = little ? second : first;
        long low = little ? first : second;
        return (high << 32) | (low & 0xFFFFFFFFL);
    }

    private void check(int offset, int length) throws IOException {
        if (offset < 0 || offset + length > data.length) {
            throw new IOException("Cabecera ELF truncada: se necesitan "
                    + (offset + length) + " bytes y el archivo tiene " + data.length);
        }
    }
}
