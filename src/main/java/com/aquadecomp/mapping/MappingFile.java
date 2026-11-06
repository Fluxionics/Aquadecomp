package com.aquadecomp.mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MappingFile {

    public enum Direction {
        REVERSE, FORWARD
    }

    public static final String COPIED_NAME = "aquedecomp-mapping.txt";

    private static final Map<String, String> PRIMITIVES = Map.ofEntries(
            Map.entry("void", "V"), Map.entry("boolean", "Z"), Map.entry("byte", "B"),
            Map.entry("char", "C"), Map.entry("short", "S"), Map.entry("int", "I"),
            Map.entry("long", "J"), Map.entry("float", "F"), Map.entry("double", "D"));

    public record RenameTable(Map<String, String> types,
                              Map<String, String> methodsByProto,
                              Map<String, String> methodsByName,
                              Set<String> ambiguousMethods,
                              Map<String, String> fieldsByType,
                              Map<String, String> fieldsByName,
                              Set<String> ambiguousFields) {

        public String methodName(String definingClass, String name,
                                 List<? extends CharSequence> parameterTypes,
                                 String returnType) {
            String proto = definingClass + "->" + name + "("
                    + String.join("", parameterTypes) + ")" + returnType;
            String exact = methodsByProto.get(proto);
            if (exact != null) {
                return exact;
            }
            String nameKey = definingClass + "#" + name;
            if (ambiguousMethods.contains(nameKey)) {
                return name;
            }
            String fallback = methodsByName.get(nameKey);
            return fallback != null ? fallback : name;
        }

        public String fieldName(String definingClass, String name, String type) {
            String exact = fieldsByType.get(definingClass + "#" + name + ":" + type);
            if (exact != null) {
                return exact;
            }
            String nameKey = definingClass + "#" + name;
            if (ambiguousFields.contains(nameKey)) {
                return name;
            }
            String fallback = fieldsByName.get(nameKey);
            return fallback != null ? fallback : name;
        }
    }

    private record TypePair(String obf, String orig) {
    }

    private record MethodEntry(String obfClass, String origClass, String origName,
                               String obfName, List<TypePair> params, TypePair returnType) {
    }

    private record FieldEntry(String obfClass, String origClass, String origName,
                              String obfName, TypePair type) {
    }

    private final Map<String, String> obfToOrig;
    private final Map<String, String> origToObf;
    private final List<MethodEntry> methods;
    private final List<FieldEntry> fields;
    private final Map<Direction, RenameTable> cache = new HashMap<>();

    private MappingFile(Map<String, String> obfToOrig, List<MethodEntry> methods,
                        List<FieldEntry> fields) {
        this.obfToOrig = obfToOrig;
        this.origToObf = new LinkedHashMap<>();
        obfToOrig.forEach((obf, orig) -> this.origToObf.put(orig, obf));
        this.methods = methods;
        this.fields = fields;
    }

    public static MappingFile parse(Path file) throws IOException {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("No se pudo leer el mapping: " + file
                    + " (" + e.getMessage() + ")", e);
        }
        MappingFile mapping = parse(lines);
        if (mapping.classCount() == 0) {
            throw new IOException("El archivo no contiene entradas de mapping válidas: " + file);
        }
        return mapping;
    }

    public static MappingFile parse(List<String> lines) {
        Map<String, String> obfToOrig = new LinkedHashMap<>();
        for (String line : lines) {
            String[] pair = classPair(line);
            if (pair != null) {
                obfToOrig.put(pair[1], pair[0]);
            }
        }
        MappingFile mapping = new MappingFile(obfToOrig, new ArrayList<>(), new ArrayList<>());
        mapping.parseMembers(lines);
        return mapping;
    }

    public int classCount() {
        return obfToOrig.size();
    }

    public int memberCount() {
        return methods.size() + fields.size();
    }

    public RenameTable renameTable(Direction direction) {
        return cache.computeIfAbsent(direction, dir -> buildTable(dir == Direction.FORWARD));
    }

    private void parseMembers(List<String> lines) {
        String currentObf = null;
        String currentOrig = null;
        for (String line : lines) {
            if (line.isBlank() || line.charAt(0) == '#') {
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0))) {
                String[] pair = classPair(line);
                if (pair != null) {
                    currentOrig = pair[0];
                    currentObf = pair[1];
                }
                continue;
            }
            if (currentObf == null) {
                continue;
            }
            String text = line.trim();
            if (text.isEmpty() || text.charAt(0) == '#') {
                continue;
            }
            int arrow = text.indexOf(" -> ");
            if (arrow <= 0) {
                continue;
            }
            String obfName = text.substring(arrow + 4).trim();
            if (obfName.isEmpty() || obfName.indexOf(':') >= 0) {
                continue;
            }
            addMember(currentObf, currentOrig, stripLineInfo(text.substring(0, arrow)), obfName);
        }
    }

    private void addMember(String obfClass, String origClass, String left, String obfName) {
        int paren = left.indexOf('(');
        if (paren >= 0) {
            int close = left.indexOf(')', paren + 1);
            if (close < 0) {
                return;
            }
            String head = left.substring(0, paren).trim();
            int space = head.lastIndexOf(' ');
            if (space <= 0) {
                return;
            }
            String name = head.substring(space + 1).trim();
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                name = name.substring(dot + 1);
            }
            if (name.isEmpty()) {
                return;
            }
            String returnType = head.substring(0, space).trim();
            String paramsRaw = left.substring(paren + 1, close).trim();
            List<TypePair> params = new ArrayList<>();
            if (!paramsRaw.isEmpty()) {
                for (String param : paramsRaw.split(",")) {
                    params.add(resolveType(param));
                }
            }
            methods.add(new MethodEntry(desc(obfClass), desc(origClass), name, obfName,
                    params, resolveType(returnType)));
            return;
        }
        int space = left.lastIndexOf(' ');
        if (space <= 0) {
            return;
        }
        String type = left.substring(0, space).trim();
        String name = left.substring(space + 1).trim();
        if (name.isEmpty()) {
            return;
        }
        fields.add(new FieldEntry(desc(obfClass), desc(origClass), name, obfName,
                resolveType(type)));
    }

    private TypePair resolveType(String raw) {
        String type = raw.trim();
        int arrays = 0;
        while (type.endsWith("[]")) {
            arrays++;
            type = type.substring(0, type.length() - 2).trim();
        }
        if (type.length() > 2 && type.charAt(0) == 'L'
                && type.charAt(type.length() - 1) == ';') {
            type = type.substring(1, type.length() - 1).replace('/', '.');
        }
        String prefix = "[".repeat(arrays);
        if (PRIMITIVES.containsKey(type)) {
            String primitive = PRIMITIVES.get(type);
            return new TypePair(prefix + primitive, prefix + primitive);
        }
        String dotted = type.replace('/', '.');
        String obf = dotted;
        String orig = dotted;
        String restored = obfToOrig.get(dotted);
        if (restored != null) {
            orig = restored;
        } else {
            String obfuscated = origToObf.get(dotted);
            if (obfuscated != null) {
                obf = obfuscated;
            }
        }
        return new TypePair(prefix + desc(obf), prefix + desc(orig));
    }

    private RenameTable buildTable(boolean forward) {
        Map<String, String> types = new HashMap<>();
        for (Map.Entry<String, String> entry : obfToOrig.entrySet()) {
            String obf = desc(entry.getKey());
            String orig = desc(entry.getValue());
            types.put(forward ? orig : obf, forward ? obf : orig);
        }

        Map<String, String> methodProtos = new HashMap<>();
        Map<String, String> methodNames = new HashMap<>();
        Set<String> ambiguousMethodNames = new HashSet<>();
        Set<String> ambiguousMethodProtos = new HashSet<>();
        for (MethodEntry method : methods) {
            String definingClass = forward ? method.origClass() : method.obfClass();
            String name = forward ? method.origName() : method.obfName();
            String target = forward ? method.obfName() : method.origName();
            String params = (forward
                    ? method.params().stream().map(TypePair::orig)
                    : method.params().stream().map(TypePair::obf))
                    .collect(Collectors.joining());
            String returnType = forward ? method.returnType().orig() : method.returnType().obf();
            addEntry(methodProtos, ambiguousMethodProtos,
                    definingClass + "->" + name + "(" + params + ")" + returnType, target);
            addEntry(methodNames, ambiguousMethodNames, definingClass + "#" + name, target);
        }

        Map<String, String> fieldTypes = new HashMap<>();
        Map<String, String> fieldNames = new HashMap<>();
        Set<String> ambiguousFieldNames = new HashSet<>();
        Set<String> ambiguousFieldTypes = new HashSet<>();
        for (FieldEntry field : fields) {
            String definingClass = forward ? field.origClass() : field.obfClass();
            String name = forward ? field.origName() : field.obfName();
            String target = forward ? field.obfName() : field.origName();
            String type = forward ? field.type().orig() : field.type().obf();
            addEntry(fieldTypes, ambiguousFieldTypes,
                    definingClass + "#" + name + ":" + type, target);
            addEntry(fieldNames, ambiguousFieldNames, definingClass + "#" + name, target);
        }

        return new RenameTable(types, methodProtos, methodNames, ambiguousMethodNames,
                fieldTypes, fieldNames, ambiguousFieldNames);
    }

    private static void addEntry(Map<String, String> map, Set<String> ambiguous,
                                 String key, String value) {
        String existing = map.get(key);
        if (existing != null) {
            if (!existing.equals(value)) {
                map.remove(key);
                ambiguous.add(key);
            }
            return;
        }
        if (!ambiguous.contains(key)) {
            map.put(key, value);
        }
    }

    private static String[] classPair(String line) {
        if (line.isBlank() || line.charAt(0) == '#'
                || Character.isWhitespace(line.charAt(0))) {
            return null;
        }
        int arrow = line.indexOf(" -> ");
        if (arrow <= 0) {
            return null;
        }
        String right = line.substring(arrow + 4).trim();
        if (!right.endsWith(":")) {
            return null;
        }
        String orig = line.substring(0, arrow).trim();
        String obf = right.substring(0, right.length() - 1).trim();
        if (orig.isEmpty() || obf.isEmpty()) {
            return null;
        }
        return new String[]{orig, obf};
    }

    private static String stripLineInfo(String value) {
        String text = value.trim();
        while (true) {
            int i = 0;
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                i++;
            }
            if (i == 0 || i >= text.length() || text.charAt(i) != ':') {
                return text;
            }
            int j = i + 1;
            while (j < text.length() && Character.isDigit(text.charAt(j))) {
                j++;
            }
            if (j >= text.length() || text.charAt(j) != ':') {
                return text;
            }
            text = text.substring(j + 1).trim();
        }
    }

    private static String desc(String dotted) {
        String primitive = PRIMITIVES.get(dotted);
        if (primitive != null) {
            return primitive;
        }
        return "L" + dotted.replace('.', '/') + ";";
    }
}
