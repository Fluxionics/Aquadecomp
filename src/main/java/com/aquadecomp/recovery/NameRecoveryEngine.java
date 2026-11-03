package com.aquadecomp.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class NameRecoveryEngine {

    private final Map<String, String> recoveredNames = new HashMap<>();
    private final Map<String, Integer> nameConfidence = new HashMap<>();

    private static final Pattern KOTLIN_METADATA = Pattern.compile(
            "Lkotlin/Metadata|@Metadata|Companion"
    );

    private static final Pattern SOURCE_FILE = Pattern.compile(
            "SourceFile|SourceDebugExtension"
    );

    private static final Pattern ENUM_PATTERN = Pattern.compile(
            "enum\\s+\\w+\\s*\\{|INSTANCE|\\$VALUES"
    );

    private static final Pattern LAMBDA_PATTERN = Pattern.compile(
            "lambda\\$|\\$\\$Lambda"
    );

    private static final Pattern RESOURCE_REFERENCE = Pattern.compile(
            "R\\.\\w+\\.|getResources\\(\\)|getString\\(|getColor\\("
    );

    private static final Pattern COMMON_NAMES = Pattern.compile(
            "(onCreate|onResume|onPause|onDestroy|onClick|onLongClick|" +
            "toString|equals|hashCode|compareTo|clone|wait|notify)"
    );

    public void recoverFromMapping(Path mappingFile) {
        if (!Files.exists(mappingFile)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(mappingFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.contains("->")) {
                    String[] parts = line.split("->");
                    if (parts.length == 2) {
                        String original = parts[0].trim();
                        String obfuscated = parts[1].trim().replaceAll("\\s*\\(.*", "");
                        recoveredNames.put(obfuscated, original);
                        nameConfidence.put(obfuscated, 100);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void recoverFromKotlinMetadata(String source, String className) {
        if (!KOTLIN_METADATA.matcher(source).find()) {
            return;
        }

        extractKotlinNames(source, className);
    }

    public void recoverFromSourceFile(String source, String className) {
        if (!SOURCE_FILE.matcher(source).find()) {
            return;
        }

        for (String method : extractMethodSignatures(source)) {
            if (!method.contains("$") && !method.matches("[a-z]") &&
                !method.matches("[a-z]{1,3}")) {
                int confidence = 85;
                if (COMMON_NAMES.matcher(method).find()) {
                    confidence = 90;
                }
                recoveredNames.putIfAbsent(method, method);
                nameConfidence.put(method, confidence);
            }
        }
    }

    public void recoverFromResourceReferences(String source) {
        java.util.regex.Matcher matcher = RESOURCE_REFERENCE.matcher(source);
        int confidence = 0;

        while (matcher.find()) {
            confidence = Math.min(75, confidence + 5);
            String match = matcher.group();

            if (match.contains("getString")) {
                extractPotentialStringName(source);
            } else if (match.contains("getColor")) {
                extractPotentialColorName(source);
            }
        }
    }

    public void recoverFromEnumPatterns(String source, String className) {
        if (!ENUM_PATTERN.matcher(source).find()) {
            return;
        }

        if (className.contains("Enum") || source.contains("enum ")) {
            String baseName = className.replaceAll("\\$.*", "");
            recoveredNames.putIfAbsent(baseName, baseName);
            nameConfidence.put(baseName, 85);

            for (String line : source.split("\n")) {
                if (line.contains("INSTANCE") || line.contains("VALUES")) {
                    String fieldName = extractFieldName(line);
                    if (fieldName != null && !fieldName.isEmpty()) {
                        recoveredNames.putIfAbsent(fieldName, fieldName);
                        nameConfidence.put(fieldName, 80);
                    }
                }
            }
        }
    }

    public void recoverFromCallContext(String source) {
        java.util.regex.Pattern methodCall = Pattern.compile(
                "\\.(\\w+)\\s*\\("
        );
        java.util.regex.Matcher matcher = methodCall.matcher(source);

        while (matcher.find()) {
            String methodName = matcher.group(1);
            if (isLikelyRealName(methodName)) {
                recoveredNames.putIfAbsent(methodName, methodName);
                nameConfidence.put(methodName, 70);
            }
        }
    }

    public String recover(String obfuscatedName) {
        String recovered = recoveredNames.getOrDefault(obfuscatedName, obfuscatedName);
        return recovered;
    }

    public int getConfidence(String name) {
        return nameConfidence.getOrDefault(name, 0);
    }

    public Map<String, String> getRecoveredNames() {
        return new HashMap<>(recoveredNames);
    }

    private void extractKotlinNames(String source, String className) {
        String[] lines = source.split("\n");
        for (String line : lines) {
            if (line.contains("fun ") || line.contains("val ") || line.contains("var ")) {
                String name = extractVariableName(line);
                if (name != null) {
                    recoveredNames.putIfAbsent(name, name);
                    nameConfidence.put(name, 90);
                }
            }
        }
    }

    private String[] extractMethodSignatures(String source) {
        java.util.regex.Pattern pattern = Pattern.compile("(public|private)\\s+\\w+\\s+(\\w+)\\s*\\(");
        java.util.regex.Matcher matcher = pattern.matcher(source);
        java.util.List<String> methods = new java.util.ArrayList<>();

        while (matcher.find()) {
            methods.add(matcher.group(2));
        }

        return methods.toArray(new String[0]);
    }

    private void extractPotentialStringName(String source) {
        java.util.regex.Pattern pattern = Pattern.compile("getString\\(R\\.string\\.(\\w+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            String name = matcher.group(1);
            recoveredNames.putIfAbsent(name, name);
            nameConfidence.put(name, 75);
        }
    }

    private void extractPotentialColorName(String source) {
        java.util.regex.Pattern pattern = Pattern.compile("getColor\\(R\\.color\\.(\\w+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            String name = matcher.group(1);
            recoveredNames.putIfAbsent(name, name);
            nameConfidence.put(name, 75);
        }
    }

    private String extractFieldName(String line) {
        java.util.regex.Pattern pattern = Pattern.compile("\\b(\\w+)\\s*=\\s*(INSTANCE|VALUES|\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractVariableName(String line) {
        java.util.regex.Pattern pattern = Pattern.compile("(val|var)\\s+(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    private boolean isLikelyRealName(String name) {
        if (name.length() <= 2) {
            return false;
        }

        if (name.matches("^[a-z]+$") && name.length() > 3) {
            return true;
        }

        if (name.matches("^[a-z][a-zA-Z0-9]*$")) {
            return true;
        }

        return COMMON_NAMES.matcher(name).find();
    }
}
