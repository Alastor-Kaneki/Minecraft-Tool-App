package com.alastorkaneki.nullforge;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class Project {
    public enum Edition {
        BEDROCK,
        JAVA
    }

    public enum Kind {
        BEDROCK_RESOURCE,
        BEDROCK_BEHAVIOR,
        BEDROCK_ADDON,
        BEDROCK_SKIN,
        BEDROCK_WORLD_TEMPLATE,
        JAVA_RESOURCE,
        JAVA_DATA,
        JAVA_COMBINED
    }

    public final String id;
    public String name;
    public final Edition edition;
    public final Kind kind;
    public final File root;
    public final long createdAt;
    public long modifiedAt;
    public String targetVersion;
    public int packFormat;

    public Project(String id, String name, Edition edition, Kind kind, File root, long createdAt, long modifiedAt, String targetVersion, int packFormat) {
        this.id = id;
        this.name = name;
        this.edition = edition;
        this.kind = kind;
        this.root = root;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.targetVersion = targetVersion;
        this.packFormat = packFormat;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("edition", edition.name());
        json.put("kind", kind.name());
        json.put("createdAt", createdAt);
        json.put("modifiedAt", modifiedAt);
        json.put("targetVersion", targetVersion);
        json.put("packFormat", packFormat);
        return json;
    }

    public static Project fromJson(JSONObject json, File root) throws JSONException {
        return new Project(
                json.getString("id"),
                json.getString("name"),
                Edition.valueOf(json.getString("edition")),
                Kind.valueOf(json.getString("kind")),
                root,
                json.optLong("createdAt", System.currentTimeMillis()),
                json.optLong("modifiedAt", System.currentTimeMillis()),
                json.optString("targetVersion", "1.21.0"),
                json.optInt("packFormat", 64)
        );
    }

    public String extension() {
        if (edition == Edition.JAVA) {
            return ".zip";
        }
        if (kind == Kind.BEDROCK_ADDON) {
            return ".mcaddon";
        }
        return ".mcpack";
    }

    public String kindLabel() {
        return switch (kind) {
            case BEDROCK_RESOURCE -> "Resource Pack";
            case BEDROCK_BEHAVIOR -> "Behavior Pack";
            case BEDROCK_ADDON -> "Add-On";
            case BEDROCK_SKIN -> "Skin Pack";
            case BEDROCK_WORLD_TEMPLATE -> "World Template";
            case JAVA_RESOURCE -> "Resource Pack";
            case JAVA_DATA -> "Data Pack";
            case JAVA_COMBINED -> "Resource + Data";
        };
    }
}
