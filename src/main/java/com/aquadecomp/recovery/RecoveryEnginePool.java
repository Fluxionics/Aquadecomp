package com.aquadecomp.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class RecoveryEnginePool {

    private final BlockingQueue<RecoveryEngineSet> availableEngines;
    private final int poolSize;

    public RecoveryEnginePool(int poolSize) {
        this.poolSize = Math.max(1, poolSize);
        this.availableEngines = new LinkedBlockingQueue<>(this.poolSize);
        initializePool();
    }

    private void initializePool() {
        for (int i = 0; i < poolSize; i++) {
            try {
                availableEngines.put(new RecoveryEngineSet());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to initialize engine pool", e);
            }
        }
    }

    public RecoveryEngineSet getEngineSet() throws InterruptedException {
        return availableEngines.take();
    }

    public void releaseEngineSet(RecoveryEngineSet engines) {
        try {
            availableEngines.put(engines);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getAvailableEngines() {
        return availableEngines.size();
    }

    public static final class RecoveryEngineSet {
        public final NameRecoveryEngine nameRecovery;
        public final DeobfuscationEngine deobfuscationEngine;
        public final StringDecryptionEngine stringDecryption;
        public final ClassRecoveryEngine classRecovery;
        public final MetadataExtractor metadataExtractor;
        public final RealCompilationVerifier compilationVerifier;

        public RecoveryEngineSet() {
            this.nameRecovery = new NameRecoveryEngine();
            this.deobfuscationEngine = new DeobfuscationEngine(nameRecovery);
            this.stringDecryption = new StringDecryptionEngine();
            this.classRecovery = new ClassRecoveryEngine();
            this.metadataExtractor = new MetadataExtractor();
            this.compilationVerifier = new RealCompilationVerifier();
        }
    }
}
