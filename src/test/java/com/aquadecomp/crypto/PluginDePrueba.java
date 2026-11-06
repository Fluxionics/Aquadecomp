package com.aquadecomp.crypto;

public final class PluginDePrueba implements DecryptionPlugin {

    @Override
    public String id() {
        return "plugin-de-prueba";
    }

    @Override
    public String description() {
        return "Plugin de prueba cargado por ServiceLoader";
    }

    @Override
    public PluginOutcome apply(String source) {
        return PluginOutcome.none();
    }
}
