package com.aquadecomp.core;

import jadx.api.JavaClass;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;

import java.util.ArrayList;
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

    public static ObfuscationInfo analyze(List<JavaClass> classes) {
        int totalClasses = 0, obfClasses = 0;
        int totalMethods = 0, obfMethods = 0;
        int totalFields = 0, obfFields = 0;
        int obfPackages = 0, totalPackages = 0;
        List<String> signals = new ArrayList<>();

        Set<String> seenPackages = new java.util.TreeSet<>();
        Set<String> obfSeenPackages = new java.util.TreeSet<>();

        for (JavaClass jc : classes) {
            ClassNode cn = jc.getClassNode();
            if (cn == null) continue;

            totalClasses++;
            String simple = cn.getName();
            if (looksObfuscated(simple)) obfClasses++;

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
                if (looksObfuscated(m.getName())) obfMethods++;
            }
            for (FieldNode f : cn.getFields()) {
                totalFields++;
                String n = f.getName();

                if (n.startsWith("this$") || n.startsWith("val$") || n.startsWith("type$")) continue;
                if (looksObfuscated(n)) obfFields++;
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
        if (obfPackages > 0) {
            signals.add(String.format(Locale.ROOT,
                    "%d paquetes con nombres de 1-2 letras (p. ej. a.b.c)", obfPackages));
        }
        if (level.equals("NONE")) {
            signals.add("No se detectaron patrones de renombre masivo");
        }

        return new ObfuscationInfo(totalClasses, obfClasses, totalMethods, obfMethods,
                totalFields, obfFields, obfPackages, ratio, level,
                List.copyOf(signals), false);
    }

    public static ObfuscationInfo withStringEncryption(ObfuscationInfo info, boolean likely) {
        if (!likely) return info;
        List<String> sig = new ArrayList<>(info.signals());
        sig.add("Patrones de encriptación de strings detectados en el código "
                + "(Base64 + Cipher): las cadenas pueden estar cifradas en runtime");
        return new ObfuscationInfo(info.totalClasses(), info.obfuscatedClasses(),
                info.totalMethods(), info.obfuscatedMethods(),
                info.totalFields(), info.obfuscatedFields(),
                info.obfuscatedPackages(), info.obfuscatedRatio(),
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
