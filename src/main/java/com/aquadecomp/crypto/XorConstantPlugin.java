package com.aquadecomp.crypto;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XorConstantPlugin implements DecryptionPlugin {

    public static final String ID = "xor-constante-base64";

    private static final Pattern EXPRESSION = Pattern.compile(
            "new\\s+String\\s*\\(\\s*[^;]{0,400}?"
                    + "(?:android\\.util\\.)?Base64\\s*\\.\\s*"
                    + "(?:getDecoder\\s*\\(\\s*\\)\\s*\\.\\s*)?decode\\s*\\(\\s*"
                    + "\"([A-Za-z0-9+/=]{8,})\"",
            Pattern.DOTALL);

    private static final Pattern CARET_CONSTANT = Pattern.compile(
            "\\^\\s*(0x[0-9a-fA-F]{1,2}|\\d{1,3})");

    private static final Pattern NUMBER = Pattern.compile(
            "\\b(0x[0-9a-fA-F]{1,2}|\\d{1,3})\\b");

    private static final Pattern XOR_CALL = Pattern.compile(
            "\\bxor\\w*\\s*\\(", Pattern.CASE_INSENSITIVE);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Descifra XOR con constante cuando el texto cifrado Base64 y el valor XOR "
                + "aparecen en la misma expresión";
    }

    @Override
    public PluginOutcome apply(String source) {
        Matcher matcher = EXPRESSION.matcher(source);
        List<PluginOutcome.Replacement> replacements = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        boolean xorSeen = false;

        while (matcher.find()) {
            int open = source.indexOf('(', matcher.start());
            int close = SourceExpressions.matchingParenIndex(source, open);
            if (open < 0 || close < 0) {
                continue;
            }
            String expression = source.substring(matcher.start(), close + 1);
            Integer constant = constantOf(expression);
            if (constant == null || constant == 0) {
                continue;
            }
            xorSeen = true;
            byte[] plain = decrypt(matcher.group(1), constant);
            if (plain == null) {
                continue;
            }
            String text = new String(plain, StandardCharsets.UTF_8);
            if (!SourceExpressions.printable(text)) {
                continue;
            }
            replacements.add(new PluginOutcome.Replacement(matcher.start(), close + 1,
                    SourceExpressions.quote(text)));
        }

        if (xorSeen && replacements.isEmpty()) {
            notes.add(ID + ": se detectó un XOR con constante, pero el resultado no era "
                    + "texto imprimible; no se modificó el código");
        }
        return replacements.isEmpty()
                ? new PluginOutcome(replacements, notes)
                : PluginOutcome.withReplacements(replacements);
    }

    private Integer constantOf(String expression) {
        Matcher caret = CARET_CONSTANT.matcher(expression);
        if (caret.find()) {
            return parse(caret.group(1));
        }
        Matcher call = XOR_CALL.matcher(expression);
        if (!call.find()) {
            return null;
        }
        int open = expression.indexOf('(', call.start());
        int close = SourceExpressions.matchingParenIndex(expression, open);
        if (open < 0 || close < 0) {
            return null;
        }
        String args = stripStringLiterals(expression.substring(open + 1, close));
        Integer last = null;
        Matcher numbers = NUMBER.matcher(args);
        while (numbers.find()) {
            last = parse(numbers.group(1));
        }
        return last;
    }

    private static String stripStringLiterals(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Integer parse(String text) {
        try {
            return text.startsWith("0x") || text.startsWith("0X")
                    ? Integer.parseInt(text.substring(2), 16)
                    : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] decrypt(String encoded, int constant) {
        byte[] data;
        try {
            data = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) ((data[i] & 0xFF) ^ (constant & 0xFF));
        }
        return out;
    }
}
