package com.aquadecomp.decompilation;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class VineflowerDecompiler implements DecompilerStrategy {

    private static final String NAME = "Vineflower";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.jetbrains.java.decompiler.main.Fernflower");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public Set<String> rescue(Path jar, Path sourcesDir, Set<String> missing, DecompilerConfig config)
            throws Exception {
        Files.createDirectories(sourcesDir);
        Path classesDir = Files.createTempDirectory("aquedecomp-vineflower");
        try {
            if (extractClasses(jar, classesDir, missing) == 0) {
                return Set.of();
            }
            for (String name : missing) {
                Files.createDirectories(sourcesDir.resolve(name.replace('.', '/')).getParent());
            }
            DirectoryResultSaver saver = new DirectoryResultSaver(sourcesDir.toFile());
            PrintStreamLogger logger = new PrintStreamLogger(
                    new PrintStream(OutputStream.nullOutputStream()));
            Fernflower fernflower = new Fernflower(saver, null, logger);
            fernflower.addSource(classesDir.toFile());
            fernflower.decompileContext();
            Set<String> rescued = new LinkedHashSet<>();
            for (String name : missing) {
                if (Files.isRegularFile(sourcesDir.resolve(name.replace('.', '/') + ".java"))) {
                    rescued.add(name);
                }
            }
            return rescued;
        } finally {
            deleteTree(classesDir);
        }
    }

    private int extractClasses(Path jar, Path target, Set<String> missing) throws IOException {
        Set<String> exact = new LinkedHashSet<>();
        List<String> prefixes = new ArrayList<>();
        for (String name : missing) {
            String path = name.replace('.', '/');
            exact.add(path + ".class");
            prefixes.add(path + "$");
        }
        int copied = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory() || !entryName.endsWith(".class")) {
                    continue;
                }
                if (!matches(entryName, exact, prefixes)) {
                    continue;
                }
                Path out = target.resolve(entryName);
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
        }
        return copied;
    }

    private boolean matches(String entryName, Set<String> exact, List<String> prefixes) {
        if (exact.contains(entryName)) {
            return true;
        }
        for (String prefix : prefixes) {
            if (entryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
