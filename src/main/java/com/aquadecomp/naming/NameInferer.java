package com.aquadecomp.naming;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class NameInferer {

    public static final int MAX_CONFIDENCE = 95;

    private static final Pattern CLASS_DECL = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|protected|private|final|abstract|static|strictfp)\\s+)*"
                    + "(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)([^\\n{]*)");

    private static final Pattern EXTENDS =
            Pattern.compile("\\bextends\\s+([\\w.$]+)");

    private static final Pattern IMPLEMENTS =
            Pattern.compile("\\bimplements\\s+([\\w.$,\\s]+)");

    private static final Pattern FIELD = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|transient|volatile)"
                    + "\\s+)+([\\w.$]+(?:<[^>]+>)?(?:\\[\\])*)\\s+([A-Za-z_$][\\w$]*)"
                    + "\\s*(?:=[^;]*)?;[ \\t]*$");

    private static final Pattern METHOD = Pattern.compile(
            "(?m)^[ \\t]*(?:@[A-Za-z_$][\\w$]*(?:\\([^)]*\\))?\\s+)*"
                    + "(?:(?:public|protected|private|static|final|abstract|synchronized|"
                    + "native|default|strictfp)\\s+)*"
                    + "([\\w.$]+(?:<[^>]+>)?(?:\\[\\])*)\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)"
                    + "\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*[{;][ \\t]*$");

    private static final Map<String, String> OVERRIDE_NAMES = Map.ofEntries(
            Map.entry("void(View)", "onClick"),
            Map.entry("void(Context,BroadcastReceiver)", "onReceive"),
            Map.entry("void(BroadcastReceiver,Context)", "onReceive"),
            Map.entry("boolean(Object)", "equals"),
            Map.entry("String()", "toString"));

    private static final List<String> COLLECTION_TYPES = List.of(
            "List", "ArrayList", "LinkedList", "Vector", "Map", "HashMap",
            "LinkedHashMap", "TreeMap", "Set", "HashSet", "LinkedHashSet",
            "Collection", "Iterable", "Queue", "Deque", "SparseArray");

    private static final List<String> NOTES = List.of(
            "Heurística basada en reglas fijas, no en aprendizaje automático.",
            "Solo se sugiere para identificadores que parecen ofuscados "
                    + "(una o dos letras o letras con dígitos).",
            "No se modifica ningún código: revisa cada sugerencia antes de aplicarla.",
            "La confianza refuerza la señal detectada, no garantiza que el nombre "
                    + "sea el original.");

    private NameInferer() {
    }

    public static NameReport infer(Path sourcesDir) throws IOException {
        if (!Files.isDirectory(sourcesDir)) {
            throw new IOException("No existe un directorio de fuentes en: " + sourcesDir);
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourcesDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().endsWith(".bak"))
                    .sorted()
                    .toList();
        }

        List<NameSuggestion> suggestions = new ArrayList<>();
        for (Path file : files) {
            String content;
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            String relative = sourcesDir.relativize(file).toString().replace('\\', '/');
            suggestions.addAll(analyze(content, relative));
        }
        return new NameReport(sourcesDir.toAbsolutePath().toString(), files.size(),
                suggestions, NOTES);
    }

    static List<NameSuggestion> analyze(String source, String file) {
        List<NameSuggestion> out = new ArrayList<>();
        inferClasses(source, file, out);
        inferFields(source, file, out);
        inferMethods(source, file, out);
        return out;
    }

    private static void inferClasses(String source, String file,
                                     List<NameSuggestion> out) {
        Matcher matcher = CLASS_DECL.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!looksObfuscated(name)) {
                continue;
            }
            String header = matcher.group(2);
            List<String> signals = new ArrayList<>();
            Integer confidence = null;
            String suggested = null;

            Matcher extendsMatcher = EXTENDS.matcher(header);
            if (extendsMatcher.find()) {
                String parent = simpleName(extendsMatcher.group(1));
                if (parent.endsWith("Activity") && !looksObfuscated(parent)) {
                    suggested = "Actividad";
                    confidence = 55;
                    signals.add("hereda de " + parent);
                }
            }
            Matcher implementsMatcher = IMPLEMENTS.matcher(header);
            if (implementsMatcher.find()) {
                for (String iface : implementsMatcher.group(1).split(",")) {
                    String simple = simpleName(iface.trim());
                    if (simple.equals("Runnable") && suggested == null) {
                        suggested = "TareaFondo";
                        confidence = 65;
                        signals.add("implementa Runnable");
                    } else if (simple.endsWith("OnClickListener") && suggested == null) {
                        suggested = "ManejadorClick";
                        confidence = 60;
                        signals.add("implementa " + simple);
                    }
                }
            }
            if (suggested != null) {
                out.add(new NameSuggestion(file, "clase", name, suggested,
                        clamp(confidence), signals));
            }
        }
    }

    private static void inferFields(String source, String file, List<NameSuggestion> out) {
        Matcher matcher = FIELD.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(2);
            if (!looksObfuscated(name)) {
                continue;
            }
            String type = simpleType(matcher.group(1));
            List<String> signals = new ArrayList<>();
            String suggested = null;
            Integer confidence = null;

            if (type.equals("int") || type.equals("long")) {
                if (source.contains(name + "++") || source.contains(name + " +=")
                        || source.contains(name + "+=")) {
                    suggested = "contador";
                    confidence = 45;
                    signals.add("de tipo " + type + " y se incrementa en el código");
                } else if (matcher.group(0).matches("(?s).*=\\s*-?\\d+\\s*;.*")) {
                    suggested = "valor";
                    confidence = 30;
                    signals.add("de tipo " + type + " con valor inicial numérico");
                }
            } else if (type.equals("String")) {
                if (source.contains(name + ".length()")) {
                    suggested = "texto";
                    confidence = 45;
                    signals.add("de tipo String y se usa con length()");
                } else {
                    suggested = "texto";
                    confidence = 35;
                    signals.add("de tipo String");
                }
            } else if (COLLECTION_TYPES.contains(type)) {
                suggested = "elementos";
                confidence = 40;
                signals.add("de tipo colección " + type);
            } else if (matcher.group(1).endsWith("[]")) {
                suggested = "valores";
                confidence = 40;
                signals.add("es un arreglo");
            }

            if (suggested != null) {
                out.add(new NameSuggestion(file, "campo", name, suggested,
                        clamp(confidence), signals));
            }
        }
    }

    private static void inferMethods(String source, String file, List<NameSuggestion> out) {
        boolean listenerAttached = source.contains("setOnClickListener(this)");
        Matcher matcher = METHOD.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(2);
            if (!looksObfuscated(name)) {
                continue;
            }
            String returnType = simpleType(matcher.group(1));
            List<String> params = parameterTypes(matcher.group(3));
            boolean overridden = matcher.group(0).contains("@Override")
                    || hasOverrideAnnotation(source, matcher.start());

            String key = returnType + "(" + String.join(",", params) + ")";
            List<String> signals = new ArrayList<>();
            String suggested = OVERRIDE_NAMES.get(key);
            Integer confidence = null;

            if (suggested != null && overridden) {
                signals.add("con @Override y firma propia de " + suggested);
                confidence = suggested.equals("toString") ? 80 : 88;
            } else if (suggested != null && suggested.equals("onClick")
                    && listenerAttached && params.equals(List.of("View"))) {
                suggested = "onClick";
                signals.add("la clase se registra como OnClickListener y el método "
                        + "recibe un View");
                confidence = 85;
            } else {
                suggested = null;
            }

            if (suggested != null) {
                out.add(new NameSuggestion(file, "metodo", name, suggested,
                        clamp(confidence), signals));
            }
        }
    }

    static boolean looksObfuscated(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (RESERVED.contains(name)) {
            return false;
        }
        return name.matches("[a-z]")
                || name.matches("[a-z]\\d{1,3}")
                || name.matches("[a-z]{2}\\d+")
                || name.matches("[a-z]\\d+[a-z]");
    }

    private static final List<String> RESERVED = List.of(
            "this", "super", "class", "int", "new", "void", "long", "byte",
            "char", "case", "true", "false", "null", "name", "size", "type",
            "data", "text", "value", "index", "count", "flag", "mode", "tag");

    private static boolean hasOverrideAnnotation(String source, int methodStart) {
        String before = source.substring(0, methodStart);
        int idx = before.lastIndexOf("@Override");
        if (idx < 0) {
            return false;
        }
        String between = before.substring(idx);
        return !between.contains("}") && !between.contains(";");
    }

    private static List<String> parameterTypes(String params) {
        String cleaned = params.replaceAll("<[^>]*>", "");
        List<String> types = new ArrayList<>();
        if (cleaned.isBlank()) {
            return types;
        }
        for (String param : cleaned.split(",")) {
            String trimmed = param.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int space = trimmed.lastIndexOf(' ');
            String type = space > 0 ? trimmed.substring(0, space) : trimmed;
            types.add(simpleType(type));
        }
        return types;
    }

    private static String simpleType(String type) {
        String cleaned = type.replaceAll("<[^>]*>", "");
        cleaned = cleaned.replaceAll("\\[\\]", "");
        return simpleName(cleaned);
    }

    private static String simpleName(String type) {
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    private static int clamp(int confidence) {
        return Math.max(0, Math.min(MAX_CONFIDENCE, confidence));
    }
}
