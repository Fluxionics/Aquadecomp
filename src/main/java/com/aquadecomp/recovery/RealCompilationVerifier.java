package com.aquadecomp.recovery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class RealCompilationVerifier {

    private static final long JAVAC_TIMEOUT_MS = 8000;

    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder("javac", "-version")
                    .redirectErrorStream(true)
                    .start();
            drain(process);
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public CompilationResult verify(Path javaFile) {
        if (!Files.exists(javaFile) || !javaFile.toString().endsWith(".java")) {
            return new CompilationResult(false, List.of(), -1);
        }

        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("aquadecomp-compile");
        } catch (IOException e) {
            return new CompilationResult(false, List.of("No se pudo crear directorio temporal"), 0);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "javac",
                    "-d", tempDir.toString(),
                    "-encoding", "UTF-8",
                    "-nowarn",
                    javaFile.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            if (!process.waitFor(JAVAC_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                drain(process);
                return new CompilationResult(false, List.of("javac excedió el tiempo límite"), 0);
            }

            String output = drain(process);
            List<String> errors = parseJavacOutput(output);
            boolean success = errors.isEmpty();

            return new CompilationResult(success, errors, calculateScore(errors));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CompilationResult(false, List.of("Verificación interrumpida"), 0);
        } catch (Exception e) {
            return new CompilationResult(false, List.of(String.valueOf(e.getMessage())), 0);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    private String drain(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private List<String> parseJavacOutput(String output) {
        List<String> errors = new ArrayList<>();

        for (String line : output.split("\n")) {
            if (line.contains("error:")) {
                if (isResolutionIssue(line)) {
                    continue;
                }
                errors.add(line.trim());
            }
        }
        if (output.contains("error: no se pudo encontrar o cargar clase principal")
                || output.contains("error: could not find or load main class")) {
            errors.add("javac no pudo ejecutarse");
        }
        return errors;
    }

    private boolean isResolutionIssue(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("cannot find symbol")
                || lower.contains("does not exist")
                || lower.contains("cannot access")
                || lower.contains("no se puede encontrar el símbolo")
                || lower.contains("no existe")
                || lower.contains("no se puede acceder");
    }

    private int calculateScore(List<String> errors) {
        if (errors.isEmpty()) {
            return 100;
        }
        int deductions = 0;
        for (String error : errors) {
            if (error.contains("incompatible types")) {
                deductions += 7;
            } else {
                deductions += 5;
            }
        }
        return Math.max(0, 100 - deductions);
    }

    private void cleanupTempDir(Path tempDir) {
        try (var walk = Files.walk(tempDir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
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
