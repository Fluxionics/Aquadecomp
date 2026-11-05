package com.aquadecomp.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceLevelTest {

    @Test
    void boundariesMapToExpectedLevels() {
        assertEquals(ConfidenceLevel.EXCELLENT, ConfidenceLevel.fromScore(100));
        assertEquals(ConfidenceLevel.EXCELLENT, ConfidenceLevel.fromScore(90));
        assertEquals(ConfidenceLevel.GOOD, ConfidenceLevel.fromScore(89));
        assertEquals(ConfidenceLevel.GOOD, ConfidenceLevel.fromScore(75));
        assertEquals(ConfidenceLevel.ACCEPTABLE, ConfidenceLevel.fromScore(74));
        assertEquals(ConfidenceLevel.ACCEPTABLE, ConfidenceLevel.fromScore(60));
        assertEquals(ConfidenceLevel.POOR, ConfidenceLevel.fromScore(59));
        assertEquals(ConfidenceLevel.POOR, ConfidenceLevel.fromScore(40));
        assertEquals(ConfidenceLevel.UNRELIABLE, ConfidenceLevel.fromScore(39));
        assertEquals(ConfidenceLevel.UNRELIABLE, ConfidenceLevel.fromScore(0));
    }

    @Test
    void onlyFiveLevelsExist() {
        assertEquals(5, ConfidenceLevel.values().length);
        for (ConfidenceLevel level : ConfidenceLevel.values()) {
            assertFalse(level.label.toLowerCase(java.util.Locale.ROOT).contains("smali"),
                    "No debe existir un nivel ligado a Smali: " + level);
        }
    }

    @Test
    void reliableLevelsAreTheTopThree() {
        assertTrue(ClassConfidence.builder("a").score(95).build().isReliable());
        assertTrue(ClassConfidence.builder("b").score(80).build().isReliable());
        assertTrue(ClassConfidence.builder("c").score(65).build().isReliable());
        assertFalse(ClassConfidence.builder("d").score(50).build().isReliable());
        assertFalse(ClassConfidence.builder("e").score(10).build().isReliable());
    }

    @Test
    void labelsAreSpanish() {
        assertEquals("Excelente", ConfidenceLevel.EXCELLENT.label);
        assertEquals("Bueno", ConfidenceLevel.GOOD.label);
        assertEquals("Aceptable", ConfidenceLevel.ACCEPTABLE.label);
        assertEquals("Pobre", ConfidenceLevel.POOR.label);
        assertEquals("No confiable", ConfidenceLevel.UNRELIABLE.label);
    }
}
