package com.aquadecomp.crypto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class PluginRegistry {

    private PluginRegistry() {
    }

    public static List<DecryptionPlugin> plugins() {
        Map<String, DecryptionPlugin> byId = new LinkedHashMap<>();
        for (DecryptionPlugin plugin : builtIns()) {
            byId.put(plugin.id(), plugin);
        }
        for (DecryptionPlugin plugin : ServiceLoader.load(DecryptionPlugin.class)) {
            byId.putIfAbsent(plugin.id(), plugin);
        }
        return List.copyOf(byId.values());
    }

    public static List<DecryptionPlugin> builtIns() {
        List<DecryptionPlugin> plugins = new ArrayList<>();
        plugins.add(new AesEmbeddedKeyPlugin());
        plugins.add(new Rc4EmbeddedKeyPlugin());
        plugins.add(new XorConstantPlugin());
        return List.copyOf(plugins);
    }
}
