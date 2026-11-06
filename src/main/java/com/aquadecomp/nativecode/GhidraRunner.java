package com.aquadecomp.nativecode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GhidraRunner {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    public record Result(Path outputFile, int functions, String logTail) {
    }

    private GhidraRunner() {
    }

    public static Result decompile(Path home, Path soFile, Path outDir, Duration timeout)
            throws IOException {
        Path script = GhidraLocator.headlessScript(home);
        if (script == null) {
            throw new IOException("No se encontró analyzeHeadless en " + home + "/support");
        }
        Files.createDirectories(outDir);
        Path scriptSource = outDir.resolve("DecompileFunctions.java");
        Files.writeString(scriptSource, loadScript(), StandardCharsets.UTF_8);
        Path outputFile = outDir.resolve(stripExtension(soFile.getFileName().toString()) + ".c");
        Files.deleteIfExists(outputFile);
        Path projectDir = outDir.resolve("ghidra-project");

        List<String> command = new ArrayList<>();
        if (script.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".bat") || script.getFileName().toString()
                .toLowerCase(java.util.Locale.ROOT).endsWith(".cmd")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(script.toString());
        command.add(projectDir.toString());
        command.add("aquadecomp");
        command.add("-import");
        command.add(soFile.toString());
        command.add("-postScript");
        command.add(scriptSource.toString());
        command.add(outputFile.toString());
        command.add("-deleteProject");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder log = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    log.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Análisis de Ghidra interrumpido", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Ghidra superó el tiempo límite de "
                    + timeout.toMinutes() + " minutos");
        }
        String tail = tail(log.toString(), 2000);
        if (process.exitValue() != 0) {
            throw new IOException("Ghidra terminó con código " + process.exitValue()
                    + ": " + tail);
        }
        if (!Files.isRegularFile(outputFile)) {
            throw new IOException("Ghidra no produjo salida: " + tail);
        }
        String content = Files.readString(outputFile, StandardCharsets.UTF_8);
        int functions = countFunctions(content);
        return new Result(outputFile, functions, tail);
    }

    static String loadScript() throws IOException {
        try (InputStream in = GhidraRunner.class
                .getResourceAsStream("/ghidra/DecompileFunctions.java")) {
            if (in == null) {
                throw new IOException("Falta el recurso ghidra/DecompileFunctions.java");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static int countFunctions(String content) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf("=== ", index)) >= 0) {
            count++;
            index += 4;
        }
        return count;
    }

    private static String tail(String text, int max) {
        if (text.length() <= max) {
            return text.trim();
        }
        return text.substring(text.length() - max).trim();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
