package dev.alastorkaneki.inventoryeditor;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.alastorkaneki.inventoryeditor.WorldManager.WorldRef;

public final class WorldExportImporter {
    private WorldExportImporter() {}

    public static File exportMirror(Activity activity, WorldRef world) throws Exception {
        if (!WorldManager.validMirror(activity, world)) {
            throw new IllegalStateException("Import/open the world mirror first");
        }

        File exports = new File(activity.getFilesDir(), "exports");
        if (!exports.mkdirs() && !exports.isDirectory()) {
            throw new IOException("Could not create export directory");
        }

        String safe = world.displayName.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safe.isEmpty()) safe = "Edited_World";
        File out = new File(exports, safe + "_edited_" + System.currentTimeMillis() + ".mcworld");

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            zipTree(WorldManager.mirrorDir(activity, world), "", zip);
        }

        if (!out.isFile() || out.length() == 0) {
            throw new IOException("Edited .mcworld export failed");
        }
        return out;
    }

    private static void zipTree(File file, String relative, ZipOutputStream zip) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            if (!relative.isEmpty()) {
                ZipEntry dir = new ZipEntry(relative.endsWith("/") ? relative : relative + "/");
                zip.putNextEntry(dir);
                zip.closeEntry();
            }
            for (File child : children) {
                String next = relative.isEmpty() ? child.getName() : relative + "/" + child.getName();
                zipTree(child, next, zip);
            }
            return;
        }

        ZipEntry entry = new ZipEntry(relative);
        entry.setTime(file.lastModified());
        zip.putNextEntry(entry);
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) zip.write(buffer, 0, n);
        }
        zip.closeEntry();
    }

    public static void openInTarget(Activity activity, WorldRef world, File mcworld) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".files",
                mcworld
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setPackage(world.source.packageName);
        intent.setDataAndType(uri, "application/zip");
        intent.setClipData(ClipData.newRawUri("edited-world", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            intent.setDataAndType(uri, "application/octet-stream");
        }
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            intent.setDataAndType(uri, "*/*");
        }
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            throw new IllegalStateException(world.source.label + " has no compatible .mcworld import handler");
        }

        activity.startActivity(intent);
    }
}
