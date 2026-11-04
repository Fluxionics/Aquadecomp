package com.aquadecomp.decompilation;

import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ProcyonDecompiler implements DecompilerStrategy {

    private static final String NAME = "Procyon";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.strobel.decompiler.Decompiler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public Set<String> rescue(Path jar, Path sourcesDir, Set<String> missing, DecompilerConfig config)
            throws Exception {
        Files.createDirectories(sourcesDir);
        Set<String> rescued = new LinkedHashSet<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            DecompilerSettings settings = DecompilerSettings.javaDefaults();
            settings.setTypeLoader(new JarTypeLoader(jarFile));
            for (String name : missing) {
                String internal = name.replace('.', '/');
                if (jarFile.getJarEntry(internal + ".class") == null) {
                    continue;
                }
                StringWriter writer = new StringWriter();
                try {
                    Decompiler.decompile(internal, new PlainTextOutput(writer), settings);
                } catch (Exception | StackOverflowError e) {
                    continue;
                }
                String code = writer.toString();
                if (code.isBlank() || code.contains("!!! ERROR: Failed to load class")) {
                    continue;
                }
                Path file = sourcesDir.resolve(internal + ".java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, code, StandardCharsets.UTF_8);
                rescued.add(name);
            }
        }
        return rescued;
    }
}
