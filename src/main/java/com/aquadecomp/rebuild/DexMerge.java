package com.aquadecomp.rebuild;

import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DexMerge {

    private DexMerge() {
    }

    public record MergeResult(List<ApkPacker.Replacement> replacements, int classesReplaced) {
    }

    public static MergeResult merge(Path originalApk, List<Path> newDexFiles,
                                    Set<String> replacedClasses, Path workDir)
            throws Exception {
        Path staging = workDir.resolve("smali-nuevas");
        deleteRecursively(staging);
        Files.createDirectories(staging);

        List<String> originalDexNames = rootDexEntries(originalApk);
        if (originalDexNames.isEmpty()) {
            throw new IOException("El APK original no contiene clases dex en la raíz");
        }

        Map<String, Set<String>> porDex = new LinkedHashMap<>();
        for (String dexName : originalDexNames) {
            porDex.put(dexName, new LinkedHashSet<>());
        }
        Path lectura = workDir.resolve("original-lectura.dex");
        for (String dexName : originalDexNames) {
            extract(originalApk, dexName, lectura);
            DexBackedDexFile dex = DexFileFactory.loadDexFile(lectura.toFile(),
                    opcodesFor(lectura));
            for (ClassDef clase : dex.getClasses()) {
                String tipo = clase.getType();
                String fqcn = tipo.substring(1, tipo.length() - 1).replace('/', '.');
                if (replacedClasses.contains(fqcn)) {
                    porDex.get(dexName).add(fqcn);
                }
            }
        }
        Files.deleteIfExists(lectura);
        Set<String> pendientes = new LinkedHashSet<>(replacedClasses);
        porDex.values().forEach(pendientes::removeAll);
        porDex.get(originalDexNames.get(0)).addAll(pendientes);

        for (Path newDex : newDexFiles) {
            disassemble(newDex, staging, opcodesFor(newDex));
        }

        int merged = 0;
        List<ApkPacker.Replacement> replacements = new ArrayList<>();
        int indice = 0;
        for (String dexName : originalDexNames) {
            Set<String> clases = porDex.get(dexName);
            if (clases.isEmpty()) {
                replacements.add(new ApkPacker.Replacement(dexName,
                        lecturaOriginal(originalApk, dexName)));
                continue;
            }
            indice++;
            Path extracted = workDir.resolve("original-" + dexName.replace('/', '_'));
            Path tree = workDir.resolve("smali-" + indice);
            deleteRecursively(tree);
            Files.createDirectories(tree);
            extract(originalApk, dexName, extracted);
            Opcodes opcodes = opcodesFor(extracted);
            disassemble(extracted, tree, opcodes);

            for (String fqcn : clases) {
                Path source = staging.resolve(fqcn.replace('.', '/') + ".smali");
                if (!Files.isRegularFile(source)) {
                    continue;
                }
                Path target = tree.resolve(fqcn.replace('.', '/') + ".smali");
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                merged++;
            }

            Path outDex = workDir.resolve("reemplazado-" + indice + ".dex");
            SmaliOptions options = new SmaliOptions();
            options.apiLevel = opcodes.api;
            options.outputDexFile = outDex.toString();
            boolean assembled = Smali.assemble(options, List.of(tree.toString()));
            if (!assembled || !Files.isRegularFile(outDex)) {
                throw new IOException("No se pudo ensamblar el dex " + dexName);
            }
            replacements.add(new ApkPacker.Replacement(dexName,
                    Files.readAllBytes(outDex)));
            deleteRecursively(tree);
        }
        if (merged == 0) {
            throw new IOException("Ninguna clase recompilada se pudo integrar al dex original");
        }
        return new MergeResult(replacements, merged);
    }

    private static byte[] lecturaOriginal(Path apk, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new IOException("Falta la entrada " + entryName + " en el APK original");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
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
