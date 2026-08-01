package com.alastorkaneki.nullforge;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectAssetImporter {
    public static final class ProjectTarget {
        public final String name;
        public final String edition;
        public final String kind;
        public final File root;

        public ProjectTarget(String name, String edition, String kind, File root) {
            this.name = name;
            this.edition = edition;
            this.kind = kind;
            this.root = root;
        }
    }

    public static final class Result {
        public int copied;
        public int overwritten;
        public int skipped;
    }

    private ProjectAssetImporter() {
    }

    public static List<ProjectTarget> listProjects(File filesDirectory, String edition) {
        List<ProjectTarget> projects = new ArrayList<>();
        File directory = new File(filesDirectory, "projects");
        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) {
            return projects;
        }
        for (File child : children) {
            File metadata = new File(child, "project.json");
            if (!metadata.isFile()) {
                continue;
            }
            try {
                JSONObject json = new JSONObject(new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8));
                String projectEdition = json.optString("edition", "");
                if (!edition.equalsIgnoreCase(projectEdition)) {
                    continue;
                }
                projects.add(new ProjectTarget(
                        json.optString("name", child.getName()),
                        projectEdition,
                        json.optString("kind", ""),
                        child
                ));
            } catch (Exception ignored) {
            }
        }
        return projects;
    }

    public static Result importSelections(File snapshotRoot, String edition, Set<String> selections, ProjectTarget project) throws Exception {
        Result result = new Result();
        Set<String> normalized = collapseSelections(selections);
        for (String relative : normalized) {
            File source = safeChild(snapshotRoot, relative);
            if (!source.exists()) {
                result.skipped++;
                continue;
            }
            copySelection(snapshotRoot, source, relative, edition, project, result);
        }
        return result;
    }

    private static void copySelection(File snapshotRoot, File source, String relative, String edition, ProjectTarget project, Result result) throws Exception {
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null || children.length == 0) {
                return;
            }
            for (File child : children) {
                String childRelative = relative.isEmpty() ? child.getName() : relative + "/" + child.getName();
                copySelection(snapshotRoot, child, childRelative, edition, project, result);
            }
            return;
        }
        String destinationPath = mapDestination(relative, edition, project.kind);
        if (destinationPath == null || destinationPath.isEmpty()) {
            result.skipped++;
            return;
        }
        File destination = safeChild(project.root, destinationPath);
        File parent = destination.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (destination.exists()) {
            result.overwritten++;
        }
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        result.copied++;
    }

    private static String mapDestination(String relative, String edition, String kind) {
        String clean = relative.replace('\\', '/');
        if ("JAVA".equalsIgnoreCase(edition)) {
            if (kind.equals("JAVA_RESOURCE") && clean.startsWith("data/")) {
                return null;
            }
            if (kind.equals("JAVA_DATA") && clean.startsWith("assets/")) {
                return null;
            }
            return clean;
        }
        if (kind.equals("BEDROCK_ADDON")) {
            return clean;
        }
        if (kind.equals("BEDROCK_RESOURCE")) {
            if (clean.startsWith("behavior_pack/")) {
                return null;
            }
            return stripPrefix(clean, "resource_pack/");
        }
        if (kind.equals("BEDROCK_BEHAVIOR")) {
            if (clean.startsWith("resource_pack/")) {
                return null;
            }
            return stripPrefix(clean, "behavior_pack/");
        }
        return clean;
    }

    private static String stripPrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static Set<String> collapseSelections(Set<String> selections) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : selections) {
            String clean = value.replace('\\', '/');
            boolean covered = false;
            for (String existing : result) {
                if (clean.equals(existing) || clean.startsWith(existing + "/")) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                result.removeIf(existing -> existing.startsWith(clean + "/"));
                result.add(clean);
            }
        }
        return result;
    }

    private static File safeChild(File root, String relative) throws IOException {
        File target = new File(root, relative);
        String canonicalRoot = root.getCanonicalPath();
        String rootPath = canonicalRoot + File.separator;
        String targetPath = target.getCanonicalPath();
        if (!targetPath.equals(canonicalRoot) && !targetPath.startsWith(rootPath)) {
            throw new IOException("Unsafe path: " + relative);
        }
        return target;
    }
}
