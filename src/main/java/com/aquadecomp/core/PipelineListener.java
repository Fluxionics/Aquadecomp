package com.aquadecomp.core;

public interface PipelineListener {

    void onStage(String stage);

    void onProgress(int percent, String message);

    void onLog(String line);

    PipelineListener CONSOLE = new PipelineListener() {
        @Override
        public void onStage(String stage) {
            System.out.println("\n==> " + stage);
        }

        @Override
        public void onProgress(int percent, String message) {
            System.out.printf("    [%3d%%] %s%n", percent, message);
        }

        @Override
        public void onLog(String line) {
            System.out.println("    " + line);
        }
    };

    PipelineListener SILENT = new PipelineListener() {
        @Override
        public void onStage(String stage) {
        }

        @Override
        public void onProgress(int percent, String message) {
        }

        @Override
        public void onLog(String line) {
        }
    };
}
