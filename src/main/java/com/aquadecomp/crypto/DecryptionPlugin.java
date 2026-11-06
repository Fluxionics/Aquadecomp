package com.aquadecomp.crypto;

import java.util.List;

public interface DecryptionPlugin {

    String id();

    String description();

    PluginOutcome apply(String source);
}
