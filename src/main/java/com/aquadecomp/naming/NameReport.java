package com.aquadecomp.naming;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public record NameReport(String sourceDir, int filesScanned, List<NameSuggestion> suggestions,
                         List<String> notes) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public NameReport {
        suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
        notes = notes != null ? List.copyOf(notes) : List.of();
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
