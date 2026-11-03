package com.aquadecomp.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class DeobfuscationEngine {

    private final NameRecoveryEngine nameRecovery;
    private final Map<String, String> nameMap = new HashMap<>();

    public DeobfuscationEngine(NameRecoveryEngine nameRecovery) {
        this.nameRecovery = nameRecovery;
        this.nameMap.putAll(nameRecovery.getRecoveredNames());
    }

    public String deobfuscate(String source, String className) {
        String result = source;

        result = applyNameMapping(result);
        result = replaceCommonObfuscationPatterns(result);
        result = recoverMethodSignatures(result);
        result = renameVariables(result);
        result = fixInvalidIdentifiers(result);

        return result;
    }

    private String applyNameMapping(String source) {
        String result = source;

        for (Map.Entry<String, String> entry : nameMap.entrySet()) {
            String obfuscated = entry.getKey();
            String realName = entry.getValue();

            if (!obfuscated.equals(realName)) {
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(obfuscated) + "\\b");
                result = pattern.matcher(result).replaceAll(realName);
            }
        }

        return result;
    }

    private String replaceCommonObfuscationPatterns(String source) {
        String result = source;

        result = result.replaceAll("\\ba\\s*\\(", "handler(");
        result = result.replaceAll("\\bb\\s*\\(", "callback(");
        result = result.replaceAll("\\bc\\s*\\(", "listener(");

        result = result.replaceAll("\\bif\\s*\\(\\s*a\\b", "if (shouldProcess");
        result = result.replaceAll("\\bif\\s*\\(\\s*b\\b", "if (isValid");
        result = result.replaceAll("\\bif\\s*\\(\\s*c\\b", "if (isEnabled");

        result = result.replaceAll("\\bint\\s+a\\b", "int result");
        result = result.replaceAll("\\bString\\s+a\\b", "String value");
        result = result.replaceAll("\\bboolean\\s+a\\b", "boolean flag");
        result = result.replaceAll("\\bvoid\\s+a\\b", "void process");

        return result;
    }

    private String recoverMethodSignatures(String source) {
        Pattern pattern = Pattern.compile("(public|private|protected)\\s+(static\\s+)?(\\w+)\\s+([a-z]{1,3})\\s*\\(");
        java.util.regex.Matcher matcher = pattern.matcher(source);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String visibility = matcher.group(1);
            String isStatic = matcher.group(2) != null ? "static " : "";
            String returnType = matcher.group(3);
            String methodName = matcher.group(4);

            String recoveredName = recoverMethodName(methodName, returnType);
            String replacement = visibility + " " + isStatic + returnType + " " + recoveredName + "(";

            matcher.appendReplacement(sb, replacement.replace("$", "\\$"));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String renameVariables(String source) {
        Pattern singleLetterVar = Pattern.compile("\\b([a-z])\\s*=\\s*");
        java.util.regex.Matcher matcher = singleLetterVar.matcher(source);

        StringBuffer sb = new StringBuffer();
        int varCounter = 0;

        while (matcher.find()) {
            String letter = matcher.group(1);

            if (!isCommonKeyword(letter)) {
                String newName = "var" + (++varCounter);
                String replacement = newName + " = ";
                matcher.appendReplacement(sb, replacement);
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    private String fixInvalidIdentifiers(String source) {
        String result = source;

        result = result.replaceAll("\\$\\$Lambda\\d+", "lambda");
        result = result.replaceAll("synthetic\\s+method", "synthetic_method");
        result = result.replaceAll("\\s+//\\s*$", "");

        return result;
    }

    private String recoverMethodName(String obfuscated, String returnType) {
        if (nameRecovery.getConfidence(obfuscated) > 50) {
            return nameRecovery.recover(obfuscated);
        }

        if (returnType.equals("void")) {
            return "execute";
        } else if (returnType.equals("boolean")) {
            return "check";
        } else if (returnType.equals("String")) {
            return "getString";
        } else if (returnType.equals("int")) {
            return "getValue";
        }

        return obfuscated;
    }

    private boolean isCommonKeyword(String letter) {
        return letter.matches("[ibjfdsv]");
    }

    public void addCustomMapping(String obfuscated, String realName) {
        nameMap.put(obfuscated, realName);
    }

    public Map<String, String> getAppliedMappings() {
        return new HashMap<>(nameMap);
    }
}
