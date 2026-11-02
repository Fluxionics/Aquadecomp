package com.aquadecomp.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class InputNormalizer {

    private static final String APK_EXTENSION = ".apk";
    private static final String AAB_EXTENSION = ".aab";
    private static final String XAPK_EXTENSION = ".xapk";
    private static final String APKS_EXTENSION = ".apks";

    public enum InputType {
        APK("APK", "Paquete Android estándar"),
        AAB("AAB", "Android App Bundle"),
        XAPK("XAPK", "Paquete expandido"),
        APKS("APKS", "Splits APK"),
        UNKNOWN("UNKNOWN", "Formato desconocido");

        public final String label;
        public final String description;

        InputType(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public static InputType detectType(Path file) {
        String name = file.getFileName().toString().toLowerCase();

        if (name.endsWith(APK_EXTENSION)) {
            return InputType.APK;
        } else if (name.endsWith(AAB_EXTENSION)) {
            return InputType.AAB;
        } else if (name.endsWith(XAPK_EXTENSION)) {
            return InputType.XAPK;
        } else if (name.endsWith(APKS_EXTENSION)) {
            return InputType.APKS;
        }
        return InputType.UNKNOWN;
    }

    public NormalizedInput normalize(Path inputFile) throws IOException {
        if (!Files.isRegularFile(inputFile)) {
            throw new IOException("Archivo no encontrado: " + inputFile);
        }

        InputType type = detectType(inputFile);

        return switch (type) {
            case APK -> normalizeApk(inputFile);
            case AAB -> normalizeAab(inputFile);
            case XAPK -> normalizeXapk(inputFile);
            case APKS -> normalizeApks(inputFile);
            case UNKNOWN -> throw new IOException("Formato no soportado: " + inputFile);
        };
    }

    private NormalizedInput normalizeApk(Path apkFile) throws IOException {
        Path tempDir = Files.createTempDirectory("aquadecomp_apk_");
        extractApkContents(apkFile, tempDir);

        List<Path> dexFiles = extractDexFiles(apkFile, tempDir);
        Path manifestPath = tempDir.resolve("AndroidManifest.xml");
        Path resourcesDir = tempDir.resolve("resources");

        return new NormalizedInput(
                InputType.APK,
                apkFile,
                dexFiles,
                manifestPath,
                resourcesDir,
                tempDir,
                false
        );
    }

    private NormalizedInput normalizeAab(Path aabFile) throws IOException {
        Path tempDir = Files.createTempDirectory("aquadecomp_aab_");

        try {
            Path bundletoolOutput = tempDir.resolve("bundletool_out");
            Files.createDirectories(bundletoolOutput);

            ProcessBuilder pb = new ProcessBuilder(
                    "bundletool", "dump", "manifest",
                    "--bundle=" + aabFile.toString()
            );
            pb.directory(tempDir.toFile());
            Process proc = pb.start();
            proc.waitFor();

            List<Path> dexFiles = extractDexFilesFromAab(aabFile, tempDir);

            return new NormalizedInput(
                    InputType.AAB,
                    aabFile,
                    dexFiles,
                    tempDir.resolve("AndroidManifest.xml"),
                    tempDir.resolve("resources"),
                    tempDir,
                    false
            );
        } catch (Exception e) {
            throw new IOException("Error normalizando AAB: " + e.getMessage(), e);
        }
    }

    private NormalizedInput normalizeXapk(Path xapkFile) throws IOException {
        Path tempDir = Files.createTempDirectory("aquadecomp_xapk_");
        Path extractDir = tempDir.resolve("xapk_extracted");
        Files.createDirectories(extractDir);

        extractZip(xapkFile, extractDir);

        List<Path> dexFiles = new ArrayList<>();
        List<Path> apkFiles = new ArrayList<>();

        Files.walk(extractDir)
                .filter(p -> p.toString().endsWith(".apk"))
                .forEach(apkFiles::add);

        for (Path apk : apkFiles) {
            extractDexFiles(apk, tempDir).forEach(dexFiles::add);
        }

        return new NormalizedInput(
                InputType.XAPK,
                xapkFile,
                dexFiles,
                extractDir.resolve("AndroidManifest.xml"),
                extractDir.resolve("resources"),
                tempDir,
                false
        );
    }

    private NormalizedInput normalizeApks(Path apksFile) throws IOException {
        Path tempDir = Files.createTempDirectory("aquadecomp_apks_");
        Path extractDir = tempDir.resolve("apks_extracted");
        Files.createDirectories(extractDir);

        extractZip(apksFile, extractDir);

        List<Path> dexFiles = new ArrayList<>();
        List<Path> apkFiles = new ArrayList<>();

        Files.walk(extractDir)
                .filter(p -> p.toString().endsWith(".apk"))
                .forEach(apkFiles::add);

        for (Path apk : apkFiles) {
            extractDexFiles(apk, tempDir).forEach(dexFiles::add);
        }

        return new NormalizedInput(
                InputType.APKS,
                apksFile,
                dexFiles,
                extractDir.resolve("AndroidManifest.xml"),
                extractDir.resolve("resources"),
                tempDir,
                false
        );
    }

    private List<Path> extractDexFiles(Path apkFile, Path outputDir) throws IOException {
        List<Path> dexFiles = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(apkFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("classes") && entry.getName().endsWith(".dex")) {
                    Path dexPath = outputDir.resolve(entry.getName());
                    Files.createDirectories(dexPath.getParent());
                    Files.copy(zis, dexPath);
                    dexFiles.add(dexPath);
                }
            }
        }

        return dexFiles;
    }

    private List<Path> extractDexFilesFromAab(Path aabFile, Path outputDir) throws IOException {
        List<Path> dexFiles = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(aabFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().contains("dex") && entry.getName().endsWith(".dex")) {
                    Path dexPath = outputDir.resolve(entry.getName());
                    Files.createDirectories(dexPath.getParent());
                    Files.copy(zis, dexPath);
                    dexFiles.add(dexPath);
                }
            }
        }

        return dexFiles;
    }

    private void extractApkContents(Path apkFile, Path outputDir) throws IOException {
        extractZip(apkFile, outputDir);
    }

    private void extractZip(Path zipFile, Path outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = outputDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zis, filePath);
                }
            }
        }
    }

    public record NormalizedInput(
            InputType type,
            Path originalFile,
            List<Path> dexFiles,
            Path manifestPath,
            Path resourcesDir,
            Path tempDirectory,
            boolean isSplit
    ) {
        public boolean hasDexFiles() {
            return dexFiles != null && !dexFiles.isEmpty();
        }

        public int dexCount() {
            return dexFiles != null ? dexFiles.size() : 0;
        }
    }
}
