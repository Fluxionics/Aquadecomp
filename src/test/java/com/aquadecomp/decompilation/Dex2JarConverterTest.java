package com.aquadecomp.decompilation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Dex2JarConverterTest {

    @Test
    void rootLevelDexFilesAreAccepted() {
        assertTrue(Dex2JarConverter.isDexEntry("classes.dex"));
        assertTrue(Dex2JarConverter.isDexEntry("classes2.dex"));
        assertTrue(Dex2JarConverter.isDexEntry("classes10.dex"));
    }

    @Test
    void bundleDexFilesAreAccepted() {
        assertTrue(Dex2JarConverter.isDexEntry("base/dex/classes.dex"));
        assertTrue(Dex2JarConverter.isDexEntry("base/dex/classes3.dex"));
    }

    @Test
    void nonDexEntriesAreRejected() {
        assertFalse(Dex2JarConverter.isDexEntry("readme.txt"));
        assertFalse(Dex2JarConverter.isDexEntry("classes.dex.bak"));
        assertFalse(Dex2JarConverter.isDexEntry("assets/classes.dex"));
        assertFalse(Dex2JarConverter.isDexEntry("lib/arm64-v8a/classes.dex"));
        assertFalse(Dex2JarConverter.isDexEntry("base/dex/notclasses.dex"));
    }
}
