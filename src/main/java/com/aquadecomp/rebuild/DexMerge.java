package com.aquadecomp.rebuild;

import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DexMerge {

    private DexMerge() {
    }

    public record MergeResult(Path dex, int classesReplaced) {
    }

    public static MergeResult merge(Path originalApk, List<Path> newDexFiles,
                                    Set<String> replacedClasses, Path workDir, Path outDex)
            throws Exception {
        Path tree = workDir.resolve("smali-original");
        Path staging = workDir.resolve("smali-nuevas");
        deleteRecursively(tree);
        deleteRecursively(staging);
        Files.createDirectories(tree);
        Files.createDirectories(staging);

        List<String> originalDexNames = rootDexEntries(originalApk);
        if (originalDexNames.isEmpty()) {
            throw new IOException("El APK original no contiene clases dex en la raíz");
        }

        int apiLevel = 15;
        for (String dexName : originalDexNames) {
            Path extracted = workDir.resolve("original-" + dexName.replace('/', '_'));
            extract(originalApk, dexName, extracted);
            Opcodes opcodes = opcodesFor(extracted);
            apiLevel = Math.max(apiLevel, opcodes.api);
            disassemble(extracted, tree, opcodes);
        }

        for (Path newDex : newDexFiles) {
            Opcodes opcodes = opcodesFor(newDex);
            apiLevel = Math.max(apiLevel, opcodes.api);
            disassemble(newDex, staging, opcodes);
        }

        for (String fqcn : replacedClasses) {
            Files.deleteIfExists(tree.resolve(fqcn.replace('.', '/') + ".smali"));
        }

        int merged = 0;
        for (String fqcn : replacedClasses) {
            Path source = staging.resolve(fqcn.replace('.', '/') + ".smali");
            if (!Files.isRegularFile(source)) {
                continue;
            }
            Path target = tree.resolve(fqcn.replace('.', '/') + ".smali");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            merged++;
        }
        if (merged == 0) {
            throw new IOException("Ninguna clase recompilada se pudo integrar al dex original");
        }

        SmaliOptions options = new SmaliOptions();
        options.apiLevel = apiLevel;
        options.outputDexFile = outDex.toString();
        boolean assembled = Smali.assemble(options, List.of(tree.toString()));
        if (!assembled || !Files.isRegularFile(outDex)) {
            throw new IOException("No se pudo ensamblar el dex fusionado");
        }
        return new MergeResult(outDex, merged);
    }

    public static List<String> rootDexEntries(Path apk) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.equals("classes.dex") || name.matches("classes\\d+\\.dex")) {
                    names.add(name);
                }
            }
        }
        names.sort(Comparator.comparingInt(DexMerge::dexIndex));
        return names;
    }

    static Opcodes opcodesFor(Path dexFile) throws IOException {
        byte[] head = new byte[8];
        try (InputStream in = Files.newInputStream(dexFile)) {
            if (in.readNBytes(head, 0, 8) != 8) {
                return Opcodes.getDefault();
            }
        }
        String magic = new String(head, 0, 4, StandardCharsets.US_ASCII);
        if (!"dex\n".equals(magic)) {
            return Opcodes.getDefault();
        }
        String version = new String(head, 4, 3, StandardCharsets.US_ASCII);
        try {
            return Opcodes.forDexVersion(Integer.parseInt(version));
        } catch (RuntimeException e) {
            return Opcodes.getDefault();
        }
    }

    private static void disassemble(Path dexFile, Path outDir, Opcodes opcodes) throws Exception {
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile.toFile(), opcodes);
        BaksmaliOptions options = new BaksmaliOptions();
        options.apiLevel = opcodes.api;
        Baksmali.disassembleDexFile(dex, outDir.toFile(), 1, options);
    }

    private static int dexIndex(String name) {
        if (name.equals("classes.dex")) {
            return 0;
        }
        try {
            return Integer.parseInt(
                    name.substring("classes".length(), name.length() - ".dex".length()));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static void extract(Path apk, String entryName, Path target) throws IOException {
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new IOException("Falta la entrada " + entryName + " en el APK original");
            }
            try (InputStream in = zip.getInputStream(entry);
                 OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
