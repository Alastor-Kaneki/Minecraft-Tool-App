package com.alastorkaneki.nullforge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AssetCacheManager {
    public interface Listener {
        void onStatus(String text);
        void onProgress(int completed, int total);
    }

    public static final class Snapshot {
        public final String id;
        public final String edition;
        public final String channel;
        public final String version;
        public final String label;
        public final long createdAt;
        public final File directory;
        public final File content;

        public Snapshot(String id, String edition, String channel, String version, String label, long createdAt, File directory) {
            this.id = id;
            this.edition = edition;
            this.channel = channel;
            this.version = version;
            this.label = label;
            this.createdAt = createdAt;
            this.directory = directory;
            this.content = new File(directory, "content");
        }
    }

    private static final String BEDROCK_RELEASES = "https://api.github.com/repos/Mojang/bedrock-samples/releases?per_page=100";
    private static final String JAVA_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String JAVA_OBJECTS = "https://resources.download.minecraft.net/";

    private final File root;

    public AssetCacheManager(Context context) {
        root = new File(context.getFilesDir(), "asset_vault");
        root.mkdirs();
    }

    public List<Snapshot> listSnapshots() {
        List<Snapshot> snapshots = new ArrayList<>();
        collectSnapshots(new File(root, "bedrock/stable"), snapshots);
        collectSnapshots(new File(root, "bedrock/preview"), snapshots);
        collectSnapshots(new File(root, "java/release"), snapshots);
        collectSnapshots(new File(root, "java/snapshot"), snapshots);
        snapshots.sort(Comparator.comparingLong((Snapshot item) -> item.createdAt).reversed());
        return snapshots;
    }

    public String latestJavaVersion(boolean snapshot) throws Exception {
        JSONObject manifest = getJson(JAVA_MANIFEST);
        return manifest.getJSONObject("latest").getString(snapshot ? "snapshot" : "release");
    }

    public Snapshot cacheBedrock(boolean preview, Listener listener) throws Exception {
        listener.onStatus("Loading Mojang Bedrock releases");
        JSONArray releases = getArray(BEDROCK_RELEASES);
        JSONObject release = null;
        for (int index = 0; index < releases.length(); index++) {
            JSONObject candidate = releases.getJSONObject(index);
            if (candidate.optBoolean("draft", false)) {
                continue;
            }
            if (candidate.optBoolean("prerelease", false) == preview) {
                release = candidate;
                break;
            }
        }
        if (release == null) {
            throw new IOException("No matching Bedrock release was found.");
        }
        String tag = release.getString("tag_name");
        JSONArray assets = release.getJSONArray("assets");
        JSONObject full = null;
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.getJSONObject(index);
            String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
            if (name.endsWith(".zip") && name.contains("full")) {
                full = asset;
                break;
            }
        }
        if (full == null) {
            throw new IOException("The complete Bedrock asset archive was not found in release " + tag + ".");
        }
        String channel = preview ? "preview" : "stable";
        File destination = snapshotDirectory("bedrock", channel, tag);
        Snapshot existing = readSnapshot(destination);
        if (existing != null && existing.content.isDirectory()) {
            listener.onStatus("Bedrock " + tag + " is already cached");
            return existing;
        }
        File staging = new File(destination.getParentFile(), destination.getName() + ".staging");
        deleteRecursively(staging);
        staging.mkdirs();
        File archive = new File(staging, "bedrock-full.zip");
        listener.onStatus("Downloading complete Bedrock " + channel + " assets");
        download(full.getString("browser_download_url"), archive, listener);
        File content = new File(staging, "content");
        content.mkdirs();
        listener.onStatus("Extracting Bedrock assets");
        extractZip(archive, content, listener);
        flattenSingleRoot(content);
        archive.delete();
        writeSnapshot(staging, "bedrock", channel, tag, "Bedrock " + capitalize(channel) + " " + tag);
        replaceDirectory(staging, destination);
        listener.onStatus("Bedrock " + tag + " cached for offline use");
        return readSnapshot(destination);
    }

    public Snapshot cacheJava(String versionId, Listener listener) throws Exception {
        String requested = versionId == null ? "" : versionId.trim();
        if (requested.isEmpty()) {
            requested = latestJavaVersion(false);
        }
        listener.onStatus("Resolving Java " + requested);
        JSONObject manifest = getJson(JAVA_MANIFEST);
        JSONObject versionEntry = null;
        JSONArray versions = manifest.getJSONArray("versions");
        for (int index = 0; index < versions.length(); index++) {
            JSONObject candidate = versions.getJSONObject(index);
            if (requested.equals(candidate.optString("id"))) {
                versionEntry = candidate;
                break;
            }
        }
        if (versionEntry == null) {
            throw new IOException("Java version " + requested + " was not found in Mojang's manifest.");
        }
        String channel = "release".equals(versionEntry.optString("type")) ? "release" : "snapshot";
        File destination = snapshotDirectory("java", channel, requested);
        Snapshot existing = readSnapshot(destination);
        if (existing != null && existing.content.isDirectory()) {
            listener.onStatus("Java " + requested + " is already cached");
            return existing;
        }
        File staging = new File(destination.getParentFile(), destination.getName() + ".staging");
        deleteRecursively(staging);
        staging.mkdirs();
        File content = new File(staging, "content");
        content.mkdirs();
        JSONObject metadata = getJson(versionEntry.getString("url"));
        JSONObject client = metadata.getJSONObject("downloads").getJSONObject("client");
        File jar = new File(staging, "client.jar");
        listener.onStatus("Downloading Java " + requested + " client files");
        download(client.getString("url"), jar, listener);
        listener.onStatus("Extracting Java assets and data");
        extractJavaJar(jar, content, listener);
        jar.delete();
        JSONObject assetIndexInfo = metadata.getJSONObject("assetIndex");
        JSONObject assetIndex = getJson(assetIndexInfo.getString("url"));
        JSONObject objects = assetIndex.getJSONObject("objects");
        List<String> names = new ArrayList<>();
        objects.keys().forEachRemaining(names::add);
        listener.onStatus("Downloading " + names.size() + " Java asset objects");
        downloadJavaObjects(objects, names, content, listener);
        writeSnapshot(staging, "java", channel, requested, "Java " + capitalize(channel) + " " + requested);
        replaceDirectory(staging, destination);
        listener.onStatus("Java " + requested + " cached for offline use");
        return readSnapshot(destination);
    }

    public void deleteSnapshot(Snapshot snapshot) throws IOException {
        deleteRecursively(snapshot.directory);
    }

    private void downloadJavaObjects(JSONObject objects, List<String> names, File content, Listener listener) throws Exception {
        int workers = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();
        for (String name : names) {
            pool.submit(() -> {
                try {
                    JSONObject object = objects.getJSONObject(name);
                    String hash = object.getString("hash");
                    File target = safeChild(content, "assets/" + name);
                    File parent = target.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    if (!target.isFile() || target.length() != object.optLong("size", target.length())) {
                        download(JAVA_OBJECTS + hash.substring(0, 2) + "/" + hash, target, null);
                    }
                } catch (Throwable error) {
                    synchronized (errors) {
                        if (errors.size() < 5) {
                            errors.add(error);
                        }
                    }
                    failures.incrementAndGet();
                } finally {
                    int done = completed.incrementAndGet();
                    if (done == names.size() || done % 25 == 0) {
                        listener.onProgress(done, names.size());
                    }
                }
            });
        }
        pool.shutdown();
        if (!pool.awaitTermination(90, TimeUnit.MINUTES)) {
            pool.shutdownNow();
            throw new IOException("Java asset download timed out.");
        }
        if (failures.get() > 0) {
            Throwable first = errors.isEmpty() ? null : errors.get(0);
            throw new IOException(failures.get() + " Java assets failed to download" + (first == null ? "." : ": " + first.getMessage()));
        }
    }

    private void extractJavaJar(File archive, File destination, Listener listener) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            int count = 0;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith("assets/") && !name.startsWith("data/")) {
                    input.closeEntry();
                    continue;
                }
                File target = safeChild(destination, name);
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    File parent = target.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                        copy(input, output);
                    }
                }
                count++;
                if (count % 250 == 0) {
                    listener.onStatus("Extracted " + count + " Java files");
                }
                input.closeEntry();
            }
        }
    }

    private void extractZip(File archive, File destination, Listener listener) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            int count = 0;
            while ((entry = input.getNextEntry()) != null) {
                File target = safeChild(destination, entry.getName());
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    File parent = target.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                        copy(input, output);
                    }
                }
                count++;
                if (count % 250 == 0) {
                    listener.onStatus("Extracted " + count + " Bedrock files");
                }
                input.closeEntry();
            }
        }
    }

    private void flattenSingleRoot(File content) throws IOException {
        File[] children = content.listFiles();
        if (children == null || children.length != 1 || !children[0].isDirectory()) {
            return;
        }
        File only = children[0];
        File[] nested = only.listFiles();
        if (nested == null) {
            return;
        }
        for (File child : nested) {
            Files.move(child.toPath(), new File(content, child.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        only.delete();
    }

    private void collectSnapshots(File directory, List<Snapshot> snapshots) {
        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            Snapshot snapshot = readSnapshot(child);
            if (snapshot != null && snapshot.content.isDirectory()) {
                snapshots.add(snapshot);
            }
        }
    }

    private Snapshot readSnapshot(File directory) {
        File metadata = new File(directory, "snapshot.json");
        if (!metadata.isFile()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(readText(metadata));
            return new Snapshot(
                    json.getString("id"),
                    json.getString("edition"),
                    json.getString("channel"),
                    json.getString("version"),
                    json.getString("label"),
                    json.optLong("createdAt", metadata.lastModified()),
                    directory
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeSnapshot(File directory, String edition, String channel, String version, String label) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", edition + ":" + channel + ":" + version);
        json.put("edition", edition);
        json.put("channel", channel);
        json.put("version", version);
        json.put("label", label);
        json.put("createdAt", System.currentTimeMillis());
        writeText(new File(directory, "snapshot.json"), json.toString(2));
    }

    private File snapshotDirectory(String edition, String channel, String version) {
        File parent = new File(root, edition + "/" + channel);
        parent.mkdirs();
        return new File(parent, sanitize(version));
    }

    private JSONObject getJson(String url) throws Exception {
        return new JSONObject(getText(url));
    }

    private JSONArray getArray(String url) throws Exception {
        return new JSONArray(getText(url));
    }

    private String getText(String value) throws Exception {
        HttpURLConnection connection = open(value);
        try (InputStream input = new BufferedInputStream(connection.getInputStream()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private void download(String value, File target, Listener listener) throws Exception {
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File temporary = new File(target.getAbsolutePath() + ".part");
        temporary.delete();
        HttpURLConnection connection = open(value);
        long total = connection.getContentLengthLong();
        long done = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = new BufferedInputStream(connection.getInputStream()); BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                done += read;
                if (listener != null && total > 0) {
                    listener.onProgress((int) Math.min(Integer.MAX_VALUE, done), (int) Math.min(Integer.MAX_VALUE, total));
                }
            }
        } finally {
            connection.disconnect();
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private HttpURLConnection open(String value) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "NullForge-Studio/0.3");
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/json, */*");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("Server returned HTTP " + code + " for " + value);
        }
        return connection;
    }

    private File safeChild(File root, String relative) throws IOException {
        File target = new File(root, relative);
        String rootPath = root.getCanonicalPath() + File.separator;
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath)) {
            throw new IOException("Unsafe archive path: " + relative);
        }
        return target;
    }

    private void replaceDirectory(File source, File destination) throws IOException {
        deleteRecursively(destination);
        File parent = destination.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Could not delete " + file.getAbsolutePath());
        }
    }

    private static void copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void writeText(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isEmpty() ? "snapshot" : sanitized;
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
