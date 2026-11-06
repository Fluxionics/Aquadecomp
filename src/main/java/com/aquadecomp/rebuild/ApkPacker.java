package com.aquadecomp.rebuild;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ApkPacker {

    public record Replacement(String name, byte[] data) {
    }

    private static final int ALIGNMENT = 4096;

    private ApkPacker() {
    }

    public static void repack(Path originalApk, Path outputApk, List<Replacement> newDexEntries)
            throws IOException {
        CountingOutputStream counter = new CountingOutputStream(
                Files.newOutputStream(outputApk));
        try (counter; ZipOutputStream zip = new ZipOutputStream(counter)) {
            zip.setLevel(6);
            try (ZipFile in = new ZipFile(originalApk.toFile())) {
                Enumeration<? extends ZipEntry> entries = in.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || isRootDex(name) || isSignatureFile(name)) {
                        continue;
                    }
                    byte[] data;
                    try (InputStream stream = in.getInputStream(entry)) {
                        data = stream.readAllBytes();
                    }
                    writeEntry(zip, counter, name, entry.getMethod() == ZipEntry.STORED,
                            entry.getTime(), data);
                }
            }
            for (Replacement replacement : newDexEntries) {
                writeEntry(zip, counter, replacement.name(), true,
                        System.currentTimeMillis(), replacement.data());
            }
        }
    }

    public static boolean isRootDex(String name) {
        return name.equals("classes.dex") || name.matches("classes\\d+\\.dex");
    }

    static boolean isSignatureFile(String name) {
        if (name.length() < "META-INF/".length()
                || !name.regionMatches(true, 0, "META-INF/", 0, "META-INF/".length())) {
            return false;
        }
        String rest = name.substring("META-INF/".length());
        if (rest.contains("/")) {
            return false;
        }
        String upper = rest.toUpperCase(Locale.ROOT);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")
                || upper.endsWith(".EC") || upper.equals("MANIFEST.MF");
    }

    private static void writeEntry(ZipOutputStream zip, CountingOutputStream counter,
                                   String name, boolean stored, long time, byte[] data)
            throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(name);
        if (time >= 0) {
            entry.setTime(time);
        }

        if (stored) {
            CRC32 crc = new CRC32();
            crc.update(data);
            byte[] extra = alignedExtra(counter.written, nameBytes.length, entryExtra(entry));
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            entry.setCrc(crc.getValue());
            entry.setExtra(extra);
        }
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static byte[] entryExtra(ZipEntry entry) {
        byte[] existing = entry.getExtra();
        return existing != null ? existing : new byte[0];
    }

    private static byte[] alignedExtra(long currentOffset, int nameLength, byte[] existing)
            throws IOException {
        long headerBase = currentOffset + 30 + nameLength + existing.length;
        long missing = (ALIGNMENT - (headerBase % ALIGNMENT)) % ALIGNMENT;
        long subfieldLength = missing - 4;
        if (subfieldLength < 0) {
            subfieldLength += ALIGNMENT;
        }
        if (existing.length + 4 + subfieldLength > 0xFFFF) {
            return existing;
        }
        byte[] padded = new byte[(int) (existing.length + 4 + subfieldLength)];
        System.arraycopy(existing, 0, padded, 0, existing.length);
        int offset = existing.length;
        padded[offset] = 0;
        padded[offset + 1] = 0;
        padded[offset + 2] = (byte) (subfieldLength & 0xFF);
        padded[offset + 3] = (byte) ((subfieldLength >> 8) & 0xFF);
        return padded;
    }

    public static List<String> dexNames(Path apk) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (isRootDex(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static final class CountingOutputStream extends OutputStream {

        private final OutputStream delegate;
        private long written;

        private CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            written++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            written += len;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
