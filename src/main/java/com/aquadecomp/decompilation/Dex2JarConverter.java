package com.aquadecomp.decompilation;

import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.d2j.reader.DexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Dex2JarConverter {

    private Dex2JarConverter() {
    }

    public static Path convert(Path apk, Path workDir) throws IOException {
        List<DexFileReader> readers = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!isDexEntry(entry.getName())) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    readers.add(new DexFileReader(in));
                }
            }
        }
        if (readers.isEmpty()) {
            throw new IOException("No se encontraron archivos DEX en el APK");
        }
        Files.createDirectories(workDir);
        Path jar = workDir.resolve("aquedecomp-classes.jar");
        Dex2jar.from(new MultiDexFileReader(readers))
                .skipExceptions(true)
                .to(jar);
        if (!Files.isRegularFile(jar)) {
            throw new IOException("dex2jar no genero el archivo JAR");
        }
        return jar;
    }

    static boolean isDexEntry(String name) {
        int slash = name.lastIndexOf('/');
        String base = slash < 0 ? name : name.substring(slash + 1);
        if (slash >= 0 && !name.contains("/dex/")) {
            return false;
        }
        return base.equals("classes.dex") || base.matches("classes\\d+\\.dex");
    }
}
