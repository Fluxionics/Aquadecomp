package com.aquadecomp.mapping;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class MappingApplier {

    public record Applied(Path apk, int classes, int members) {
    }

    private static final int MAX_NESTING = 3;

    private MappingApplier() {
    }

    public static Applied apply(Path apk, MappingFile mapping, Path output) throws IOException {
        MappingFile.RenameTable table = mapping.renameTable(MappingFile.Direction.REVERSE);
        int[] counts = new int[2];
        byte[] rewritten = rewriteZip(Files.readAllBytes(apk), table, 0, counts);
        if (rewritten == null) {
            throw new IOException("No se encontraron archivos dex en " + apk
                    + ": --mapping necesita un APK con dex (para XAPK/APKS/AAB usa el "
                    + "APK interno)");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, rewritten);
        return new Applied(output, counts[0], counts[1]);
    }

    static byte[] rewriteZip(byte[] zipBytes, MappingFile.RenameTable table, int depth,
                             int[] counts) throws IOException {
        boolean foundDex = false;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipBytes));
             ZipOutputStream out = new ZipOutputStream(buffer)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    out.putNextEntry(new ZipEntry(name));
                    out.closeEntry();
                    continue;
                }
                byte[] data = in.readAllBytes();
                byte[] replaced = null;
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".dex")) {
                    foundDex = true;
                    replaced = rewriteDex(data, table, counts, name.indexOf('/') < 0);
                } else if (depth < MAX_NESTING && isArchive(lower)) {
                    byte[] inner = rewriteZip(data, table, depth + 1, counts);
                    if (inner != null) {
                        foundDex = true;
                        replaced = inner;
                    }
                }
                out.putNextEntry(new ZipEntry(name));
                out.write(replaced != null ? replaced : data);
                out.closeEntry();
            }
        }
        return foundDex ? buffer.toByteArray() : null;
    }

    private static byte[] rewriteDex(byte[] data, MappingFile.RenameTable table,
                                     int[] counts, boolean root) throws IOException {
        try {
            DexMappingRewriter.Result result = DexMappingRewriter.rewrite(data, table);
            counts[0] += result.classes();
            counts[1] += result.members();
            return result.dex();
        } catch (IOException | RuntimeException e) {
            if (root) {
                throw new IOException("No se pudo aplicar el mapping al dex: "
                        + e.getMessage(), e);
            }
            return data;
        }
    }

    private static boolean isArchive(String lowerName) {
        return lowerName.endsWith(".apk") || lowerName.endsWith(".aab")
                || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk");
    }
}
