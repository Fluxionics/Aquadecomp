package com.aquadecomp.core;

import jadx.api.JavaClass;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ObfuscationDetector {

    private static final Set<String> LEGIT_SHORT = Set.of(

            "is", "in", "on", "of", "at", "to", "or", "if", "do", "go", "up",
            "no", "so", "by", "as", "be", "it", "an", "id", "ok", "get", "set",
            "add", "put", "run", "try", "new", "use", "log", "max", "min",
            "and", "not", "xor", "all", "any", "end", "top", "map", "key",
            "val", "has", "own", "for", "are", "can", "may", "one", "two",
            "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
            "onClick", "onCreateView", "toString", "hashCode", "equals",
            "main", "init", "draw", "read", "write", "open", "close", "next",
            "prev", "size", "name", "data", "type", "code", "from", "with",
            "apply", "build", "make", "copy", "move", "load", "save", "parse"
    );

    private static final Pattern P_1_3_LETTERS = Pattern.compile("[a-zA-Z]{1,3}");
    private static final Pattern P_LETTER_DIGITS = Pattern.compile("[a-zA-Z]{1,2}\\d{1,4}");
    private static final Pattern P_ZZ = Pattern.compile("z{1,4}[a-zA-Z0-9]{0,4}");
    private static final Pattern P_DOLLAR = Pattern.compile("[a-zA-Z]{1,2}(\\$[a-zA-Z0-9]{1,2})+");
    private static final Pattern P_HEXISH = Pattern.compile("[a-fA-F0-9]{6,}");
    private static final Pattern P_PACKAGES = Pattern.compile("[a-zA-Z]{1,2}");
    private static final Pattern ANON_CLASS = Pattern.compile("\\$\\d+");

    public record Renames(Set<String> targets, int classes, int methods,
                          int fields, int packages) {
        public int totalIds() {
            return classes + methods + fields + packages;
        }
    }

    public static Renames loadRenames(Path jobf) {
        Set<String> targets = new HashSet<>();
        int c = 0, m = 0, f = 0, p = 0;
        if (!Files.isRegularFile(jobf)) return new Renames(targets, 0, 0, 0, 0);
        try {
            for (String line : Files.readAllLines(jobf)) {
                if (line.length() < 4) continue;
                char kind = line.charAt(0);
                if (kind != 'c' && kind != 'm' && kind != 'f' && kind != 'p') continue;
                int idx = line.lastIndexOf(" = ");
                if (idx < 0 || idx < 3) continue;
                String original = line.substring(2, idx);
                String target = line.substring(idx + 3).trim();
                if (target.isEmpty()) continue;
                targets.add(target);
                if (renamedMeaningfulName(kind, original)) {
                    switch (kind) {
                        case 'c' -> c++;
                        case 'm' -> m++;
                        case 'f' -> f++;
                        default -> p++;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return new Renames(targets, c, m, f, p);
    }

    private static boolean renamedMeaningfulName(char kind, String original) {
        switch (kind) {
            case 'c' -> {
                String simple = original.substring(original.lastIndexOf('.') + 1);
                if (simple.equals("R") || simple.startsWith("R$")
                        || simple.equals("BuildConfig") || simple.equals("Manifest")) return false;
                if (simple.contains("$$")) return false;
                return !ANON_CLASS.matcher(simple).find();
            }
            case 'm' -> {
                int paren = original.indexOf('(');
                String head = paren > 0 ? original.substring(0, paren) : original;
                int lastDot = head.lastIndexOf('.');
                String owner = head.substring(0, lastDot);
                String name = head.substring(lastDot + 1);
                if (owner.contains("$$")) return false;
                if (name.startsWith("lambda$") || name.startsWith("invoke$")) return false;
                return !looksObfuscated(name);
            }
            case 'f' -> {
                String name = original.substring(original.lastIndexOf('.') + 1);
                return !name.startsWith("this$") && !name.startsWith("val$")
                        && !name.startsWith("type$");
            }
            default -> {
                return true;
            }
        }
    }

    public static ObfuscationInfo analyze(List<JavaClass> classes, Renames renames) {
        int totalClasses = 0, obfClasses = 0;
        int totalMethods = 0, obfMethods = 0;
        int totalFields = 0, obfFields = 0;
        int obfPackages = 0, totalPackages = 0;
        List<String> signals = new ArrayList<>();
        Set<String> substitutedNames = renames.targets();

        Set<String> seenPackages = new java.util.TreeSet<>();
        Set<String> obfSeenPackages = new java.util.TreeSet<>();

        for (JavaClass jc : classes) {
            ClassNode cn = jc.getClassNode();
            if (cn == null) continue;

            totalClasses++;
            String simple = cn.getName();
            if (!substitutedNames.contains(simple) && looksObfuscated(simple)) obfClasses++;

            String fullName = cn.getFullName();
            int lastDot = fullName.lastIndexOf('.');
            if (lastDot > 0) {
                String pkg = fullName.substring(0, lastDot);
                if (seenPackages.add(pkg)) {
                    totalPackages++;

                    String[] segs = pkg.split("\\.");
                    boolean allShort = segs.length > 0;
                    for (String s : segs) {
                        if (!P_PACKAGES.matcher(s).matches()) {
                            allShort = false;
                            break;
                        }
                    }
                    if (allShort) obfSeenPackages.add(pkg);
                }
            }

            for (MethodNode m : cn.getMethods()) {
                totalMethods++;
                if (!substitutedNames.contains(m.getName()) && looksObfuscated(m.getName())) obfMethods++;
            }
            for (FieldNode f : cn.getFields()) {
                totalFields++;
                String n = f.getName();

                if (n.startsWith("this$") || n.startsWith("val$") || n.startsWith("type$")) continue;
                if (!substitutedNames.contains(n) && looksObfuscated(n)) obfFields++;
            }
        }
        obfPackages = obfSeenPackages.size();

        if (totalClasses == 0) {
            return ObfuscationInfo.unknown();
        }

        double classRatio = (double) obfClasses / totalClasses;
        double methodRatio = totalMethods == 0 ? 0 : (double) obfMethods / totalMethods;
        double fieldRatio = totalFields == 0 ? 0 : (double) obfFields / totalFields;
        double pkgRatio = totalPackages == 0 ? 0 : (double) obfPackages / totalPackages;
        double ratio = 0.35 * classRatio + 0.40 * methodRatio + 0.15 * fieldRatio + 0.10 * pkgRatio;

        double substClassRatio = totalClasses == 0 ? 0 : (double) renames.classes() / totalClasses;
        double substMethodRatio = totalMethods == 0 ? 0 : (double) renames.methods() / totalMethods;
        double substFieldRatio = totalFields == 0 ? 0 : (double) renames.fields() / totalFields;
        double substPkgRatio = totalPackages == 0 ? 0 : (double) renames.packages() / totalPackages;
        double substitutedRatio = Math.min(1.0,
                0.35 * substClassRatio + 0.40 * substMethodRatio
                        + 0.15 * substFieldRatio + 0.10 * substPkgRatio);
        int substitutedIds = renames.totalIds();

        String level;
        if (ratio < 0.05) level = "NONE";
        else if (ratio < 0.30) level = "LOW";
        else if (ratio < 0.70) level = "MEDIUM";
        else level = "HIGH";

        signals.add(String.format(Locale.ROOT,
                "%.1f%% de clases con nombres de apariencia ofuscada (%d/%d)",
                classRatio * 100, obfClasses, totalClasses));
        signals.add(String.format(Locale.ROOT,
                "%.1f%% de métodos ofuscados (%d/%d)",
                methodRatio * 100, obfMethods, totalMethods));
        if (totalFields > 0) {
            signals.add(String.format(Locale.ROOT,
                    "%.1f%% de campos ofuscados (%d/%d)",
                    fieldRatio * 100, obfFields, totalFields));
        }
        if (substitutedIds > 0) {
            signals.add(String.format(Locale.ROOT,
                    "%d identificadores renombrados por la desofuscación: son sustitutos "
                            + "generados, no los nombres originales (clases %d, métodos %d, "
                            + "campos %d, paquetes %d)",
                    substitutedIds, renames.classes(), renames.methods(),
                    renames.fields(), renames.packages()));
        }
        if (obfPackages > 0) {
            signals.add(String.format(Locale.ROOT,
                    "%d paquetes con nombres de 1-2 letras (p. ej. a.b.c)", obfPackages));
        }
        if (level.equals("NONE")) {
            signals.add("No se detectaron patrones de renombre masivo");
        }

        return new ObfuscationInfo(totalClasses, obfClasses, totalMethods, obfMethods,
                totalFields, obfFields, obfPackages, substitutedIds, ratio, substitutedRatio,
                level, List.copyOf(signals), false);
    }

    public static ObfuscationInfo withStringEncryption(ObfuscationInfo info, boolean likely) {
        if (!likely) return info;
        List<String> sig = new ArrayList<>(info.signals());
        sig.add("Patrones de encriptación de strings detectados en el código "
                + "(Base64 + Cipher): las cadenas pueden estar cifradas en runtime");
        return new ObfuscationInfo(info.totalClasses(), info.obfuscatedClasses(),
                info.totalMethods(), info.obfuscatedMethods(),
                info.totalFields(), info.obfuscatedFields(),
                info.obfuscatedPackages(), info.substitutedIds(),
                info.obfuscatedRatio(), info.substitutedRatio(),
                info.level().equals("NONE") ? "LOW" : info.level(),
                List.copyOf(sig), true);
    }

    static boolean looksObfuscated(String name) {
        if (name == null || name.isEmpty()) return true;
        String n = name;

        if (n.equals("R") || n.equals("BuildConfig") || n.equals("Manifest")
                || n.equals("package-info")) {
            return false;
        }
        if (LEGIT_SHORT.contains(n) || LEGIT_SHORT.contains(n.toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (n.startsWith("Composable") || n.startsWith("invoke$")
                || n.startsWith("lambda$") || n.endsWith("$lambda")) {
            return false;
        }
        if (P_1_3_LETTERS.matcher(n).matches()) return true;
        if (P_LETTER_DIGITS.matcher(n).matches()) return true;
        if (P_ZZ.matcher(n).matches()) return true;
        if (P_DOLLAR.matcher(n).matches()) return true;
        if (P_HEXISH.matcher(n).matches()) return true;
        return false;
    }

    private ObfuscationDetector() {
    }
}
