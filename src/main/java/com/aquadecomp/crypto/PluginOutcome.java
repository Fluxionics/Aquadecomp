package com.aquadecomp.crypto;

import java.util.List;

public record PluginOutcome(List<Replacement> replacements, List<String> notes) {

    public record Replacement(int start, int end, String text) {
    }

    public PluginOutcome {
        replacements = replacements != null ? List.copyOf(replacements) : List.of();
        notes = notes != null ? List.copyOf(notes) : List.of();
    }

    public static PluginOutcome none() {
        return new PluginOutcome(List.of(), List.of());
    }

    public static PluginOutcome withNotes(List<String> notes) {
        return new PluginOutcome(List.of(), notes);
    }

    public static PluginOutcome withReplacements(List<Replacement> replacements) {
        return new PluginOutcome(replacements, List.of());
    }
}
