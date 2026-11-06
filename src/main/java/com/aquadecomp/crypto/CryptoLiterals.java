package com.aquadecomp.crypto;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CryptoLiterals {

    record Ciphertext(int start, int end, byte[] bytes) {
    }

    private static final Pattern DIRECT_KEY = Pattern.compile(
            "SecretKeySpec\\s*\\(\\s*\"([^\"]{1,64})\"\\s*\\.\\s*getBytes\\s*\\(\\s*\\)");

    private static final Pattern VARIABLE_KEY = Pattern.compile(
            "SecretKeySpec\\s*\\(\\s*([A-Za-z_$][\\w$]*)\\s*,");

    private static final Pattern KEY_ASSIGNMENT = Pattern.compile(
            "(?:byte\\[\\]\\s+)?([A-Za-z_$][\\w$]*)\\s*=\\s*\"([^\"]{1,64})\"\\s*\\.\\s*getBytes\\s*\\(\\s*\\)");

    private static final Pattern DIRECT_IV = Pattern.compile(
            "IvParameterSpec\\s*\\(\\s*\"([^\"]{1,64})\"\\s*\\.\\s*getBytes\\s*\\(\\s*\\)");

    private static final Pattern VARIABLE_IV = Pattern.compile(
            "IvParameterSpec\\s*\\(\\s*([A-Za-z_$][\\w$]*)\\s*,");

    private static final Pattern TRANSFORMATION = Pattern.compile(
            "Cipher\\s*\\.\\s*getInstance\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern BASE64 = Pattern.compile(
            "(?:android\\.util\\.)?Base64\\s*\\.\\s*(?:getDecoder\\s*\\(\\s*\\)\\s*\\.\\s*)?"
                    + "decode\\s*\\(\\s*\"([A-Za-z0-9+/=]{8,})\"");

    private CryptoLiterals() {
    }

    static String transformation(String source) {
        Matcher matcher = TRANSFORMATION.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    static byte[] embeddedKey(String source) {
        Matcher direct = DIRECT_KEY.matcher(source);
        if (direct.find()) {
            return direct.group(1).getBytes(StandardCharsets.UTF_8);
        }
        Matcher variable = VARIABLE_KEY.matcher(source);
        if (variable.find()) {
            String name = variable.group(1);
            Matcher assignment = KEY_ASSIGNMENT.matcher(source);
            while (assignment.find()) {
                if (assignment.group(1).equals(name)) {
                    return assignment.group(2).getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    static byte[] embeddedIv(String source) {
        Matcher direct = DIRECT_IV.matcher(source);
        if (direct.find()) {
            return direct.group(1).getBytes(StandardCharsets.UTF_8);
        }
        Matcher variable = VARIABLE_IV.matcher(source);
        if (variable.find()) {
            String name = variable.group(1);
            Matcher assignment = KEY_ASSIGNMENT.matcher(source);
            while (assignment.find()) {
                if (assignment.group(1).equals(name)) {
                    return assignment.group(2).getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    static boolean mentionsCipher(String source) {
        return TRANSFORMATION.matcher(source).find();
    }

    static List<Ciphertext> base64Ciphertexts(String source) {
        List<Ciphertext> found = new ArrayList<>();
        Matcher matcher = BASE64.matcher(source);
        while (matcher.find()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(matcher.group(1));
                if (bytes.length > 0) {
                    found.add(new Ciphertext(matcher.start(1), matcher.end(1), bytes));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return found;
    }
}
