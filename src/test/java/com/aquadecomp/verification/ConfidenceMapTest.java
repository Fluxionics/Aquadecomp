package com.aquadecomp.verification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceMapTest {

    private ClassConfidence of(String name, int score, String engine, boolean valid) {
        return ClassConfidence.builder(name)
                .decompiler(engine)
                .score(score)
                .structurallyValid(valid)
                .build();
    }

    @Test
    void builderDerivesLevelAndClampsScore() {
        assertEquals(ConfidenceLevel.EXCELLENT, of("a", 95, "JADX", true).level());
        assertEquals(ConfidenceLevel.GOOD, of("b", 80, "JADX", true).level());
        assertEquals(ConfidenceLevel.UNRELIABLE, of("c", 10, "JADX", false).level());

        ClassConfidence tooHigh = ClassConfidence.builder("d").score(150).build();
        assertEquals(100, tooHigh.score());
        ClassConfidence tooLow = ClassConfidence.builder("e").score(-20).build();
        assertEquals(0, tooLow.score());
    }

    @Test
    void summaryCountsByLevel() {
        ConfidenceMap map = new ConfidenceMap();
        map.record(of("a", 95, "JADX", true));
        map.record(of("b", 80, "JADX", true));
        map.record(of("c", 65, "JADX", true));
        map.record(of("d", 50, "CFR", false));
        map.record(of("e", 10, "CFR", false));

        ConfidenceMap.ConfidenceStats stats = map.getSummary();
        assertEquals(5, stats.total());
        assertEquals(1, stats.excellent());
        assertEquals(1, stats.good());
        assertEquals(1, stats.acceptable());
        assertEquals(1, stats.poor());
        assertEquals(1, stats.unreliable());
        assertEquals(60.0, stats.averageScore(), 0.001);
        assertEquals(3, stats.structurallyValid());
        assertEquals(60.0, stats.getReliablePercentage(), 0.001);
        assertEquals(20.0, stats.getExcellentPercentage(), 0.001);
        assertEquals(60.0, stats.getStructurallyValidPercentage(), 0.001);
        assertEquals(3, stats.decompilerStats().get("JADX"));
        assertEquals(2, stats.decompilerStats().get("CFR"));
    }

    @Test
    void emptyMapHasZeroSummary() {
        ConfidenceMap.ConfidenceStats stats = new ConfidenceMap().getSummary();
        assertEquals(0, stats.total());
        assertEquals(0.0, stats.averageScore(), 0.001);
        assertEquals(0.0, stats.getReliablePercentage(), 0.001);
        assertEquals(0.0, stats.getStructurallyValidPercentage(), 0.001);
    }

    @Test
    void queriesFilterByLevelAndEngine() {
        ConfidenceMap map = new ConfidenceMap();
        map.record(of("a", 95, "JADX", true));
        map.record(of("b", 80, "CFR", true));

        assertEquals(1, map.getAllByLevel(ConfidenceLevel.EXCELLENT).size());
        assertEquals(List.of("b"), map.getByDecompiler("CFR"));
        assertEquals("a", map.get("a").className());
        assertEquals(2, map.getTotalClasses());
    }

    @Test
    void reliableFlagMatchesLevel() {
        assertTrue(of("a", 95, "JADX", true).isReliable());
        assertTrue(of("b", 65, "JADX", true).isReliable());
        assertTrue(!of("c", 30, "JADX", false).isReliable());
    }
}
