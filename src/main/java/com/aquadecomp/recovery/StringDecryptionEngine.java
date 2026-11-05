package com.aquadecomp.recovery;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
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

    private int lastReplacementCount;

    public String attemptDecryption(String source) {
        lastReplacementCount = 0;
        Map<String, String> decrypted = new HashMap<>();

        String result = foldByteArrays(source, decrypted);
        result = foldBase64(result, decrypted);

        lastReplacementCount = decrypted.size();
        return result;
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
}
