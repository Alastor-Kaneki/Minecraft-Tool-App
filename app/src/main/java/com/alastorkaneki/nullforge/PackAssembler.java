package com.alastorkaneki.nullforge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PackAssembler {
    public interface Progress {
        void update(String message, int current, int total);
    }

    public record BuildResult(File file, String fileName, String mimeType, int included, List<String> missing) {
    }

    private record TreeFile(String path, long size) {
    }

    private PackAssembler() {
    }

    public static BuildResult build(Context context, TweakProvider provider, String section, List<TweakPack> selected, Progress progress) throws Exception {
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one pack");
        }
        File workRoot = new File(context.getCacheDir(), "tweak-build-" + System.nanoTime());
        File stage = new File(workRoot, "stage");
        stage.mkdirs();
        Map<String, List<TweakPack>> groups = new LinkedHashMap<>();
        for (TweakPack pack : selected) {
            groups.computeIfAbsent(pack.repository + "@" + pack.branch, ignored -> new ArrayList<>()).add(pack);
        }
        int total = selected.size();
        int current = 0;
        int included = 0;
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<TweakPack>> group : groups.entrySet()) {
            TweakPack first = group.getValue().get(0);
            List<TreeFile> tree = fetchTree(first.repository, first.branch);
            for (TweakPack pack : group.getValue()) {
                current++;
                progress.update("Adding " + pack.name, current, total);
                String prefix = resolvePrefix(tree, pack);
                if (prefix == null) {
                    missing.add(pack.name);
                    continue;
                }
                List<TreeFile> sourceFiles = sourceFiles(tree, prefix);
                if (sourceFiles.isEmpty()) {
                    missing.add(pack.name);
                    continue;
                }
                boolean wrote = false;
                for (TreeFile source : sourceFiles) {
                    String relative = relativePath(source.path(), prefix);
                    if (relative.isBlank()) {
                        relative = source.path().substring(source.path().lastIndexOf('/') + 1);
                    }
                    byte[] bytes = TweaksRepository.getBytes(raw(first.repository, first.branch, source.path()));
                    if (isArchive(source.path())) {
                        extractArchive(bytes, stage);
                    } else {
                        File target;
                        if (provider == TweakProvider.VANILLA) {
                            target = new File(stage, safe(pack.name) + "/" + relative);
                        } else {
                            target = new File(stage, relative);
                        }
                        write(target, bytes);
                    }
                    wrote = true;
                }
                if (wrote) {
                    included++;
                } else {
                    missing.add(pack.name);
                }
            }
        }
        writeSelected(stage, provider, section, selected, missing);
        if (provider != TweakProvider.VANILLA) {
            ensureBedrockManifest(stage, provider, section);
        } else {
            File note = new File(stage, "BUILD_REQUIRED.txt");
            write(note, ("This export contains source from VanillaTweaks/packs.\n"
                    + "The upstream repository states that these packs are built with Python, Poetry, and Beet.\n"
                    + "Run the upstream build process before installing the generated data packs.\n").getBytes(StandardCharsets.UTF_8));
        }
        String extension = provider == TweakProvider.VANILLA ? ".zip" : section.equals("Addons") ? ".mcaddon" : ".mcpack";
        String base = safe(provider.label + " " + section + " " + System.currentTimeMillis());
        File output = new File(context.getCacheDir(), base + extension);
        progress.update("Compressing pack", total, total);
        zip(stage, output);
        delete(workRoot);
        return new BuildResult(output, output.getName(), "application/zip", included, missing);
    }

    private static List<TreeFile> fetchTree(String repository, String branch) throws Exception {
        String endpoint = "https://api.github.com/repos/" + repository + "/git/trees/" + branch + "?recursive=1";
        JSONObject root = new JSONObject(TweaksRepository.getText(endpoint));
        JSONArray entries = root.getJSONArray("tree");
        List<TreeFile> files = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if ("blob".equals(entry.optString("type"))) {
                files.add(new TreeFile(entry.optString("path"), entry.optLong("size", 0)));
            }
        }
        return files;
    }

    private static String resolvePrefix(List<TreeFile> tree, TweakPack pack) {
        if (!pack.sourcePath.isBlank()) {
            return pack.sourcePath;
        }
        String root = sourceRoot(pack);
        String target = pack.normalizedId();
        Set<String> candidates = new LinkedHashSet<>();
        for (TreeFile file : tree) {
            String path = file.path();
            if (!path.startsWith(root + "/") && !path.equals(root)) {
                continue;
            }
            String[] parts = path.split("/");
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) {
                    prefix.append('/');
                }
                prefix.append(parts[i]);
                if (TweakPack.normalize(parts[i]).equals(target)) {
                    candidates.add(prefix.toString());
                }
            }
            String fileName = parts[parts.length - 1];
            int dot = fileName.lastIndexOf('.');
            String bare = dot > 0 ? fileName.substring(0, dot) : fileName;
            if (TweakPack.normalize(bare).equals(target) && isArchive(path)) {
                candidates.add(path);
            }
        }
        if (candidates.isEmpty()) {
            List<String> fuzzy = new ArrayList<>();
            for (TreeFile file : tree) {
                String path = file.path();
                if (!path.startsWith(root + "/")) {
                    continue;
                }
                String normalized = TweakPack.normalize(path);
                if (normalized.contains(target)) {
                    int slash = path.lastIndexOf('/');
                    fuzzy.add(slash > 0 ? path.substring(0, slash) : path);
                }
            }
            fuzzy.sort(Comparator.comparingInt(String::length));
            return fuzzy.isEmpty() ? null : fuzzy.get(0);
        }
        return candidates.stream()
                .sorted(Comparator.comparingInt(value -> prefixScore(tree, value)))
                .findFirst()
                .orElse(null);
    }

    private static int prefixScore(List<TreeFile> tree, String prefix) {
        int files = 0;
        for (TreeFile file : tree) {
            if (file.path().equals(prefix) || file.path().startsWith(prefix + "/")) {
                files++;
            }
        }
        return files == 0 ? Integer.MAX_VALUE : prefix.length() - Math.min(files, 1000);
    }

    private static List<TreeFile> sourceFiles(List<TreeFile> tree, String prefix) {
        List<TreeFile> files = new ArrayList<>();
        for (TreeFile file : tree) {
            if (file.path().equals(prefix) || file.path().startsWith(prefix + "/")) {
                files.add(file);
            }
        }
        return files;
    }

    private static String sourceRoot(TweakPack pack) {
        if (pack.provider == TweakProvider.BECOM) {
            return "packs";
        }
        if (pack.provider == TweakProvider.BEDROCK) {
            return switch (pack.section) {
                case "Resource Packs" -> "resource_packs";
                case "Addons" -> "addons";
                default -> "crafting_tweaks";
            };
        }
        return "datapacks";
    }

    private static String relativePath(String path, String prefix) {
        if (path.equals(prefix)) {
            return "";
        }
        String value = path.startsWith(prefix + "/") ? path.substring(prefix.length() + 1) : path;
        String lower = value.toLowerCase(Locale.ROOT);
        String[] removable = {
                "files/",
                "pack/",
                "resource_pack/",
                "behavior_pack/",
                "behaviour_pack/"
        };
        for (String start : removable) {
            if (lower.startsWith(start)) {
                return value.substring(start.length());
            }
        }
        return value;
    }

    private static void ensureBedrockManifest(File stage, TweakProvider provider, String section) throws Exception {
        File manifest = new File(stage, "manifest.json");
        if (manifest.isFile()) {
            return;
        }
        boolean behavior = section.equals("Addons") || section.equals("Crafting Tweaks") || section.equals("Behaviour Packs");
        String type = behavior ? "data" : "resources";
        JSONObject root = new JSONObject();
        root.put("format_version", 2);
        JSONObject header = new JSONObject();
        header.put("name", "NullForge " + provider.label + " Selection");
        header.put("description", "Assembled natively by NullForge Studio. Credits remain with " + provider.label + " and the original pack creators.");
        header.put("uuid", UUID.randomUUID().toString());
        header.put("version", new JSONArray(new int[]{1, 0, 0}));
        header.put("min_engine_version", new JSONArray(new int[]{1, 21, 0}));
        root.put("header", header);
        JSONObject module = new JSONObject();
        module.put("type", type);
        module.put("uuid", UUID.randomUUID().toString());
        module.put("version", new JSONArray(new int[]{1, 0, 0}));
        root.put("modules", new JSONArray().put(module));
        JSONObject metadata = new JSONObject();
        metadata.put("authors", new JSONArray().put(provider.label).put("NullForge Studio assembler"));
        metadata.put("url", provider.repository);
        root.put("metadata", metadata);
        write(manifest, root.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeSelected(File stage, TweakProvider provider, String section, List<TweakPack> selected, List<String> missing) throws Exception {
        StringBuilder text = new StringBuilder();
        text.append(provider.label).append(" — ").append(section).append('\n');
        text.append("Assembled by NullForge Studio\n\n");
        for (TweakPack pack : selected) {
            text.append(missing.contains(pack.name) ? "[missing] " : "[included] ");
            text.append(pack.name).append(" — ").append(pack.category).append('\n');
        }
        text.append("\nCredits: ").append(provider.website).append('\n');
        text.append("Source: ").append(provider.repository).append('\n');
        write(new File(stage, "Selected Packs.txt"), text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isArchive(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".mcpack") || lower.endsWith(".mcaddon");
    }

    private static void extractArchive(byte[] bytes, File stage) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                File target = safeTarget(stage, entry.getName());
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                        byte[] buffer = new byte[32768];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                input.closeEntry();
            }
        }
    }

    private static File safeTarget(File root, String path) throws Exception {
        File target = new File(root, path);
        String rootPath = root.getCanonicalPath() + File.separator;
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath)) {
            throw new IllegalStateException("Unsafe archive path");
        }
        return target;
    }

    private static void write(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }

    private static void zip(File source, File output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            addDirectory(source, source, zip);
        }
    }

    private static void addDirectory(File root, File current, ZipOutputStream zip) throws Exception {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                addDirectory(root, child, zip);
            } else {
                String name = root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/');
                zip.putNextEntry(new ZipEntry(name));
                try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(child))) {
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String raw(String repository, String branch, String path) {
        return "https://raw.githubusercontent.com/" + repository + "/" + branch + "/" + TweaksRepository.encodePath(path);
    }

    private static String safe(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._ -]", "").trim().replace(' ', '-');
        return cleaned.isBlank() ? "NullForge-Tweaks" : cleaned;
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }
        file.delete();
    }
}
