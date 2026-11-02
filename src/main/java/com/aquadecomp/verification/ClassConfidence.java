package com.aquadecomp.verification;

import java.util.List;

public record ClassConfidence(
        String className,
        String decompilerUsed,
        ConfidenceLevel level,
        int score,
        List<String> issues,
        boolean compilesSuccessfully,
        String sourceFormat
) {
    public boolean isReliable() {
        return level == ConfidenceLevel.EXCELLENT
                || level == ConfidenceLevel.GOOD
                || level == ConfidenceLevel.ACCEPTABLE;
    }

    public static ClassConfidence.Builder builder(String className) {
        return new ClassConfidence.Builder(className);
    }

    public static final class Builder {
        private final String className;
        private String decompilerUsed;
        private ConfidenceLevel level;
        private int score = 50;
        private List<String> issues = java.util.Collections.emptyList();
        private boolean compilesSuccessfully;
        private String sourceFormat = "JAVA";

        public Builder(String className) {
            this.className = className;
        }

        public Builder decompiler(String decompiler) {
            this.decompilerUsed = decompiler;
            return this;
        }

        public Builder level(ConfidenceLevel level) {
            this.level = level;
            return this;
        }

        public Builder score(int score) {
            this.score = Math.min(100, Math.max(0, score));
            this.level = ConfidenceLevel.fromScore(this.score);
            return this;
        }

        public Builder issues(List<String> issues) {
            this.issues = issues != null ? issues : java.util.Collections.emptyList();
            return this;
        }

        public Builder compiles(boolean success) {
            this.compilesSuccessfully = success;
            return this;
        }

        public Builder format(String format) {
            this.sourceFormat = format;
            return this;
        }

        public ClassConfidence build() {
            return new ClassConfidence(
                    className,
                    decompilerUsed,
                    level,
                    score,
                    issues,
                    compilesSuccessfully,
                    sourceFormat
            );
        }
    }
}
