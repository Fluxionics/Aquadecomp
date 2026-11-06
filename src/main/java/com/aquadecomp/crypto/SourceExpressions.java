package com.aquadecomp.crypto;

public final class SourceExpressions {

    private static final int WINDOW = 400;

    private SourceExpressions() {
    }

    public static int[] enclosingNewString(String source, int literalIndex) {
        if (literalIndex < 0 || literalIndex >= source.length()) {
            return null;
        }
        int open = source.lastIndexOf("new String(", literalIndex);
        if (open < 0) {
            return null;
        }
        if (literalIndex - open > WINDOW) {
            return null;
        }
        for (int i = open; i < literalIndex; i++) {
            char c = source.charAt(i);
            if (c == ';' || c == '{' || c == '}') {
                return null;
            }
        }
        int close = matchingParen(source, open + "new String(".length() - 1);
        if (close < 0 || close < literalIndex) {
            return null;
        }
        return new int[]{open, close + 1};
    }

    public static int matchingParenIndex(String source, int openIndex) {
        return matchingParen(source, openIndex);
    }

    public static boolean printable(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    public static String quote(String text) {
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

    private static int matchingParen(String source, int openIndex) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = openIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (inString) {
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
