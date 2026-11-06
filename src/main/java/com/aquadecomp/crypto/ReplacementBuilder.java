package com.aquadecomp.crypto;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class ReplacementBuilder {

    private ReplacementBuilder() {
    }

    static List<PluginOutcome.Replacement> fromCiphertexts(String source,
                                                           List<CryptoLiterals.Ciphertext> ciphertexts,
                                                           Function<byte[], byte[]> decryptor) {
        List<PluginOutcome.Replacement> out = new ArrayList<>();
        Set<Integer> usedRanges = new HashSet<>();
        for (CryptoLiterals.Ciphertext ciphertext : ciphertexts) {
            byte[] plain;
            try {
                plain = decryptor.apply(ciphertext.bytes());
            } catch (RuntimeException e) {
                continue;
            }
            if (plain == null || plain.length == 0) {
                continue;
            }
            String text = new String(plain, StandardCharsets.UTF_8);
            if (!SourceExpressions.printable(text)) {
                continue;
            }
            int[] range = SourceExpressions.enclosingNewString(source, ciphertext.start());
            if (range == null) {
                continue;
            }
            if (!usedRanges.add(range[0])) {
                continue;
            }
            out.add(new PluginOutcome.Replacement(range[0], range[1],
                    SourceExpressions.quote(text)));
        }
        return out;
    }
}
