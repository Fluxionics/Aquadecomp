package com.aquadecomp.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AesEmbeddedKeyPlugin implements DecryptionPlugin {

    public static final String ID = "aes-clave-embebida";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Descifra AES cuando la clave (y el IV, si el modo lo pide) está embebida "
                + "como literal en el mismo código";
    }

    @Override
    public PluginOutcome apply(String source) {
        String transformation = CryptoLiterals.transformation(source);
        if (transformation == null
                || !transformation.toUpperCase(Locale.ROOT).startsWith("AES")) {
            return PluginOutcome.none();
        }

        List<String> notes = new ArrayList<>();
        byte[] key = CryptoLiterals.embeddedKey(source);
        if (key == null) {
            notes.add(ID + ": se detectó AES pero la clave no está embebida como literal; "
                    + "no se descifró nada");
            return PluginOutcome.withNotes(notes);
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            notes.add(ID + ": la clave embebida tiene " + key.length
                    + " bytes y AES exige 16, 24 o 32; no se descifró nada");
            return PluginOutcome.withNotes(notes);
        }

        String normalized = normalize(transformation);
        byte[] iv = CryptoLiterals.embeddedIv(source);
        if (normalized.contains("/CBC/") && iv == null) {
            notes.add(ID + ": AES/CBC detectado pero el IV no está embebido como literal; "
                    + "no se descifró nada");
            return PluginOutcome.withNotes(notes);
        }

        byte[] ivBytes = iv;
        List<PluginOutcome.Replacement> replacements = ReplacementBuilder.fromCiphertexts(
                source, CryptoLiterals.base64Ciphertexts(source),
                bytes -> decrypt(normalized, key, ivBytes, bytes));

        if (replacements.isEmpty()) {
            notes.add(ID + ": clave embebida, pero ningún texto descifrado era imprimible "
                    + "o la expresión no era reconocible; no se modificó el código");
        }
        return new PluginOutcome(replacements, notes);
    }

    static String normalize(String transformation) {
        if (!transformation.contains("/")) {
            return "AES/ECB/PKCS5Padding";
        }
        return transformation;
    }

    private static byte[] decrypt(String transformation, byte[] key, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            if (iv != null && transformation.contains("/CBC/")) {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            } else {
                cipher.init(Cipher.DECRYPT_MODE, keySpec);
            }
            return cipher.doFinal(data);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isPrintableUtf8(byte[] data) {
        return data != null
                && SourceExpressions.printable(new String(data, StandardCharsets.UTF_8));
    }
}
