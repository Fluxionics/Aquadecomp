package com.aquadecomp.recovery;

import com.aquadecomp.core.PipelineListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RecoveryPipeline {

    private static final int MAX_JAVAC_CHECKS = 40;
    private static final int PROGRESS_INTERVAL = 500;

    private final StringDecryptionEngine stringDecryption = new StringDecryptionEngine();
    private final ClassRecoveryEngine classRecovery = new ClassRecoveryEngine();
    private final MetadataExtractor metadataExtractor = new MetadataExtractor();
    private final RealCompilationVerifier compilationVerifier = new RealCompilationVerifier();

    private final Map<String, RecoveryMetrics> metrics = new LinkedHashMap<>();

    public RecoveryResult recover(Path sourcesDir, PipelineListener listener) {
        long start = System.currentTimeMillis();
        metrics.clear();

        List<Path> files = listJavaFiles(sourcesDir);
        if (files.isEmpty()) {
            return new RecoveryResult(0, 0, 0, 0, 0, 0, 0,
                    Map.of(), Map.of(), System.currentTimeMillis() - start);
        }

        boolean javacAvailable = compilationVerifier.isAvailable();
        int step = Math.max(1, files.size() / MAX_JAVAC_CHECKS);

        int modified = 0;
        int stringsDecrypted = 0;
        int javacSampled = 0;
        int javacCompiled = 0;
        Map<String, Integer> pluginCounts = new LinkedHashMap<>();
        java.util.Set<String> pluginNotes = new java.util.LinkedHashSet<>();

        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            boolean fileModified = false;
            int decryptedHere = 0;

            try {
                FileOutcome outcome = recoverFile(file);
                fileModified = outcome.modified();
                decryptedHere = outcome.stringsDecrypted();
                modified += fileModified ? 1 : 0;
                stringsDecrypted += decryptedHere;
                outcome.pluginCounts().forEach((k, v) -> pluginCounts.merge(k, v, Integer::sum));
                pluginNotes.addAll(outcome.pluginNotes());
            } catch (Exception ignored) {
            }

            boolean verify = javacAvailable
                    && javacSampled < MAX_JAVAC_CHECKS
                    && (i % step == 0 || fileModified);
            if (verify) {
                RealCompilationVerifier.CompilationResult check = compilationVerifier.verify(file);
                javacSampled++;
                if (check.isSuccess()) {
                    javacCompiled++;
                }
                metrics.put(file.toString(), new RecoveryMetrics(
                        file.toString(),
                        fileModified ? List.of("Cadenas descifradas") : List.of(),
                        check.score(),
                        check.isSuccess(),
                        fileModified
                ));
            }

            if ((i + 1) % PROGRESS_INTERVAL == 0 || i + 1 == files.size()) {
                listener.onProgress(97, "Recuperación: "
                        + (i + 1) + "/" + files.size() + " archivos");
            }
        }

        int kotlinClasses = (int) metadataExtractor.getAllMetadata().values().stream()
                .filter(MetadataExtractor.Metadata::isKotlin)
                .count();
        int innerClasses = (int) classRecovery.getRecoveredClasses().values().stream()
                .filter(ClassRecoveryEngine.ClassInfo::isInner)
                .count();

        Map<String, Integer> items = new LinkedHashMap<>();
        if (stringsDecrypted > 0) {
            items.put("Cadenas descifradas", stringsDecrypted);
        }
        int pluginDecrypted = pluginCounts.values().stream()
                .mapToInt(Integer::intValue).sum();
        if (pluginDecrypted > 0) {
            items.put("Cadenas descifradas por plugins de encriptación", pluginDecrypted);
        }
        int logged = 0;
        for (String note : pluginNotes) {
            if (logged >= 3) {
                break;
            }
            listener.onLog("Plugins de encriptación: " + note);
            logged++;
        }

        return new RecoveryResult(
                files.size(),
                modified,
                javacSampled,
                javacCompiled,
                stringsDecrypted,
                kotlinClasses,
                innerClasses,
                items,
                new LinkedHashMap<>(metrics),
                System.currentTimeMillis() - start
        );
    }

    private List<Path> listJavaFiles(Path sourcesDir) {
        if (!Files.isDirectory(sourcesDir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(sourcesDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private FileOutcome recoverFile(Path file) throws IOException {
        String original = Files.readString(file);
        String recovered = stringDecryption.attemptDecryption(original);
        int decrypted = stringDecryption.lastReplacementCount();
        Map<String, Integer> pluginCounts = stringDecryption.lastPluginCounts();
        List<String> pluginNotes = stringDecryption.lastNotes();

        classRecovery.analyze(recovered);
        metadataExtractor.extract(recovered, extractClassName(file));

        if (!recovered.equals(original)) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file, recovered);
            return new FileOutcome(true, decrypted, pluginCounts, pluginNotes);
        }
        return new FileOutcome(false, decrypted, pluginCounts, pluginNotes);
    }

    private String extractClassName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, lastDot(name));
    }

    private int lastDot(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? dot : name.length();
    }

    public Map<String, RecoveryMetrics> getMetrics() {
        return new LinkedHashMap<>(metrics);
    }

    private record FileOutcome(boolean modified, int stringsDecrypted,
                               Map<String, Integer> pluginCounts, List<String> pluginNotes) {
    }

    public static final class RecoveryResult {
        private final int filesProcessed;
        private final int filesModified;
        private final int javacSampled;
        private final int javacCompiled;
        private final int stringsDecrypted;
        private final int kotlinClasses;
        private final int innerClasses;
        private final Map<String, Integer> items;
        private final Map<String, RecoveryMetrics> fileMetrics;
        private final long durationMs;

        public RecoveryResult(int filesProcessed, int filesModified,
                              int javacSampled, int javacCompiled,
                              int stringsDecrypted, int kotlinClasses, int innerClasses,
                              Map<String, Integer> items,
                              Map<String, RecoveryMetrics> fileMetrics,
                              long durationMs) {
            this.filesProcessed = filesProcessed;
            this.filesModified = filesModified;
            this.javacSampled = javacSampled;
            this.javacCompiled = javacCompiled;
            this.stringsDecrypted = stringsDecrypted;
            this.kotlinClasses = kotlinClasses;
            this.innerClasses = innerClasses;
            this.items = new LinkedHashMap<>(items);
            this.fileMetrics = new LinkedHashMap<>(fileMetrics);
            this.durationMs = durationMs;
        }

        public int filesProcessed() {
            return filesProcessed;
        }

        public int filesModified() {
            return filesModified;
        }

        public int javacSampled() {
            return javacSampled;
        }

        public int javacCompiled() {
            return javacCompiled;
        }

        public int stringsDecrypted() {
            return stringsDecrypted;
        }

        public int kotlinClasses() {
            return kotlinClasses;
        }

        public int innerClasses() {
            return innerClasses;
        }

        public Map<String, Integer> items() {
            return new LinkedHashMap<>(items);
        }

        public Map<String, RecoveryMetrics> fileMetrics() {
            return new LinkedHashMap<>(fileMetrics);
        }

        public long durationMs() {
            return durationMs;
        }
    }

    public static final class RecoveryMetrics {
        private final String filePath;
        private final List<String> recoveredItems;
        private final int compilationScore;
        private final boolean isValid;
        private final boolean wasRecovered;

        public RecoveryMetrics(String filePath, List<String> recoveredItems,
                               int compilationScore, boolean isValid, boolean wasRecovered) {
            this.filePath = filePath;
            this.recoveredItems = List.copyOf(recoveredItems);
            this.compilationScore = compilationScore;
            this.isValid = isValid;
            this.wasRecovered = wasRecovered;
        }

        public String filePath() {
            return filePath;
        }

        public List<String> recoveredItems() {
            return recoveredItems;
        }

        public int compilationScore() {
            return compilationScore;
        }

        public boolean isValid() {
            return isValid;
        }

        public boolean wasRecovered() {
            return wasRecovered;
        }
    }
}
