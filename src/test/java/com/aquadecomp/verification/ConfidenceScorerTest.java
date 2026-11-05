package com.aquadecomp.verification;

import com.aquadecomp.core.PipelineListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceScorerTest {

    @TempDir
    Path tmp;

    private Path writeSource(String relative, String content) throws IOException {
        Path file = tmp.resolve("sources").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static String unit(String name) {
        return "package com.example;\n\npublic class " + name + " {\n"
                + "    public int value() {\n        return 1;\n    }\n}\n";
    }

    @Test
    void eachFileIsLabeledWithTheEngineThatProducedIt() throws IOException {
        writeSource("com/example/AlphaOne.java", unit("AlphaOne"));
        writeSource("com/example/BetaTwo.java", unit("BetaTwo"));

        ConfidenceScorer scorer = new ConfidenceScorer();
        scorer.scoreAll(Map.of("com/example/BetaTwo.java", "CFR"),
                tmp.resolve("sources"), Map.of(), PipelineListener.SILENT);

        ConfidenceMap.ConfidenceStats stats = scorer.getSummary();
        assertEquals(2, stats.total());
        assertEquals(1, stats.decompilerStats().get("JADX"));
        assertEquals(1, stats.decompilerStats().get("CFR"));
        assertEquals(List.of("com.example.BetaTwo"), scorer.getMap().getByDecompiler("CFR"));
        assertEquals("JADX", scorer.getMap().get("com.example.AlphaOne").decompilerUsed());
        assertEquals("CFR", scorer.getMap().get("com.example.BetaTwo").decompilerUsed());
    }

    @Test
    void engineIdentityDoesNotChangeTheScore() throws IOException {
        writeSource("com/example/AlphaOne.java", unit("AlphaOne"));
        writeSource("com/example/BetaTwo.java", unit("BetaTwo"));

        ConfidenceScorer scorer = new ConfidenceScorer();
        scorer.scoreAll(Map.of("com/example/BetaTwo.java", "CFR"),
                tmp.resolve("sources"), Map.of(), PipelineListener.SILENT);

        int withJadx = scorer.getMap().get("com.example.AlphaOne").score();
        int withCfr = scorer.getMap().get("com.example.BetaTwo").score();
        assertEquals(withJadx, withCfr,
                "el puntaje no debe depender del motor, sino de la evidencia del archivo");
    }

    @Test
    void emptyEngineMapLabelsEverythingAsJadx() throws IOException {
        writeSource("com/example/AlphaOne.java", unit("AlphaOne"));

        ConfidenceScorer scorer = new ConfidenceScorer();
        scorer.scoreAll(Map.of(), tmp.resolve("sources"), Map.of(), PipelineListener.SILENT);

        assertEquals(1, scorer.getSummary().decompilerStats().get("JADX"));
    }

    @Test
    void summaryCountsEveryEngineInvolved() throws IOException {
        writeSource("com/example/AlphaOne.java", unit("AlphaOne"));
        writeSource("com/example/BetaTwo.java", unit("BetaTwo"));
        writeSource("com/example/GammaThree.java", unit("GammaThree"));

        ConfidenceScorer scorer = new ConfidenceScorer();
        scorer.scoreAll(Map.of(
                        "com/example/BetaTwo.java", "CFR",
                        "com/example/GammaThree.java", "Procyon"),
                tmp.resolve("sources"), Map.of(), PipelineListener.SILENT);

        Map<String, Integer> stats = scorer.getSummary().decompilerStats();
        assertEquals(1, stats.get("JADX"));
        assertEquals(1, stats.get("CFR"));
        assertEquals(1, stats.get("Procyon"));
        assertTrue(scorer.getSummary().enginesOrdered().size() == 3);
    }
}
