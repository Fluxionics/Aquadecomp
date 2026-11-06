package com.aquadecomp.nativecode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ElfFixture {

    private ElfFixture() {
    }

    public static byte[] elf64() {
        return build(true);
    }

    public static byte[] elf32() {
        return build(false);
    }

    private static byte[] build(boolean bits64) {
        List<String> dynstrNames = List.of("libbar.so", "libfake.so",
                "Java_com_example_init", "someGlobalVar", "printf");
        ByteBuffer dynstr = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, Integer> dynstrOffsets = new LinkedHashMap<>();
        dynstr.put((byte) 0);
        for (String name : dynstrNames) {
            dynstrOffsets.put(name, dynstr.position());
            dynstr.put(name.getBytes(StandardCharsets.US_ASCII));
            dynstr.put((byte) 0);
        }
        int dynstrSize = dynstr.position();

        List<String> shNames = List.of(".shstrtab", ".dynstr", ".dynsym", ".dynamic");
        ByteBuffer shstr = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, Integer> shstrOffsets = new LinkedHashMap<>();
        shstr.put((byte) 0);
        for (String name : shNames) {
            shstrOffsets.put(name, shstr.position());
            shstr.put(name.getBytes(StandardCharsets.US_ASCII));
            shstr.put((byte) 0);
        }
        int shstrSize = shstr.position();

        int headerSize = bits64 ? 64 : 52;
        int shentsize = bits64 ? 64 : 40;
        int symSize = bits64 ? 24 : 16;
        int dynSize = bits64 ? 16 : 8;
        int sectionCount = 5;

        int headerTable = headerSize;
        int shstrOffset = headerTable + sectionCount * shentsize;
        int dynstrOffset = shstrOffset + shstrSize;
        int dynsymOffset = dynstrOffset + dynstrSize;
        int dynamicOffset = dynsymOffset + 4 * symSize;
        int total = dynamicOffset + 3 * dynSize;

        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(new byte[]{0x7F, 'E', 'L', 'F'});
        buf.put((byte) (bits64 ? 2 : 1));
        buf.put((byte) 1);
        buf.put((byte) 1);
        buf.put((byte) 0);
        buf.put(new byte[8]);
        buf.putShort((short) 3);
        buf.putShort((short) (bits64 ? 183 : 40));
        buf.putInt(1);
        if (bits64) {
            buf.putLong(0);
            buf.putLong(0);
            buf.putLong(headerTable);
            buf.putInt(0);
            buf.putShort((short) 64);
            buf.putShort((short) 0);
            buf.putShort((short) 0);
            buf.putShort((short) shentsize);
            buf.putShort((short) sectionCount);
            buf.putShort((short) 1);
        } else {
            buf.putInt(0);
            buf.putInt(0);
            buf.putInt(headerTable);
            buf.putInt(0);
            buf.putShort((short) 52);
            buf.putShort((short) 0);
            buf.putShort((short) 0);
            buf.putShort((short) shentsize);
            buf.putShort((short) sectionCount);
            buf.putShort((short) 1);
        }

        section(buf, bits64, 0, 0, 0, 0, 0, 0);
        section(buf, bits64, shstrOffsets.get(".shstrtab"), 3, shstrOffset, shstrSize, 0, 0);
        section(buf, bits64, shstrOffsets.get(".dynstr"), 3, dynstrOffset, dynstrSize, 0, 0);
        section(buf, bits64, shstrOffsets.get(".dynsym"), 11, dynsymOffset, 4 * symSize, 2,
                symSize);
        section(buf, bits64, shstrOffsets.get(".dynamic"), 6, dynamicOffset, 3 * dynSize, 2,
                dynSize);

        buf.position(shstrOffset);
        buf.put(shstr.array(), 0, shstrSize);
        buf.position(dynstrOffset);
        buf.put(dynstr.array(), 0, dynstrSize);

        buf.position(dynsymOffset);
        symbol(buf, bits64, 0, 0, 0, 0);
        symbol(buf, bits64, dynstrOffsets.get("Java_com_example_init"), 0x12, 3, 16);
        symbol(buf, bits64, dynstrOffsets.get("someGlobalVar"), 0x11, 3, 4);
        symbol(buf, bits64, dynstrOffsets.get("printf"), 0x12, 0, 0);

        buf.position(dynamicOffset);
        dynamic(buf, bits64, 1, dynstrOffsets.get("libbar.so"));
        dynamic(buf, bits64, 14, dynstrOffsets.get("libfake.so"));
        dynamic(buf, bits64, 0, 0);

        return buf.array();
    }

    private static void section(ByteBuffer buf, boolean bits64, int name, int type,
                                long offset, long size, int link, int entsize) {
        int start = buf.position();
        buf.putInt(name);
        buf.putInt(type);
        if (bits64) {
            buf.putLong(0);
            buf.putLong(0);
            buf.putLong(offset);
            buf.putLong(size);
            buf.putInt(link);
            buf.putInt(0);
            buf.putLong(1);
            buf.putLong(entsize);
        } else {
            buf.putInt(0);
            buf.putInt(0);
            buf.putInt((int) offset);
            buf.putInt((int) size);
            buf.putInt(link);
            buf.putInt(0);
            buf.putInt(1);
            buf.putInt(entsize);
        }
        int expected = bits64 ? 64 : 40;
        if (buf.position() - start != expected) {
            throw new IllegalStateException("Sección de tamaño incorrecto");
        }
    }

    private static void symbol(ByteBuffer buf, boolean bits64, int name, int info,
                               int shndx, int value) {
        if (bits64) {
            buf.putInt(name);
            buf.put((byte) info);
            buf.put((byte) 0);
            buf.putShort((short) shndx);
            buf.putLong(value);
            buf.putLong(0);
        } else {
            buf.putInt(name);
            buf.putInt(value);
            buf.putInt(0);
            buf.put((byte) info);
            buf.put((byte) 0);
            buf.putShort((short) shndx);
        }
    }

    private static void dynamic(ByteBuffer buf, boolean bits64, int tag, long value) {
        buf.putInt(tag);
        if (bits64) {
            buf.putInt(0);
            buf.putLong(value);
        } else {
            buf.putInt((int) value);
        }
    }

    public static List<String> sectionNames(ElfParser.ElfInfo elf) {
        List<String> names = new ArrayList<>();
        for (ElfParser.Section section : elf.sections()) {
            names.add(section.name());
        }
        return names;
    }
}
