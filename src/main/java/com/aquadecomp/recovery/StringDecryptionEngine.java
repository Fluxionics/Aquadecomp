package com.aquadecomp.recovery;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class StringDecryptionEngine {

    private final Map<String, String> decryptedStrings = new HashMap<>();
    private static final Pattern BYTE_ARRAY = Pattern.compile(
            "new\\s+byte\\[\\]\\s*\\{\\s*([^}]+)\\}"
    );
    private static final Pattern XOR_PATTERN = Pattern.compile(
            "\\(byte\\)\\s*\\(([^)]+)\\s*\\^\\s*([^)]+)\\)"
    );

    public String attemptDecryption(String source) {
        String result = source;

        result = decryptXorPatterns(result);
        result = decryptByteArrays(result);
        result = decryptCommonEncryption(result);
        result = replaceDecryptedStrings(result);

        return result;
    }

    private String decryptXorPatterns(String source) {
        java.util.regex.Matcher matcher = XOR_PATTERN.matcher(source);

        while (matcher.find()) {
            try {
                String value1 = matcher.group(1).trim();
                String value2 = matcher.group(2).trim();

                byte b1 = parseByte(value1);
                byte b2 = parseByte(value2);
                byte result = (byte) (b1 ^ b2);

                if (isPrintable(result)) {
                    String encrypted = matcher.group(0);
                    String decrypted = String.valueOf((char) result);
                    decryptedStrings.put(encrypted, decrypted);
                }
            } catch (Exception ignored) {
            }
        }

        return source;
    }

    private String decryptByteArrays(String source) {
        java.util.regex.Matcher matcher = BYTE_ARRAY.matcher(source);

        while (matcher.find()) {
            try {
                String bytesStr = matcher.group(1);
                String decrypted = decryptByteSequence(bytesStr);

                if (!decrypted.isEmpty() && isProbablyText(decrypted)) {
                    decryptedStrings.put(matcher.group(0), "\"" + decrypted + "\"");
                }
            } catch (Exception ignored) {
            }
        }

        return source;
    }

    private String decryptByteSequence(String bytesStr) {
        StringBuilder result = new StringBuilder();
        String[] bytes = bytesStr.split(",");

        for (String byteStr : bytes) {
            try {
                byte b = parseByte(byteStr.trim());
                if (isPrintable(b)) {
                    result.append((char) b);
                }
            } catch (Exception ignored) {
            }
        }

        return result.toString();
    }

    private String decryptCommonEncryption(String source) {
        String result = source.replaceAll(
                "Cipher\\.getInstance\\([\"']AES[\"']\\)",
                "// AES encryption detected"
        );
        result = result.replaceAll(
                "MessageDigest\\.getInstance\\([\"']SHA\\-256[\"']\\)",
                "// SHA-256 detected"
        );

        return result;
    }

    private String replaceDecryptedStrings(String source) {
        String result = source;

        for (Map.Entry<String, String> entry : decryptedStrings.entrySet()) {
            String encrypted = entry.getKey();
            String decrypted = entry.getValue();
            result = result.replace(encrypted, decrypted);
        }

        return result;
    }

    private byte parseByte(String str) {
        str = str.trim();

        if (str.startsWith("0x") || str.startsWith("0X")) {
            return (byte) Integer.parseInt(str.substring(2), 16);
        } else if (str.startsWith("(byte)")) {
            return parseByte(str.substring(6));
        } else {
            return Byte.parseByte(str);
        }
    }

    private boolean isPrintable(byte b) {
        return b >= 32 && b <= 126;
    }

    private boolean isProbablyText(String str) {
        if (str.length() < 3) {
            return false;
        }

        int printable = 0;
        for (char c : str.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == ' ') {
                printable++;
            }
        }

        return (printable * 100 / str.length()) > 70;
    }

    public Map<String, String> getDecryptedStrings() {
        return new HashMap<>(decryptedStrings);
    }
}
