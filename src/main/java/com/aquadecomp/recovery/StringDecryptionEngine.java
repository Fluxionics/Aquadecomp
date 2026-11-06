package com.aquadecomp.recovery;

import com.aquadecomp.crypto.DecryptionPlugin;
import com.aquadecomp.crypto.PluginOutcome;
import com.aquadecomp.crypto.PluginRegistry;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringDecryptionEngine {

    private static final Pattern STRING_BYTE_ARRAY = Pattern.compile(
            "new\\s+String\\s*\\(\\s*new\\s+byte\\[\\]\\s*\\{([^}]+)\\}\\s*(?:,\\s*\"([^\"]+)\"\\s*)?\\)"
    );
    private static final Pattern STRING_BASE64 = Pattern.compile(
            "new\\s+String\\s*\\(\\s*(?:android\\.util\\.)?Base64\\.decode\\s*\\(\\s*\"([A-Za-z0-9+/=]+)\""
                    + "\\s*(?:,[^)]*)?\\s*\\)\\s*(?:,\\s*\"([^\"]+)\"\\s*)?\\)"
    );

    private final List<DecryptionPlugin> plugins = PluginRegistry.plugins();

    private int lastReplacementCount;
    private final Map<String, Integer> lastPluginCounts = new LinkedHashMap<>();
    private final List<String> lastNotes = new ArrayList<>();

    public String attemptDecryption(String source) {
        lastReplacementCount = 0;
        lastPluginCounts.clear();
        lastNotes.clear();
        Map<String, String> decrypted = new HashMap<>();

        String result = source;
        int pluginReplacements = 0;
        for (DecryptionPlugin plugin : plugins) {
            PluginOutcome outcome;
            try {
                outcome = plugin.apply(result);
            } catch (RuntimeException e) {
                continue;
            }
            lastNotes.addAll(outcome.notes());
            if (outcome.replacements().isEmpty()) {
                continue;
            }
            Applied applied = applyReplacements(result, outcome.replacements());
            if (applied.count() == 0) {
                continue;
            }
            result = applied.source();
            pluginReplacements += applied.count();
            lastPluginCounts.merge(plugin.id(), applied.count(), Integer::sum);
        }

        result = foldByteArrays(result, decrypted);
        result = foldBase64(result, decrypted);

        lastReplacementCount = decrypted.size() + pluginReplacements;
        return result;
    }

    private record Applied(String source, int count) {
    }

    private static Applied applyReplacements(String source,
                                             List<PluginOutcome.Replacement> replacements) {
        List<PluginOutcome.Replacement> sorted = new ArrayList<>(replacements);
        sorted.sort(Comparator.comparingInt(PluginOutcome.Replacement::start).reversed());
        int applied = 0;
        int limit = Integer.MAX_VALUE;
        StringBuilder sb = new StringBuilder(source);
        for (PluginOutcome.Replacement replacement : sorted) {
            if (replacement.start() < 0 || replacement.end() > source.length()
                    || replacement.start() >= replacement.end()
                    || replacement.end() > limit) {
                continue;
            }
            sb.replace(replacement.start(), replacement.end(), replacement.text());
            limit = replacement.start();
            applied++;
        }
        return new Applied(sb.toString(), applied);
    }

    private String foldByteArrays(String source, Map<String, String> decrypted) {
        Matcher matcher = STRING_BYTE_ARRAY.matcher(source);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String replacement = null;
            try {
                byte[] bytes = parseByteArray(matcher.group(1));
                Charset charset = resolveCharset(matcher.group(2));
                if (bytes != null && charset != null) {
                    String text = new String(bytes, charset);
                    if (isPrintableText(text)) {
                        replacement = quote(text);
                        decrypted.put(matcher.group(0), replacement);
                    }
                }
            } catch (Exception ignored) {
            }
            if (replacement == null) {
                replacement = Matcher.quoteReplacement(matcher.group(0));
            } else {
                replacement = Matcher.quoteReplacement(replacement);
            }
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String foldBase64(String source, Map<String, String> decrypted) {
        Matcher matcher = STRING_BASE64.matcher(source);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String replacement = null;
            try {
                Charset charset = resolveCharset(matcher.group(2));
                byte[] bytes = decodeBase64(matcher.group(1));
                if (bytes != null && charset != null) {
                    String text = new String(bytes, charset);
                    if (isPrintableText(text)) {
                        replacement = quote(text);
                        decrypted.put(matcher.group(0), replacement);
                    }
                }
            } catch (Exception ignored) {
            }
            if (replacement == null) {
                replacement = Matcher.quoteReplacement(matcher.group(0));
            } else {
                replacement = Matcher.quoteReplacement(replacement);
            }
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private byte[] parseByteArray(String body) {
        String[] parts = body.split(",");
        byte[] bytes = new byte[parts.length];

        for (int i = 0; i < parts.length; i++) {
            Integer value = evaluateElement(parts[i].trim());
            if (value == null) {
                return null;
            }
            bytes[i] = (byte) value.intValue();
        }
        return bytes;
    }

    private Integer evaluateElement(String element) {
        if (element.isEmpty()) {
            return null;
        }
        if (element.startsWith("(byte)")) {
            return evaluateElement(element.substring(6).trim());
        }
        if (element.startsWith("(") && element.endsWith(")")) {
            return evaluateElement(element.substring(1, element.length() - 1).trim());
        }
        int xor = element.indexOf('^');
        if (xor > 0) {
            Integer left = evaluateElement(element.substring(0, xor).trim());
            Integer right = evaluateElement(element.substring(xor + 1).trim());
            if (left == null || right == null) {
                return null;
            }
            return left ^ right;
        }
        try {
            if (element.startsWith("0x") || element.startsWith("0X")) {
                return Integer.parseInt(element.substring(2), 16);
            }
            return Integer.parseInt(element);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private byte[] decodeBase64(String encoded) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(encoded);
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    private Charset resolveCharset(String name) {
        if (name == null || name.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPrintableText(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    private String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2);
        sb.append('"');
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    public int lastReplacementCount() {
        return lastReplacementCount;
    }

    public Map<String, Integer> lastPluginCounts() {
        return new LinkedHashMap<>(lastPluginCounts);
    }

    public List<String> lastNotes() {
        return List.copyOf(lastNotes);
    }
}
