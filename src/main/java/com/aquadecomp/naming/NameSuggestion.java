package com.aquadecomp.naming;

import java.util.List;

public record NameSuggestion(String file, String kind, String current, String suggested,
                             int confidence, List<String> signals) {

    public NameSuggestion {
        signals = signals != null ? List.copyOf(signals) : List.of();
    }
}
