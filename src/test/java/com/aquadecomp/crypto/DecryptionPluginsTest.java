package com.aquadecomp.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecryptionPluginsTest {

    private static String aesEncrypt(String plain, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"));
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static String aesCbcEncrypt(String plain, String key, String iv)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] xorBytes(byte[] data, int constant) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) ((data[i] & 0xFF) ^ (constant & 0xFF));
        }
        return out;
    }

    private static String aesSource(String cipherB64, String key) {
        return """
                import javax.crypto.Cipher;
                import javax.crypto.spec.SecretKeySpec;
                import android.util.Base64;

                class a {
                    String x() throws Exception {
                        Cipher c = Cipher.getInstance("AES");
                        c.init(1, new SecretKeySpec("%s".getBytes(), "AES"));
                        return new String(c.doFinal(Base64.decode("%s", 0)), "UTF-8");
                    }
                }
                """.formatted(key, cipherB64);
    }

    @Test
    void aesConClaveEmbebidaDescifraLaCadena() throws Exception {
        String plain = "hola mundo de prueba";
        String source = aesSource(aesEncrypt(plain, "0123456789abcdef"),
                "0123456789abcdef");

        PluginOutcome outcome = new AesEmbeddedKeyPlugin().apply(source);

        assertEquals(1, outcome.replacements().size(),
                "sustituciones: " + outcome.replacements().size());
        PluginOutcome.Replacement replacement = outcome.replacements().get(0);
        assertEquals("\"" + plain + "\"", replacement.text());
        assertEquals(source.indexOf("new String("), replacement.start());
        assertTrue(outcome.notes().isEmpty(), "notas: " + outcome.notes());
    }

    @Test
    void aesSinClaveEmbebidaNoAfiirmaNada() {
        String source = """
                class a {
                    String x(byte[] clave) throws Exception {
                        Cipher c = Cipher.getInstance("AES");
                        c.init(1, new SecretKeySpec(clave, "AES"));
                        return new String(c.doFinal(Base64.decode(
                                "q8OQkMDDkJjDw5CQwMOQkA==", 0)), "UTF-8");
                    }
                }
                """;

        PluginOutcome outcome = new AesEmbeddedKeyPlugin().apply(source);

        assertTrue(outcome.replacements().isEmpty());
        assertEquals(1, outcome.notes().size());
        assertTrue(outcome.notes().get(0).contains("no está embebida"),
                "nota: " + outcome.notes());
    }

    @Test
    void aesCbcSinIvLoDiceConClaridad() throws Exception {
        String cipherB64 = aesEncrypt("texto", "0123456789abcdef");
        String source = """
                class a {
                    String x() throws Exception {
                        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        c.init(1, new SecretKeySpec("0123456789abcdef".getBytes(),
                                "AES"));
                        return new String(c.doFinal(Base64.decode("%s", 0)), "UTF-8");
                    }
                }
                """.formatted(cipherB64);

        PluginOutcome outcome = new AesEmbeddedKeyPlugin().apply(source);

        assertTrue(outcome.replacements().isEmpty());
        assertTrue(outcome.notes().get(0).contains("IV"),
                "nota: " + outcome.notes());
    }

    @Test
    void aesCbcConIvEmbebidoDescifra() throws Exception {
        String plain = "mensaje cifrado con iv";
        String cipherB64 = aesCbcEncrypt(plain, "0123456789abcdef", "1234567890abcdef");
        String source = """
                class a {
                    String x() throws Exception {
                        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        c.init(1, new SecretKeySpec("0123456789abcdef".getBytes(),
                                "AES"), new IvParameterSpec("1234567890abcdef".getBytes(),
                                "AES"));
                        return new String(c.doFinal(Base64.decode("%s", 0)), "UTF-8");
                    }
                }
                """.formatted(cipherB64);

        PluginOutcome outcome = new AesEmbeddedKeyPlugin().apply(source);

        assertEquals(1, outcome.replacements().size());
        assertEquals("\"" + plain + "\"", outcome.replacements().get(0).text());
    }

    @Test
    void aesConResultadoNoImprimibleNoSustituye() throws Exception {
        String source = aesSource(aesEncrypt("\u0001\u0002\u0003\u0004\u0005\u0006\u0007",
                "0123456789abcdef"), "0123456789abcdef");

        PluginOutcome outcome = new AesEmbeddedKeyPlugin().apply(source);

        assertTrue(outcome.replacements().isEmpty());
        assertFalse(outcome.notes().isEmpty(), "debe explicar por qué no sustituyó");
        assertTrue(outcome.notes().get(0).contains("imprimible"),
                "nota: " + outcome.notes());
    }

    @Test
    void xorConHelperEmbebidoDescifra() {
        String plain = "secreto oculto";
        String cipherB64 = Base64.getEncoder().encodeToString(
                xorBytes(plain.getBytes(StandardCharsets.UTF_8), 0x5B));
        String source = """
                class a {
                    String x() {
                        return new String(xorBytes(Base64.decode("%s", 0), (byte) 0x5B),
                                "UTF-8");
                    }
                }
                """.formatted(cipherB64);

        PluginOutcome outcome = new XorConstantPlugin().apply(source);

        assertEquals(1, outcome.replacements().size(),
                "sustituciones: " + outcome.replacements());
        assertEquals("\"" + plain + "\"", outcome.replacements().get(0).text());
    }

    @Test
    void xorConOperadorCircunflejoDescifra() {
        String plain = "clave visible";
        String cipherB64 = Base64.getEncoder().encodeToString(
                xorBytes(plain.getBytes(StandardCharsets.UTF_8), 0x37));
        String source = "class a { String x() { return new String(Base64.decode(\""
                + cipherB64 + "\", 0) ^ 0x37, \"UTF-8\"); } }";

        PluginOutcome outcome = new XorConstantPlugin().apply(source);

        assertEquals(1, outcome.replacements().size(),
                "sustituciones: " + outcome.replacements());
        assertEquals("\"" + plain + "\"", outcome.replacements().get(0).text());
    }

    @Test
    void xorConClaveIncorrectaLoAnota() {
        String cipherB64 = Base64.getEncoder().encodeToString(
                xorBytes("texto plano".getBytes(StandardCharsets.UTF_8), 0x11));
        String source = """
                class a {
                    String x() {
                        return new String(xorBytes(Base64.decode("%s", 0), (byte) 0x99),
                                "UTF-8");
                    }
                }
                """.formatted(cipherB64);

        PluginOutcome outcome = new XorConstantPlugin().apply(source);

        assertTrue(outcome.replacements().isEmpty());
        assertEquals(1, outcome.notes().size());
        assertTrue(outcome.notes().get(0).contains("imprimible"),
                "nota: " + outcome.notes());
    }

    @Test
    void rc4ConClaveEmbebidaDescifra() {
        String plain = "mensaje rc4";
        String cipherB64 = Base64.getEncoder().encodeToString(
                Rc4EmbeddedKeyPlugin.rc4("clave-rc4".getBytes(StandardCharsets.UTF_8),
                        plain.getBytes(StandardCharsets.UTF_8)));
        String source = """
                class a {
                    String x() throws Exception {
                        Cipher c = Cipher.getInstance("RC4");
                        c.init(1, new SecretKeySpec("clave-rc4".getBytes(), "RC4"));
                        return new String(c.doFinal(Base64.decode("%s", 0)), "UTF-8");
                    }
                }
                """.formatted(cipherB64);

        PluginOutcome outcome = new Rc4EmbeddedKeyPlugin().apply(source);

        assertEquals(1, outcome.replacements().size(),
                "sustituciones: " + outcome.replacements());
        assertEquals("\"" + plain + "\"", outcome.replacements().get(0).text());
    }

    @Test
    void rc4SinClaveEmbebidaLoAnota() {
        String source = """
                class a {
                    String x(byte[] clave) throws Exception {
                        Cipher c = Cipher.getInstance("ARCFOUR");
                        c.init(1, new SecretKeySpec(clave, "ARCFOUR"));
                        return new String(c.doFinal(Base64.decode(
                                "AQQEBAUGBwgJ", 0)), "UTF-8");
                    }
                }
                """;

        PluginOutcome outcome = new Rc4EmbeddedKeyPlugin().apply(source);

        assertTrue(outcome.replacements().isEmpty());
        assertTrue(outcome.notes().get(0).contains("no está embebida"),
                "nota: " + outcome.notes());
    }

    @Test
    void registroIncluyeIntegradosYLosDeServiceLoader() {
        List<String> ids = PluginRegistry.plugins().stream().map(DecryptionPlugin::id).toList();

        assertTrue(ids.contains(AesEmbeddedKeyPlugin.ID), "ids: " + ids);
        assertTrue(ids.contains(Rc4EmbeddedKeyPlugin.ID), "ids: " + ids);
        assertTrue(ids.contains(XorConstantPlugin.ID), "ids: " + ids);
        assertTrue(ids.contains("plugin-de-prueba"), "ids: " + ids);
    }
}
