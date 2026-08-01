package com.alastorkaneki.nullforge;

import java.util.ArrayList;
import java.util.List;

public final class TweakPack {
    public final TweakProvider provider;
    public final String section;
    public final String category;
    public final String id;
    public final String name;
    public final String description;
    public final List<String> conflicts;
    public final String repository;
    public final String branch;
    public final String sourcePath;
    public final boolean sourceOnly;

    public TweakPack(
            TweakProvider provider,
            String section,
            String category,
            String id,
            String name,
            String description,
            List<String> conflicts,
            String repository,
            String branch,
            String sourcePath,
            boolean sourceOnly
    ) {
        this.provider = provider;
        this.section = section;
        this.category = category;
        this.id = id;
        this.name = name;
        this.description = description;
        this.conflicts = conflicts == null ? new ArrayList<>() : conflicts;
        this.repository = repository;
        this.branch = branch;
        this.sourcePath = sourcePath;
        this.sourceOnly = sourceOnly;
    }

    public String key() {
        return provider.name() + ":" + section + ":" + id;
    }

    public String normalizedId() {
        return normalize(id);
    }

    public static String normalize(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
