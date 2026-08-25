package dev.alastorkaneki.inventoryeditor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        String fileName = safe + "_edited_" + System.currentTimeMillis() + ".mcworld";
        File out = new File(exports, fileName);

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            zipTree(WorldManager.mirrorDir(activity, world), "", zip);
        }

        if (!out.isFile() || out.length() == 0) {
            throw new IOException("Edited .mcworld export failed");
        }

        publishToDownloads(activity, out, fileName);
        return out;
    }

    private static void publishToDownloads(Activity activity, File source, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = activity.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/mcworlds");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create Download/mcworlds export");
            boolean ok = false;
            try (InputStream in = new BufferedInputStream(new FileInputStream(source));
                 OutputStream out = new BufferedOutputStream(resolver.openOutputStream(uri, "w"))) {
                if (out == null) throw new IOException("Could not open Download/mcworlds export");
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                out.flush();
                ok = true;
            } finally {
                if (ok) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(uri, ready, null, null);
                } else {
                    resolver.delete(uri, null, null);
                }
            }
            return;
        }

        // Legacy Android fallback. Modern Android uses MediaStore above and needs no broad storage permission.
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mcworlds");
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("Could not create Download/mcworlds");
        File dest = new File(dir, fileName);
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
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
