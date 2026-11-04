package com.aquadecomp.recovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecoveryPipeline {

    private final NameRecoveryEngine nameRecovery = new NameRecoveryEngine();
    private final DeobfuscationEngine deobfuscationEngine = new DeobfuscationEngine(nameRecovery);
    private final StringDecryptionEngine stringDecryption = new StringDecryptionEngine();
    private final ClassRecoveryEngine classRecovery = new ClassRecoveryEngine();
    private final MetadataExtractor metadataExtractor = new MetadataExtractor();
    private final RealCompilationVerifier compilationVerifier = new RealCompilationVerifier();

    private final Map<String, RecoveryMetrics> metrics = new HashMap<>();

    public RecoveryResult recover(Path sourcesDir, Path resourcesDir) {
        if (!Files.isDirectory(sourcesDir)) {
            return new RecoveryResult(false, 0, new ArrayList<>(), new HashMap<>());
        }

        final int[] filesProcessedArray = {0};
        final int[] filesRecoveredArray = {0};
        List<String> recoveredItems = new ArrayList<>();
        Map<String, RecoveryMetrics> fileMetrics = new HashMap<>();

        try {
            Files.walk(sourcesDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            RecoveryMetrics result = recoverFile(file, resourcesDir);
                            fileMetrics.put(file.toString(), result);

                            if (result.wasRecovered()) {
                                filesRecoveredArray[0]++;
                                recoveredItems.addAll(result.recoveredItems());
                            }

                            metrics.put(file.toString(), result);
                        } catch (Exception ignored) {
                        }
                    });

            filesProcessedArray[0] = (int) Files.walk(sourcesDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();

        } catch (Exception ignored) {
        }

        double recoveryRate = filesProcessedArray[0] > 0 ?
                (filesRecoveredArray[0] * 100.0) / filesProcessedArray[0] : 0;
        boolean success = recoveryRate >= 95.0;

        return new RecoveryResult(success, recoveryRate, recoveredItems, fileMetrics);
    }

    private RecoveryMetrics recoverFile(Path file, Path resourcesDir) throws Exception {
        String originalContent = Files.readString(file);
        String recovered = originalContent;
        List<String> items = new ArrayList<>();
        String className = extractClassName(file);

        recovered = stringDecryption.attemptDecryption(recovered);
        if (!recovered.equals(originalContent)) {
            items.add("String decryption applied");
        }

        nameRecovery.recoverFromKotlinMetadata(recovered, className);
        nameRecovery.recoverFromSourceFile(recovered, className);
        nameRecovery.recoverFromEnumPatterns(recovered, className);
        nameRecovery.recoverFromResourceReferences(recovered);
        nameRecovery.recoverFromCallContext(recovered);
        
        recovered = deobfuscationEngine.deobfuscate(recovered, className);
        if (!recovered.equals(originalContent)) {
            items.add("Deobfuscation applied");
        }

        recovered = classRecovery.recoverClasses(recovered);
        if (!recovered.equals(originalContent)) {
            items.add("Classes recovered");
        }

        metadataExtractor.extractMetadata(recovered, className);

        if (!recovered.equals(originalContent)) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            try {
                Files.copy(file, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            Files.writeString(file, recovered);
        }

        RealCompilationVerifier.CompilationResult compilationResult =
                compilationVerifier.verify(file);

        int score = compilationResult.score();
        boolean isValid = compilationResult.isSuccess();

        return new RecoveryMetrics(
                file.toString(),
                items,
                score,
                isValid,
                !recovered.equals(originalContent)
        );
    }

    private String extractClassName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }

    public Map<String, RecoveryMetrics> getMetrics() {
        return new HashMap<>(metrics);
    }

    public static final class RecoveryResult {
        private final boolean success;
        private final double recoveryRate;
        private final List<String> recoveredItems;
        private final Map<String, RecoveryMetrics> fileMetrics;

        public RecoveryResult(boolean success, double recoveryRate,
                              List<String> recoveredItems,
                              Map<String, RecoveryMetrics> fileMetrics) {
            this.success = success;
            this.recoveryRate = recoveryRate;
            this.recoveredItems = new ArrayList<>(recoveredItems);
            this.fileMetrics = new HashMap<>(fileMetrics);
        }

        public boolean isSuccess() {
            return success;
        }

        public double recoveryRate() {
            return recoveryRate;
        }

        public List<String> recoveredItems() {
            return new ArrayList<>(recoveredItems);
        }

        public Map<String, RecoveryMetrics> fileMetrics() {
            return new HashMap<>(fileMetrics);
        }

        public String summary() {
            return String.format(
                    "Recovery: %.1f%% - %d items recovered",
                    recoveryRate,
                    recoveredItems.size()
            );
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
            this.recoveredItems = new ArrayList<>(recoveredItems);
            this.compilationScore = compilationScore;
            this.isValid = isValid;
            this.wasRecovered = wasRecovered;
        }

        public String filePath() {
            return filePath;
        }

        public List<String> recoveredItems() {
            return new ArrayList<>(recoveredItems);
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
