package com.aquadecomp.rebuild;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class SourceCompiler {

    public record Result(boolean ok, int classCount, List<String> errors) {
    }

    private SourceCompiler() {
    }

    public static Result compile(List<Path> sources, Path classesDir, List<Path> classpath)
            throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IOException("Se necesita un JDK completo (no solo una JRE) para compilar. "
                    + "Java actual: " + System.getProperty("java.home"));
        }
        if (sources.isEmpty()) {
            return new Result(false, 0, List.of("No hay archivos .java para compilar"));
        }

        Files.createDirectories(classesDir);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path emptySourcepath = Files.createTempDirectory("aquadecomp-sin-fuentes");

        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(classesDir.toString());
        if (!classpath.isEmpty()) {
            args.add("-classpath");
            args.add(String.join(java.io.File.pathSeparator,
                    classpath.stream().map(Path::toString).toList()));
        }
        args.add("-sourcepath");
        args.add(emptySourcepath.toString());
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-nowarn");
        args.add("-proc:none");
        args.add("-g");
        args.add("--release");
        args.add("11");

        int rc;
        try (javax.tools.StandardJavaFileManager fileManager =
                     javac.getStandardFileManager(diagnostics, Locale.ROOT,
                             java.nio.charset.StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromPaths(sources);
            JavaCompiler.CompilationTask task = javac.getTask(null, fileManager,
                    diagnostics, args, List.of(), units);
            boolean ok = Boolean.TRUE.equals(task.call());
            rc = ok ? 0 : 1;
        } finally {
            deleteRecursively(emptySourcepath);
        }

        List<String> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(format(d));
                if (errors.size() >= 100) {
                    errors.add("... y más errores (se muestran los primeros 100)");
                    break;
                }
            }
        }

        int classCount = countClasses(classesDir);
        if (rc != 0 && errors.isEmpty()) {
            errors.add("javac terminó con código " + rc + " sin diagnósticos detallados");
        }
        return new Result(rc == 0 && errors.isEmpty(), classCount, errors);
    }

    public static int countClasses(Path classesDir) throws IOException {
        if (!Files.isDirectory(classesDir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(classesDir)) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .count();
        }
    }

    public static List<Path> javaFilesUnder(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static String format(Diagnostic<? extends JavaFileObject> d) {
        String file = d.getSource() != null ? String.valueOf(d.getSource().toUri()) : "<desconocido>";
        long line = d.getLineNumber();
        String location = line > 0 ? file + ":" + line : file;
        return location + ": " + d.getMessage(Locale.ROOT);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
