package com.aquadecomp.verification;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

public final class ConfidenceMap {

    private final Map<String, ClassConfidence> confidence = new HashMap<>();

    public void record(ClassConfidence cc) {
        confidence.put(cc.className(), cc);
    }

    public ClassConfidence get(String className) {
        return confidence.get(className);
    }

    public int getTotalClasses() {
        return confidence.size();
    }

    public int getExcellentCount() {
        return (int) confidence.values().stream()
                .filter(c -> c.level() == ConfidenceLevel.EXCELLENT)
                .count();
    }

    public int getGoodCount() {
        return (int) confidence.values().stream()
                .filter(c -> c.level() == ConfidenceLevel.GOOD)
                .count();
    }

    public int getAcceptableCount() {
        return (int) confidence.values().stream()
                .filter(c -> c.level() == ConfidenceLevel.ACCEPTABLE)
                .count();
    }

    public int getPoorCount() {
        return (int) confidence.values().stream()
                .filter(c -> c.level() == ConfidenceLevel.POOR)
                .count();
    }

    public int getUnreliableCount() {
        return (int) confidence.values().stream()
                .filter(c -> c.level() == ConfidenceLevel.UNRELIABLE)
                .count();
    }

    public double getAverageScore() {
        if (confidence.isEmpty()) return 0;
        return confidence.values().stream()
                .mapToInt(ClassConfidence::score)
                .average()
                .orElse(0);
    }

    public int getStructurallyValidCount() {
        return (int) confidence.values().stream()
                .filter(ClassConfidence::structurallyValid)
                .count();
    }

    public List<String> getByDecompiler(String decompiler) {
        return confidence.values().stream()
                .filter(c -> decompiler.equals(c.decompilerUsed()))
                .map(ClassConfidence::className)
                .collect(Collectors.toList());
    }

    public List<ClassConfidence> getAllByLevel(ConfidenceLevel level) {
        return confidence.values().stream()
                .filter(c -> c.level() == level)
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getDecompilerStats() {
        Map<String, Integer> stats = new HashMap<>();
        confidence.values().forEach(c -> {
            String decompiler = c.decompilerUsed();
            stats.put(decompiler, stats.getOrDefault(decompiler, 0) + 1);
        });
        return stats;
    }

    public ConfidenceStats getSummary() {
        return new ConfidenceStats(
                getTotalClasses(),
                getExcellentCount(),
                getGoodCount(),
                getAcceptableCount(),
                getPoorCount(),
                getUnreliableCount(),
                getAverageScore(),
                getStructurallyValidCount(),
                getDecompilerStats()
        );
    }

    public record ConfidenceStats(
            int total,
            int excellent,
            int good,
            int acceptable,
            int poor,
            int unreliable,
            double averageScore,
            int structurallyValid,
            Map<String, Integer> decompilerStats
    ) {
        public double getExcellentPercentage() {
            return total == 0 ? 0 : (excellent * 100.0) / total;
        }

        public double getReliablePercentage() {
            int reliable = excellent + good + acceptable;
            return total == 0 ? 0 : (reliable * 100.0) / total;
        }

        public double getStructurallyValidPercentage() {
            return total == 0 ? 0 : (structurallyValid * 100.0) / total;
        }

        public List<Map.Entry<String, Integer>> enginesOrdered() {
            return decompilerStats.entrySet().stream()
                    .sorted((a, b) -> b.getValue() != a.getValue()
                            ? Integer.compare(b.getValue(), a.getValue())
                            : a.getKey().compareTo(b.getKey()))
                    .toList();
        }
    }
}
