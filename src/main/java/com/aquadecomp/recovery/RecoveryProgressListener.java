package com.aquadecomp.recovery;

import java.nio.file.Path;

public interface RecoveryProgressListener {

    void onRecoveryStart(int totalFiles, int threadCount);

    void onFileStart(Path file, int fileNumber, int totalFiles);

    void onStringDecryptionComplete(Path file, int stringsRecovered);

    void onNameRecoveryComplete(Path file, int namesRecovered);

    void onDeobfuscationComplete(Path file, int variablesRecovered);

    void onClassRecoveryComplete(Path file, int classesRecovered);

    void onCompilationComplete(Path file, int compilationScore, boolean success);

    void onFileComplete(Path file, int fileNumber, int totalFiles, RecoveryResult fileResult);

    void onRecoveryComplete(int totalFiles, int filesRecovered, double recoveryRate);

    void onError(Path file, String errorMessage);

    record RecoveryResult(
            int compilationScore,
            boolean wasRecovered,
            boolean compiles,
            long processingTimeMs
    ) {
    }

    class RecoveryProgressAdapter implements RecoveryProgressListener {
        @Override
        public void onRecoveryStart(int totalFiles, int threadCount) {
        }

        @Override
        public void onFileStart(Path file, int fileNumber, int totalFiles) {
        }

        @Override
        public void onStringDecryptionComplete(Path file, int stringsRecovered) {
        }

        @Override
        public void onNameRecoveryComplete(Path file, int namesRecovered) {
        }

        @Override
        public void onDeobfuscationComplete(Path file, int variablesRecovered) {
        }

        @Override
        public void onClassRecoveryComplete(Path file, int classesRecovered) {
        }

        @Override
        public void onCompilationComplete(Path file, int compilationScore, boolean success) {
        }

        @Override
        public void onFileComplete(Path file, int fileNumber, int totalFiles, RecoveryResult fileResult) {
        }

        @Override
        public void onRecoveryComplete(int totalFiles, int filesRecovered, double recoveryRate) {
        }

        @Override
        public void onError(Path file, String errorMessage) {
        }
    }
}
