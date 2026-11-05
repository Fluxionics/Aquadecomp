package com.aquadecomp.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectivenessCalculatorTest {

    private final EffectivenessCalculator calculator = EffectivenessCalculator.of();

    private ManifestInfo okManifest() {
        return new ManifestInfo("com.example", "1.0", "1", "com.example.App",
                false, true, List.of("android.permission.INTERNET"),
                List.of("com.example.MainActivity"), "com.example.MainActivity",
                true, null);
    }

    private DecompileStats stats(int total, int withCode, int errors, int warns,
                                 boolean resources, boolean manifestParsed) {
        return new DecompileStats(total, withCode, total - withCode, errors, warns,
                withCode, withCode * 100L, resources, manifestParsed, 1000);
    }

    private ObfuscationInfo cleanObfuscation() {
        return new ObfuscationInfo(100, 0, 1000, 0, 500, 0, 0, 0,
                0.0, 0.0, "NINGUNA", List.of(), false);
    }

    @Test
    void perfectCaseScoresOneHundred() {
        EffectivenessReport r = calculator.calculate(okManifest(), cleanObfuscation(),
                stats(100, 100, 0, 0, true, true), true);

        assertEquals(100.0, r.overallScore(), 0.001);
        assertEquals(100.0, r.coverageScore(), 0.001);
        assertEquals(100.0, r.integrityScore(), 0.001);
        assertEquals(100.0, r.identifierScore(), 0.001);
        assertEquals(100.0, r.resourceScore(), 0.001);
        assertTrue(r.strengths().stream().anyMatch(s -> s.contains("Sin errores")));
    }

    @Test
    void withoutResourcesTheWeightShifts() {
        EffectivenessReport r = calculator.calculate(okManifest(), cleanObfuscation(),
                stats(100, 100, 0, 0, false, true), false);

        assertEquals(-1.0, r.resourceScore(), 0.001);
        assertEquals(100.0, r.overallScore(), 0.001);
        assertTrue(r.resourceDetail().contains("omitidos"));
    }

    @Test
    void obfuscatedIdentifiersLowerTheScore() {
        ObfuscationInfo obf = new ObfuscationInfo(100, 50, 1000, 500, 500, 250, 10, 0,
                0.5, 0.0, "MEDIA", List.of(), false);

        EffectivenessReport r = calculator.calculate(okManifest(), obf,
                stats(100, 100, 0, 0, true, true), true);

        assertEquals(50.0, r.identifierScore(), 0.001);
        assertEquals(87.5, r.overallScore(), 0.001);
        assertTrue(r.limitations().stream().anyMatch(s -> s.contains("identificadores")));
    }

    @Test
    void decompilationErrorsReduceIntegrity() {
        EffectivenessReport r = calculator.calculate(okManifest(), cleanObfuscation(),
                stats(100, 90, 10, 0, true, true), true);

        assertEquals(90.0, r.coverageScore(), 0.001);
        assertEquals(90.0, r.integrityScore(), 0.001);
        assertTrue(r.limitations().stream().anyMatch(s -> s.contains("10 errores")));
    }

    @Test
    void missingManifestLowersResourceDimension() {
        EffectivenessReport r = calculator.calculate(ManifestInfo.failed("fallo"),
                cleanObfuscation(), stats(100, 100, 0, 0, true, false), true);

        assertEquals(40.0, r.resourceScore(), 0.001);
        assertEquals(94.0, r.overallScore(), 0.001);
        assertTrue(r.limitations().stream().anyMatch(s -> s.contains("AndroidManifest")));
    }

    @Test
    void zeroClassesProducesUnknownReport() {
        EffectivenessReport r = calculator.calculate(okManifest(), cleanObfuscation(),
                DecompileStats.empty(), true);

        assertEquals(0.0, r.overallScore(), 0.001);
        assertTrue(r.coverageDetail().contains("No se cargaron clases"));
        assertEquals("No disponible", r.methodology());
    }

    @Test
    void methodologyIsStatedInEveryReport() {
        EffectivenessReport r = calculator.calculate(okManifest(), cleanObfuscation(),
                stats(100, 100, 0, 0, true, true), true);
        assertEquals(EffectivenessCalculator.METHODOLOGY, r.methodology());
        assertTrue(r.methodology().contains("Limitación inevitable"));
    }

    @Test
    void partialCoverageKeepsScoreConsistent() {
        EffectivenessReport r = calculator.calculate(okManifest(), cleanObfuscation(),
                stats(200, 100, 0, 0, true, true), true);

        assertEquals(50.0, r.coverageScore(), 0.001);
        double expected = Math.round((0.40 * 0.5 + 0.25 * 1.0
                + 0.25 * 1.0 + 0.10 * 1.0) * 1000.0) / 10.0;
        assertEquals(expected, r.overallScore(), 0.001);
    }
}
