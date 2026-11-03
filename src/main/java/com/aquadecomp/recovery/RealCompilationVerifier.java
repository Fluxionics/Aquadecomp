package com.aquadecomp.recovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RealCompilationVerifier {

    public CompilationResult verify(Path javaFile) throws IOException {
        if (!Files.exists(javaFile) || !javaFile.toString().endsWith(".java")) {
            return new CompilationResult(false, new ArrayList<>(), -1);
        }

        Path tempDir = Files.createTempDirectory("aquadecomp-compile");

        try {
            String result = invokeJavac(javaFile, tempDir);
            List<String> errors = parseJavacOutput(result);

            boolean success = errors.isEmpty();
            int score = calculateScore(errors);

            return new CompilationResult(success, errors, score);
        } catch (Exception e) {
            List<String> errors = new ArrayList<>();
            errors.add(e.getMessage());
            return new CompilationResult(false, errors, 0);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    private String invokeJavac(Path javaFile, Path outputDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "javac",
                "-d", outputDir.toString(),
                "-encoding", "UTF-8",
                javaFile.toString()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        return output.toString();
    }

    private List<String> parseJavacOutput(String output) {
        List<String> errors = new ArrayList<>();

        for (String line : output.split("\n")) {
            if (line.contains("error:") || line.contains("ERROR")) {
                errors.add(line.trim());
            }
        }

        return errors;
    }

    private int calculateScore(List<String> errors) {
        if (errors.isEmpty()) {
            return 100;
        }

        int deductions = 0;

        for (String error : errors) {
            if (error.contains("cannot find symbol")) {
                deductions += 5;
            } else if (error.contains("incompatible types")) {
                deductions += 7;
            } else if (error.contains("method not found")) {
                deductions += 10;
            } else if (error.contains("class not found")) {
                deductions += 8;
            } else {
                deductions += 3;
            }
        }

        return Math.max(0, 100 - deductions);
    }

    private void cleanupTempDir(Path tempDir) {
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public static final class CompilationResult {
        private final boolean success;
        private final List<String> errors;
        private final int score;

        public CompilationResult(boolean success, List<String> errors, int score) {
            this.success = success;
            this.errors = new ArrayList<>(errors);
            this.score = score;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> errors() {
            return new ArrayList<>(errors);
        }

        public int score() {
            return score;
        }

        public int scoreAdjustment() {
            return score > 80 ? 15 : score > 50 ? 5 : -10;
        }
    }
}
