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
}
