package com.aquadecomp.nativecode;

import com.aquadecomp.input.InputNormalizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NativeAnalyzer {

    public record Library(String entry, String abi, ElfParser.ElfInfo elf, String error) {
    }

    public record GhidraStatus(boolean requested, boolean available, String message,
                               Path outputFile, int functions) {
    }

    public record Report(int libraries, List<Library> items, List<String> warnings,
                         GhidraStatus ghidra) {
    }

    private record Loaded(List<Library> items, Map<String, byte[]> payloads) {
    }

    private NativeAnalyzer() {
    }

    public static Report analyze(Path apk, String filter, boolean decompile,
                                 Path ghidraHome, Path outDir) throws IOException {
        InputNormalizer.NormalizedInput input = new InputNormalizer().normalize(apk);
        try {
            Loaded loaded = readLibraries(input.analysisTarget(), filter);
            List<String> warnings = new ArrayList<>();
            if (loaded.items().isEmpty()) {
                warnings.add(filter == null || filter.isBlank()
                        ? "El paquete no contiene bibliotecas .so"
                        : "Ninguna biblioteca coincide con el filtro: " + filter);
            }
            GhidraStatus status = runGhidra(decompile, ghidraHome, loaded, outDir, warnings);
            return new Report(loaded.items().size(), List.copyOf(loaded.items()),
                    List.copyOf(warnings), status);
        } finally {
            if (input.primaryApk() != null) {
                try {
                    Files.deleteIfExists(input.primaryApk());
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Loaded readLibraries(Path target, String filter) throws IOException {
        String needle = filter == null || filter.isBlank()
                ? null : filter.toLowerCase(Locale.ROOT);
        List<Library> items = new ArrayList<>();
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(target.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()
                        || !name.toLowerCase(Locale.ROOT).endsWith(".so")) {
                    continue;
                }
                if (needle != null && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                byte[] bytes;
                try (var in = zip.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                ElfParser.ElfInfo elf = null;
                String error = null;
                try {
                    elf = ElfParser.parse(bytes);
                } catch (IOException e) {
                    error = e.getMessage();
                }
                items.add(new Library(name, elf != null ? elf.abi() : abiFromPath(name),
                        elf, error));
                payloads.put(name, bytes);
            }
        }
        items.sort(Comparator.comparing(Library::entry));
        return new Loaded(items, payloads);
    }

    private static GhidraStatus runGhidra(boolean decompile, Path ghidraHome,
                                          Loaded loaded, Path outDir,
                                          List<String> warnings) {
        if (!decompile) {
            return new GhidraStatus(false, false,
                    "No se solicitó desensamblado; solo análisis ELF", null, 0);
        }
        Optional<Path> home = ghidraHome != null
                ? GhidraLocator.find(ghidraHome, null, null)
                : GhidraLocator.findDefault();
        if (home.isEmpty()) {
            return new GhidraStatus(true, false,
                    "Ghidra no está disponible: no se encontró analyzeHeadless. "
                            + "Solo se realizó análisis ELF (símbolos, secciones y "
                            + "dependencias). Instala Ghidra y define GHIDRA_INSTALL_DIR "
                            + "o indica --ghidra <directorio>.", null, 0);
        }
        Library target = loaded.items().stream()
                .filter(item -> item.elf() != null)
                .findFirst()
                .orElse(null);
        if (target == null) {
            return new GhidraStatus(true, true,
                    "Ghidra está disponible pero no hay bibliotecas ELF que analizar",
                    null, 0);
        }
        try {
            Path work = outDir != null
                    ? outDir : Files.createTempDirectory("aquadecomp-native-");
            Files.createDirectories(work);
            Path soFile = work.resolve(target.entry()
                    .substring(target.entry().lastIndexOf('/') + 1));
            Files.write(soFile, loaded.payloads().get(target.entry()));
            GhidraRunner.Result result = GhidraRunner.decompile(home.get(), soFile, work,
                    GhidraRunner.DEFAULT_TIMEOUT);
            return new GhidraStatus(true, true,
                    "Ghidra headless analizó " + target.entry(), result.outputFile(),
                    result.functions());
        } catch (IOException e) {
            warnings.add("Ghidra falló: " + e.getMessage());
            return new GhidraStatus(true, true,
                    "Ghidra no completó el análisis: " + e.getMessage(), null, 0);
        }
    }

    private static String abiFromPath(String entry) {
        String lower = entry.toLowerCase(Locale.ROOT);
        for (String abi : new String[]{"arm64-v8a", "armeabi-v7a", "armeabi",
                "x86_64", "x86", "mips64", "mips"}) {
            if (lower.contains("/" + abi + "/")) {
                return abi;
            }
        }
        return "desconocida";
    }

    public static Duration defaultTimeout() {
        return GhidraRunner.DEFAULT_TIMEOUT;
    }
}
