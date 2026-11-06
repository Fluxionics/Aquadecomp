package com.aquadecomp.rebuild;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class RebuildTestSupport {

    private RebuildTestSupport() {
    }

    static Path compileSource(Path dir, String fileName, String source) throws IOException {
        Path srcDir = dir.resolve("src");
        Files.createDirectories(srcDir);
        Path src = srcDir.resolve(fileName);
        Files.writeString(src, source, StandardCharsets.UTF_8);
        Path classes = dir.resolve("classes");
        SourceCompiler.Result result = SourceCompiler.compile(List.of(src), classes, List.of());
        if (!result.ok()) {
            throw new IllegalStateException("Fixture no compila: " + String.join("\n",
                    result.errors()));
        }
        return classes;
    }

    static Path buildDex(Path classesDir, Path workDir) throws IOException {
        Path jar = DexBuilder.jarClasses(classesDir, workDir.resolve("programa.jar"));
        List<Path> dexes = DexBuilder.build(jar, null, workDir.resolve("dex"), 21);
        return dexes.get(0);
    }

    static Path apkWith(Path apkFile, String... nameContentPairs) throws IOException {
        try (OutputStream fos = Files.newOutputStream(apkFile);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nameContentPairs[i]));
                zip.write(nameContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return apkFile;
    }

    static Path apkWithDex(Path apkFile, Path dexFile) throws IOException {
        try (OutputStream fos = Files.newOutputStream(apkFile);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(Files.readAllBytes(dexFile));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("<manifest package=\"com.example\"/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return apkFile;
    }

    static long storedDataOffset(byte[] zipBytes, String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= zipBytes.length - 30 - nameBytes.length; i++) {
            if ((zipBytes[i] & 0xFF) != 0x50 || (zipBytes[i + 1] & 0xFF) != 0x4B
                    || (zipBytes[i + 2] & 0xFF) != 0x03 || (zipBytes[i + 3] & 0xFF) != 0x04) {
                continue;
            }
            int nameLen = (zipBytes[i + 26] & 0xFF) | ((zipBytes[i + 27] & 0xFF) << 8);
            int extraLen = (zipBytes[i + 28] & 0xFF) | ((zipBytes[i + 29] & 0xFF) << 8);
            if (nameLen != nameBytes.length || i + 30 + nameLen > zipBytes.length) {
                continue;
            }
            boolean match = true;
            for (int j = 0; j < nameLen && match; j++) {
                if (zipBytes[i + 30 + j] != nameBytes[j]) {
                    match = false;
                }
            }
            if (match) {
                return (long) i + 30 + nameLen + extraLen;
            }
        }
        return -1;
    }

    static boolean containsBytes(byte[] haystack, String needle) {
        byte[] probe = needle.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= haystack.length - probe.length; i++) {
            for (int j = 0; j < probe.length; j++) {
                if (haystack[i + j] != probe[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
