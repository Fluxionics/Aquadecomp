package com.aquadecomp.recovery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class MetadataExtractor {

    private final Map<String, Metadata> extractedMetadata = new HashMap<>();
    private static final Pattern ANNOTATION = Pattern.compile(
            "@([a-zA-Z0-9.]+)(?:\\([^)]*\\))?"
    );
    private static final Pattern KOTLIN_METADATA = Pattern.compile(
            "@Metadata\\s*\\(([^)]+)\\)"
    );
    private static final Pattern SOURCE_FILE = Pattern.compile(
            "SourceFile\\s*=\\s*[\"']([^\"']+)[\"']"
    );

    public void extract(String source, String className) {
        extractAnnotations(source, className);
        extractKotlinMetadata(source, className);
        extractSourceFileInfo(source, className);
    }

    private void extractAnnotations(String source, String className) {
        java.util.regex.Matcher matcher = ANNOTATION.matcher(source);
        Set<String> annotations = new HashSet<>();

        while (matcher.find()) {
            try {
                String annotation = matcher.group(1);
                annotations.add(annotation);
            } catch (Exception ignored) {
            }
        }

        Metadata metadata = extractedMetadata.computeIfAbsent(
                className,
                k -> new Metadata(className)
        );
        metadata.addAnnotations(annotations);
    }

    private void extractKotlinMetadata(String source, String className) {
        java.util.regex.Matcher matcher = KOTLIN_METADATA.matcher(source);

        if (matcher.find()) {
            try {
                String metadataStr = matcher.group(1);
                Metadata metadata = extractedMetadata.computeIfAbsent(
                        className,
                        k -> new Metadata(className)
                );
                metadata.setIsKotlin(true);
                metadata.setKotlinMetadata(metadataStr);
            } catch (Exception ignored) {
            }
        }
    }

    private void extractSourceFileInfo(String source, String className) {
        java.util.regex.Matcher matcher = SOURCE_FILE.matcher(source);

        if (matcher.find()) {
            try {
                String sourceFile = matcher.group(1);
                Metadata metadata = extractedMetadata.computeIfAbsent(
                        className,
                        k -> new Metadata(className)
                );
                metadata.setSourceFile(sourceFile);
            } catch (Exception ignored) {
            }
        }
    }

    public Metadata getMetadata(String className) {
        return extractedMetadata.get(className);
    }

    public Map<String, Metadata> getAllMetadata() {
        return new HashMap<>(extractedMetadata);
    }

    public static final class Metadata {
        private final String className;
        private boolean isKotlin = false;
        private String kotlinMetadata = null;
        private String sourceFile = null;
        private final Set<String> annotations = new HashSet<>();
        private final Map<String, String> generics = new HashMap<>();

        public Metadata(String className) {
            this.className = className;
        }

        public String className() {
            return className;
        }

        public boolean isKotlin() {
            return isKotlin;
        }

        public void setIsKotlin(boolean isKotlin) {
            this.isKotlin = isKotlin;
        }

        public String kotlinMetadata() {
            return kotlinMetadata;
        }

        public void setKotlinMetadata(String kotlinMetadata) {
            this.kotlinMetadata = kotlinMetadata;
        }

        public String sourceFile() {
            return sourceFile;
        }

        public void setSourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
        }

        public Set<String> annotations() {
            return new HashSet<>(annotations);
        }

        public void addAnnotations(Set<String> annotations) {
            this.annotations.addAll(annotations);
        }

        public Map<String, String> generics() {
            return new HashMap<>(generics);
        }

        public void addGeneric(String typeVar, String bound) {
            generics.put(typeVar, bound);
        }
    }
}
