package com.aquadecomp.rebuild;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DexBuilder {

    private DexBuilder() {
    }

    public static Path jarClasses(Path classesDir, Path jarFile) throws IOException {
        Path parent = jarFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream fos = Files.newOutputStream(jarFile);
             ZipOutputStream zip = new ZipOutputStream(fos)) {
            try (Stream<Path> walk = Files.walk(classesDir)) {
                List<Path> classFiles = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".class"))
                        .sorted()
                        .toList();
                for (Path classFile : classFiles) {
                    String name = classesDir.relativize(classFile).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(Files.readAllBytes(classFile));
                    zip.closeEntry();
                }
            }
        }
        if (!Files.isRegularFile(jarFile)) {
            throw new IOException("No se generó el JAR de clases compiladas");
        }
        return jarFile;
    }

    public static List<Path> build(Path programJar, Path androidJar, Path outDir, int minApi)
            throws IOException {
        Files.createDirectories(outDir);
        try (Stream<Path> old = Files.list(outDir)) {
            old.filter(p -> p.getFileName().toString().endsWith(".dex"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        }

        D8Command.Builder builder = D8Command.builder();
        builder.addProgramFiles(programJar);
        if (androidJar != null) {
            builder.addLibraryFiles(androidJar);
        }
        builder.setMinApiLevel(minApi);
        builder.setOutput(outDir, OutputMode.DexIndexed);

        try {
            D8.run(builder.build());
        } catch (CompilationFailedException e) {
            throw new IOException("d8 no pudo generar el dex: " + e.getMessage(), e);
        }

        try (Stream<Path> walk = Files.list(outDir)) {
            List<Path> dexes = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".dex"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            if (dexes.isEmpty()) {
                throw new IOException("d8 terminó sin producir ningún archivo dex");
            }
            return dexes;
        }
    }
}
