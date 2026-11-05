package com.aquadecomp.input;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        if (!isZip(inputFile)) {
            throw new IOException("No es un archivo ZIP válido: " + inputFile);
        }

        InputType type = detectType(inputFile);
        if (type == InputType.UNKNOWN) {
            if (!looksLikeAndroidPackage(inputFile)) {
                throw new IOException("Formato no soportado: " + inputFile
                        + " (se aceptan .apk, .aab, .xapk y .apks)");
            }
            type = InputType.APK;
        }

        int dexEntries = countDexEntries(inputFile);
        return new NormalizedInput(type, inputFile.toAbsolutePath().normalize(), dexEntries);
    }

    private boolean isZip(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(4);
            return header.length == 4
                    && header[0] == 'P' && header[1] == 'K'
                    && (header[2] == 3 || header[2] == 5 || header[2] == 7);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean looksLikeAndroidPackage(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            boolean manifest = zip.getEntry("AndroidManifest.xml") != null;
            boolean dex = zip.stream().anyMatch(e -> isDexEntry(e.getName()));
            return manifest || dex;
        } catch (IOException e) {
            return false;
        }
    }

    private int countDexEntries(Path file) {
        int count = 0;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && isDexEntry(entry.getName())) {
                    count++;
                }
            }
        } catch (IOException e) {
            return 0;
        }
        return count;
    }

    private boolean isDexEntry(String name) {
        String base = name.substring(name.lastIndexOf('/') + 1);
        return base.startsWith("classes") && base.endsWith(".dex");
    }

    public record NormalizedInput(InputType type, Path file, int dexEntries) {
        public boolean isContainer() {
            return type == InputType.XAPK || type == InputType.APKS;
        }
    }
}
