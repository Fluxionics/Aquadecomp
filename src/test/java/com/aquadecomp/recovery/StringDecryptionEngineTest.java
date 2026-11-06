package com.aquadecomp.recovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StringDecryptionEngineTest {

    private final StringDecryptionEngine engine = new StringDecryptionEngine();

    @Test
    void foldsByteArrayIntoReadableString() {
        String result = engine.attemptDecryption(
                "String s = new String(new byte[]{72,105,33});");
        assertEquals("String s = \"Hi!\";", result);
        assertEquals(1, engine.lastReplacementCount());
    }

    @Test
    void leavesBareByteArrayUntouched() {
        String source = "byte[] b = new byte[]{72,105};";
        assertEquals(source, engine.attemptDecryption(source));
        assertEquals(0, engine.lastReplacementCount());
    }

    @Test
    void foldsBase64DecodeCall() {
        String result = engine.attemptDecryption(
                "String t = new String(Base64.decode(\"aGVsbG8=\", 0));");
        assertEquals("String t = \"hello\";", result);
        assertEquals(1, engine.lastReplacementCount());
    }

    @Test
    void foldsXorInsideByteArray() {
        String result = engine.attemptDecryption(
                "String u = new String(new byte[]{(byte)(65 ^ 32), 66});");
        assertEquals("String u = \"aB\";", result);
    }

    @Test
    void leavesNonPrintableBytesUntouched() {
        String source = "String v = new String(new byte[]{1,2,255});";
        assertEquals(source, engine.attemptDecryption(source));
        assertEquals(0, engine.lastReplacementCount());
    }

    @Test
    void leavesUnknownExpressionsUntouched() {
        String source = "String w = new String(new byte[]{n, m});";
        assertEquals(source, engine.attemptDecryption(source));
        assertEquals(0, engine.lastReplacementCount());
    }

    @Test
    void hexLiteralsAreSupported() {
        String result = engine.attemptDecryption(
                "String h = new String(new byte[]{0x48, 0x69});");
        assertEquals("String h = \"Hi\";", result);
    }

    @Test
    void stateDoesNotLeakBetweenCalls() {
        engine.attemptDecryption("String a = new String(new byte[]{72,105});");
        String second = engine.attemptDecryption("String b = otroMetodo();");
        assertEquals("String b = otroMetodo();", second);
        assertEquals(0, engine.lastReplacementCount());
    }

    @Test
    void multipleCallsInOneSourceAreAllFolded() {
        String result = engine.attemptDecryption(
                "String a = new String(new byte[]{65}); String b = new String(new byte[]{66});");
        assertEquals("String a = \"A\"; String b = \"B\";", result);
        assertEquals(2, engine.lastReplacementCount());
    }

    @Test
    void quotedTextWithSpecialCharactersIsEscaped() {
        String result = engine.attemptDecryption(
                "String q = new String(new byte[]{34, 92, 97});");
        assertEquals("String q = \"\\\"\\\\a\";", result);
        assertNotEquals(0, engine.lastReplacementCount());
    }

    @Test
    void aesWithEmbeddedKeyIsDecryptedByPluginAndCounted() throws Exception {
        String plain = "cadena cifrada real";
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec("0123456789abcdef".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8), "AES"));
        String cipherB64 = java.util.Base64.getEncoder().encodeToString(
                cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String source = """
                class a { String x() throws Exception {
                    Cipher c = Cipher.getInstance("AES");
                    c.init(1, new SecretKeySpec("0123456789abcdef".getBytes(), "AES"));
                    return new String(c.doFinal(Base64.decode("%s", 0)), "UTF-8");
                } }""".formatted(cipherB64);

        String result = engine.attemptDecryption(source);

        assertEquals(1, engine.lastPluginCounts()
                .getOrDefault(com.aquadecomp.crypto.AesEmbeddedKeyPlugin.ID, 0));
        assertEquals(1, engine.lastReplacementCount());
        org.junit.jupiter.api.Assertions.assertTrue(result.contains("\"" + plain + "\""),
                "resultado: " + result);
        org.junit.jupiter.api.Assertions.assertTrue(engine.lastNotes().isEmpty(),
                "notas: " + engine.lastNotes());
    }

    @Test
    void aesWithoutEmbeddedKeyLeavesSourceAndAddsNote() {
        String source = """
                class a { String x(byte[] clave) throws Exception {
                    Cipher c = Cipher.getInstance("AES");
                    c.init(1, new SecretKeySpec(clave, "AES"));
                    return new String(c.doFinal(Base64.decode("q8OQkMDDkJjDw5CQwMOQkA==", 0)),
                            "UTF-8");
                } }""";

        String result = engine.attemptDecryption(source);

        assertEquals(source, result);
        org.junit.jupiter.api.Assertions.assertFalse(engine.lastNotes().isEmpty(),
                "debe dejar una nota explicando por qué no descifró");
    }
}
