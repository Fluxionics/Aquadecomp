package com.aquadecomp.verification;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CompilationVerifier {

    private static final String ANDROID_JAR_PATH = "/android.jar";
    private static final long VERIFICATION_TIMEOUT_MS = 30000;

    public VerificationResult verify(Path javaFile) {
        try {
            String source = Files.readString(javaFile);
            
            if (source.contains("throw new UnsupportedOperationException")) {
                return new VerificationResult(false, 0, List.of("UnsupportedOperationException"), false);
            }

            if (source.contains("/* missing */") || source.contains("/* unknown */")) {
                return new VerificationResult(false, -25, List.of("Missing code blocks"), true);
            }

            if (source.contains("cannot find symbol") || source.contains("incompatible types")) {
                return new VerificationResult(false, -20, List.of("Compilation errors"), false);
            }

            List<String> issues = new ArrayList<>();
            int penalty = 0;

            if (source.contains("import")) {
                try {
                    String[] imports = extractImports(source);
                    for (String imp : imports) {
                        if (isProbableUnresolvedClass(imp)) {
                            penalty -= 5;
                            issues.add("Posible clase no resuelta: " + imp);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            boolean hasMethod = source.contains("public ") || source.contains("private ");
            boolean hasConstructor = source.contains("public " + extractClassName(javaFile)) 
                    || source.contains("new ");
            boolean isValidStructure = hasMethod || hasConstructor;

            return new VerificationResult(
                    isValidStructure,
                    penalty,
                    issues,
                    false
            );

        } catch (Exception e) {
            return new VerificationResult(false, -15, List.of("Error al verificar: " + e.getMessage()), false);
        }
    }

    private String[] extractImports(String source) {
        List<String> imports = new ArrayList<>();
        for (String line : source.split("\n")) {
            line = line.trim();
            if (line.startsWith("import ") && line.endsWith(";")) {
                String pkg = line.substring(7, line.length() - 1);
                imports.add(pkg);
            }
        }
        return imports.toArray(new String[0]);
    }

    private boolean isProbableUnresolvedClass(String importPath) {
        String[] problematicPrefixes = {
            "java.lang.reflect",
            "sun.reflect",
            "dalvik",
            "$Proxy"
        };

        for (String prefix : problematicPrefixes) {
            if (importPath.contains(prefix)) {
                return true;
            }
        }

        return false;
    }

    private String extractClassName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.lastIndexOf('.'));
    }

    public record VerificationResult(
            boolean isStructurallyValid,
            int scoreAdjustment,
            List<String> issues,
            boolean hasSynthetic
    ) {
        public boolean shouldRetry() {
            return !isStructurallyValid && !hasSynthetic;
        }
    }
}
