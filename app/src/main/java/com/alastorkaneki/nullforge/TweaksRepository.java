package com.alastorkaneki.nullforge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TweaksRepository {
    public record Catalog(List<TweakPack> packs, String notice) {
    }

    private static final long CACHE_AGE = 6L * 60L * 60L * 1000L;

    private TweaksRepository() {
    }

    public static Catalog load(Context context, TweakProvider provider, String section, boolean force) throws Exception {
        File cache = cacheFile(context, provider, section);
        if (!force && cache.isFile() && System.currentTimeMillis() - cache.lastModified() < CACHE_AGE) {
            Catalog saved = readCache(cache);
            if (saved != null) {
                return saved;
            }
        }
        Catalog catalog = switch (provider) {
            case VANILLA -> loadVanilla(section);
            case BEDROCK -> loadBedrock(section);
            case BECOM -> loadBecom(section);
        };
        writeCache(cache, catalog);
        return catalog;
    }

    public static byte[] getBytes(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(40000);
        connection.setRequestProperty("User-Agent", "NullForge-Studio");
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IllegalStateException("Request failed with HTTP " + status);
        }
        try (var input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    public static String getText(String url) throws Exception {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    private static Catalog loadBedrock(String section) throws Exception {
        String path = switch (section) {
            case "Resource Packs" -> "resource_packs";
            case "Addons" -> "addons";
            default -> "crafting_tweaks";
        };
        String source = "https://raw.githubusercontent.com/BedrockTweaks/Files/devel/" + path + "/packs.json";
        JSONObject root = new JSONObject(getText(source));
        JSONArray categories = root.optJSONArray("categories");
        List<TweakPack> packs = new ArrayList<>();
        if (categories != null) {
            for (int i = 0; i < categories.length(); i++) {
                JSONObject category = categories.getJSONObject(i);
                String categoryName = category.optString("name", category.optString("id", "Other"));
                JSONArray list = category.optJSONArray("packs");
                if (list == null) {
                    continue;
                }
                for (int p = 0; p < list.length(); p++) {
                    JSONObject item = list.getJSONObject(p);
                    packs.add(new TweakPack(
                            TweakProvider.BEDROCK,
                            section,
                            categoryName,
                            item.optString("id"),
                            item.optString("name", item.optString("id")),
                            item.optString("description"),
                            conflicts(item),
                            "BedrockTweaks/Files",
                            "devel",
                            "",
                            false
                    ));
                }
            }
        }
        return new Catalog(packs, "Catalog loaded from BedrockTweaks/Files. Pack files are assembled natively from the public repository.");
    }

    private static Catalog loadBecom(String section) throws Exception {
        String repo = switch (section) {
            case "Resource Packs" -> "BEComTweaks/resource-packs";
            case "Behaviour Packs" -> "BEComTweaks/behaviour-packs";
            default -> "BEComTweaks/crafting-tweaks";
        };
        JSONObject tree = new JSONObject(getText("https://api.github.com/repos/" + repo + "/git/trees/main?recursive=1"));
        JSONArray entries = tree.getJSONArray("tree");
        List<String> jsonPaths = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String path = entry.optString("path");
            if ("blob".equals(entry.optString("type")) && path.startsWith("jsons/packs/") && path.endsWith(".json")) {
                jsonPaths.add(path);
            }
        }
        Collections.sort(jsonPaths);
        List<TweakPack> packs = new ArrayList<>();
        for (String path : jsonPaths) {
            JSONObject category = new JSONObject(getText(raw(repo, "main", path)));
            String topic = category.optString("topic", readable(path.substring(path.lastIndexOf('/') + 1, path.length() - 5)));
            JSONArray list = category.optJSONArray("packs");
            if (list == null) {
                continue;
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                String id = item.optString("pack_id");
                packs.add(new TweakPack(
                        TweakProvider.BECOM,
                        section,
                        topic,
                        id,
                        item.optString("pack_name", id),
                        item.optString("pack_description"),
                        conflicts(item),
                        repo,
                        "main",
                        "",
                        false
                ));
            }
        }
        return new Catalog(packs, "Catalog loaded from the separate BEComTweaks " + section.toLowerCase(Locale.ROOT) + " repository.");
    }

    private static Catalog loadVanilla(String section) throws Exception {
        if (!"Data Packs".equals(section)) {
            return new Catalog(
                    new ArrayList<>(),
                    "VanillaTweaks/packs publishes source for some data packs only. The complete Resource Packs and Crafting Tweaks catalogs are not available in that public GitHub repository."
            );
        }
        String repo = "VanillaTweaks/packs";
        JSONObject tree = new JSONObject(getText("https://api.github.com/repos/" + repo + "/git/trees/main?recursive=1"));
        JSONArray entries = tree.getJSONArray("tree");
        Map<String, Set<String>> versions = new LinkedHashMap<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String path = entry.optString("path");
            String[] pieces = path.split("/");
            if (pieces.length >= 3 && "datapacks".equals(pieces[0])) {
                versions.computeIfAbsent(pieces[1], ignored -> new LinkedHashSet<>()).add(pieces[2]);
            }
        }
        List<String> versionNames = new ArrayList<>(versions.keySet());
        versionNames.sort(Comparator.reverseOrder());
        List<TweakPack> packs = new ArrayList<>();
        for (String version : versionNames) {
            List<String> names = new ArrayList<>(versions.get(version));
            Collections.sort(names);
            for (String folder : names) {
                packs.add(new TweakPack(
                        TweakProvider.VANILLA,
                        section,
                        "Minecraft " + version,
                        folder,
                        readable(folder),
                        "Source package from the official VanillaTweaks/packs repository.",
                        new ArrayList<>(),
                        repo,
                        "main",
                        "datapacks/" + version + "/" + folder,
                        true
                ));
            }
        }
        return new Catalog(
                packs,
                "The official public repository contains source for some Vanilla Tweaks data packs. Exports from this tab are source bundles and may require the upstream Beet build process before use."
        );
    }

    private static List<String> conflicts(JSONObject item) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addArray(values, item.optJSONArray("conflict"));
        addArray(values, item.optJSONArray("conflicts"));
        addArray(values, item.optJSONArray("obvious_conflict"));
        addArray(values, item.optJSONArray("obvious_conflicts"));
        return new ArrayList<>(values);
    }

    private static void addArray(Set<String> values, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
    }

    private static String readable(String value) {
        String cleaned = value.replace('_', ' ').replace('-', ' ').trim();
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isWhitespace(c)) {
                upper = true;
                out.append(c);
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    private static String raw(String repository, String branch, String path) {
        return "https://raw.githubusercontent.com/" + repository + "/" + branch + "/" + encodePath(path);
    }

    public static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
        for (byte raw : bytes) {
            int value = raw & 0xff;
            char c = (char) value;
            if (Character.isLetterOrDigit(c) || c == '/' || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append('%');
                String hex = Integer.toHexString(value).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    out.append('0');
                }
                out.append(hex);
            }
        }
        return out.toString();
    }

    private static File cacheFile(Context context, TweakProvider provider, String section) {
        File dir = new File(context.getFilesDir(), "tweaks-cache");
        dir.mkdirs();
        return new File(dir, provider.name().toLowerCase(Locale.ROOT) + "-" + TweakPack.normalize(section) + ".json");
    }

    private static void writeCache(File file, Catalog catalog) {
        try {
            JSONObject root = new JSONObject();
            root.put("notice", catalog.notice());
            JSONArray list = new JSONArray();
            for (TweakPack pack : catalog.packs()) {
                JSONObject item = new JSONObject();
                item.put("provider", pack.provider.name());
                item.put("section", pack.section);
                item.put("category", pack.category);
                item.put("id", pack.id);
                item.put("name", pack.name);
                item.put("description", pack.description);
                item.put("conflicts", new JSONArray(pack.conflicts));
                item.put("repository", pack.repository);
                item.put("branch", pack.branch);
                item.put("sourcePath", pack.sourcePath);
                item.put("sourceOnly", pack.sourceOnly);
                list.put(item);
            }
            root.put("packs", list);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static Catalog readCache(File file) {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            JSONObject root = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            JSONArray list = root.getJSONArray("packs");
            List<TweakPack> packs = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                JSONArray conflictArray = item.optJSONArray("conflicts");
                List<String> conflicts = new ArrayList<>();
                if (conflictArray != null) {
                    for (int c = 0; c < conflictArray.length(); c++) {
                        conflicts.add(conflictArray.optString(c));
                    }
                }
                packs.add(new TweakPack(
                        TweakProvider.valueOf(item.getString("provider")),
                        item.getString("section"),
                        item.getString("category"),
                        item.getString("id"),
                        item.getString("name"),
                        item.optString("description"),
                        conflicts,
                        item.getString("repository"),
                        item.getString("branch"),
                        item.optString("sourcePath"),
                        item.optBoolean("sourceOnly")
                ));
            }
            return new Catalog(packs, root.optString("notice"));
        } catch (Exception ignored) {
            return null;
        }
    }
}
