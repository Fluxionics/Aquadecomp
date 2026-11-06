package com.aquadecomp.nativecode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GhidraLocator {

    private static final String[] SCRIPT_NAMES = {
            "analyzeHeadless.bat", "analyzeHeadless.exe", "analyzeHeadless.sh",
            "analyzeHeadless"
    };

    private GhidraLocator() {
    }

    public static Optional<Path> findDefault() {
        return find(null, System.getenv("GHIDRA_INSTALL_DIR"), pathEntries());
    }

    public static Optional<Path> find(Path explicit, String envHome, List<String> pathDirs) {
        if (explicit != null) {
            return headlessPresent(explicit) ? Optional.of(explicit) : Optional.empty();
        }
        if (envHome != null && !envHome.isBlank()) {
            Path home = Path.of(envHome.trim());
            return headlessPresent(home) ? Optional.of(home) : Optional.empty();
        }
        if (pathDirs != null) {
            for (String entry : pathDirs) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                Path dir = Path.of(entry);
                if (Files.isDirectory(dir) && headlessIn(dir)) {
                    return Optional.of(dir);
                }
            }
        }
        return Optional.empty();
    }

    public static Path headlessScript(Path home) {
        Path support = home.resolve("support");
        if (!Files.isDirectory(support)) {
            return null;
        }
        for (String name : SCRIPT_NAMES) {
            Path candidate = support.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean headlessPresent(Path home) {
        return headlessScript(home) != null;
    }

    private static boolean headlessIn(Path dir) {
        for (String name : SCRIPT_NAMES) {
            if (Files.isRegularFile(dir.resolve(name))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> pathEntries() {
        String path = System.getenv("PATH");
        List<String> entries = new ArrayList<>();
        if (path != null) {
            for (String entry : path.split(java.io.File.pathSeparator)) {
                entries.add(entry);
            }
        }
        return entries;
    }
}
