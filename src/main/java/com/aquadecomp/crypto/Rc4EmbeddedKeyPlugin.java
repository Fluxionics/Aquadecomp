package com.aquadecomp.crypto;

import java.util.ArrayList;
import java.util.List;

public final class Rc4EmbeddedKeyPlugin implements DecryptionPlugin {

    public static final String ID = "rc4-clave-embebida";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Descifra RC4 cuando la clave está embebida como literal en el mismo código";
    }

    @Override
    public PluginOutcome apply(String source) {
        if (!mentionsRc4(source)) {
            return PluginOutcome.none();
        }
        List<String> notes = new ArrayList<>();
        byte[] key = CryptoLiterals.embeddedKey(source);
        if (key == null) {
            notes.add(ID + ": se detectó RC4 pero la clave no está embebida como literal; "
                    + "no se descifró nada");
            return PluginOutcome.withNotes(notes);
        }

        List<PluginOutcome.Replacement> replacements = ReplacementBuilder.fromCiphertexts(
                source, CryptoLiterals.base64Ciphertexts(source),
                bytes -> rc4(key, bytes));

        if (replacements.isEmpty()) {
            notes.add(ID + ": clave embebida, pero ningún texto descifrado era imprimible "
                    + "o la expresión no era reconocible; no se modificó el código");
        }
        return new PluginOutcome(replacements, notes);
    }

    private static boolean mentionsRc4(String source) {
        String upper = source.toUpperCase(java.util.Locale.ROOT);
        return upper.contains("getInstance(\"RC4")
                || upper.contains("getInstance(\"ARCFOUR")
                || upper.contains("\"RC4\"")
                || upper.contains("\"ARCFOUR\"");
    }

    static byte[] rc4(byte[] key, byte[] data) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) {
            s[i] = i;
        }
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            int temp = s[i];
            s[i] = s[j];
            s[j] = temp;
        }
        byte[] out = new byte[data.length];
        int i = 0;
        j = 0;
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) & 0xFF;
            j = (j + s[i]) & 0xFF;
            int temp = s[i];
            s[i] = s[j];
            s[j] = temp;
            out[k] = (byte) (data[k] ^ s[(s[i] + s[j]) & 0xFF]);
        }
        return out;
    }
}
