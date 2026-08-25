package dev.alastorkaneki.inventoryeditor;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WorldManager {
    private WorldManager() {}

    public static final class WorldRef {
        public final WorldSource source;
        public final String folder;
        public final String displayName;

        public WorldRef(WorldSource source, String folder, String displayName) {
            this.source = source;
            this.folder = folder;
            this.displayName = displayName == null || displayName.trim().isEmpty() ? folder : displayName.trim();
        }

        @Override public String toString() { return displayName + " · " + source.label; }
    }

    public static List<WorldRef> list(WorldSource source) throws Exception {
        String root = source.worldRoot;
        String script = "root=" + ShizukuShell.quote(root) + "; " +
                "[ -d \"$root\" ] || { printf 'BIE_MISSING\\n'; exit 0; }; " +
                "printf 'BIE_ROOT_OK\\n'; " +
                "for d in \"$root\"/*; do " +
                "[ -d \"$d\" ] || continue; " +
                "[ -f \"$d/level.dat\" ] || continue; " +
                "[ -d \"$d/db\" ] || continue; " +
                "f=${d##*/}; " +
                "n=$f; [ -f \"$d/levelname.txt\" ] && n=$(head -c 512 \"$d/levelname.txt\" | tr '\\r\\n\\t' '   '); " +
                "printf 'BIE_WORLD:%s\\t%s\\n' \"$f\" \"$n\"; " +
                "done";
        String out = ShizukuShell.exec(script);
        if (out.contains("BIE_MISSING")) throw new IllegalStateException(source.label + " world folder does not exist");
        ArrayList<WorldRef> result = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (!line.startsWith("BIE_WORLD:")) continue;
            String body = line.substring("BIE_WORLD:".length());
            int tab = body.indexOf('\t');
            String folder = tab >= 0 ? body.substring(0, tab) : body;
            String name = tab >= 0 ? body.substring(tab + 1) : folder;
            if (!folder.isEmpty() && !folder.contains("/") && !folder.equals(".") && !folder.equals("..")) {
                result.add(new WorldRef(source, folder, name));
            }
        }
        return result;
    }

    public static boolean isSourceRunning(WorldSource source) throws Exception {
        String out = ShizukuShell.exec("pidof " + ShizukuShell.quote(source.packageName) + " 2>/dev/null || true");
        return !out.trim().isEmpty();
    }

    public static boolean canTargetRunAs(WorldSource source) {
        try {
            String probe = "[ -d " + ShizukuShell.quote(source.worldRoot) + " ] && printf BIE_RUNAS_OK";
            String out = ShizukuShell.exec(
                    "run-as " + ShizukuShell.quote(source.packageName) +
                            " sh -c " + ShizukuShell.quote(probe) + " 2>/dev/null || true"
            );
            return out.contains("BIE_RUNAS_OK");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** User-visible app-specific external storage: /storage/emulated/0/Android/data/<package>/files */
    public static File visibleRoot(Context context) {
        File external = context.getExternalFilesDir(null);
        return external != null ? external : context.getFilesDir();
    }

    public static File mirrorDir(Context context, WorldRef world) {
        File base = new File(visibleRoot(context), "world-mirrors/" + world.source.key);
        return new File(base, encode(world.folder));
    }

    private static File legacyMirrorDir(Context context, WorldRef world) {
        File base = new File(context.getFilesDir(), "world-mirrors/" + world.source.key);
        return new File(base, encode(world.folder));
    }

    private static boolean validMirrorDir(File dir) {
        return new File(dir, "level.dat").isFile() && new File(dir, "db").isDirectory();
    }

    public static boolean validMirror(Context context, WorldRef world) {
        return validMirrorDir(mirrorDir(context, world));
    }

    public static File importWorld(Context context, WorldRef world) throws Exception {
        File mirror = mirrorDir(context, world);

        // Preserve an edited mirror from older builds before touching the source world.
        if (!validMirrorDir(mirror)) {
            File legacy = legacyMirrorDir(context, world);
            if (validMirrorDir(legacy)) {
                deleteTree(mirror);
                copyTree(legacy, mirror);
                if (!validMirrorDir(mirror)) {
                    deleteTree(mirror);
                    throw new IllegalStateException("Legacy mirror migration into Android/data was incomplete");
                }
                deleteTree(legacy);
                return mirror;
            }
        }

        if (isSourceRunning(world.source)) {
            throw new IllegalStateException("Close " + world.source.label + " completely before importing this world");
        }

        String src = world.source.worldRoot + "/" + world.folder;
        String pkg = context.getPackageName();

        // Keep the proven Shizuku -> run-as bridge on private storage, then let the app itself
        // copy the finished mirror into its Android/data/files tree. This avoids shell ownership
        // and Android 15 FUSE/SELinux surprises while making the actual working mirror visible.
        File staging = new File(context.getFilesDir(), "mirror-import-staging/" + world.source.key + "/" + encode(world.folder));
        String stage = staging.getAbsolutePath();
        String runAsExtract = "rm -rf " + ShizukuShell.quote(stage) +
                "; mkdir -p " + ShizukuShell.quote(stage) +
                " && tar -xf - -C " + ShizukuShell.quote(stage);

        String cmd = "src=" + ShizukuShell.quote(src) + "; " +
                "[ -f \"$src/level.dat\" ] && [ -d \"$src/db\" ] || exit 31; " +
                "tar -C \"$src\" -cf - . | run-as " + ShizukuShell.quote(pkg) +
                " sh -c " + ShizukuShell.quote(runAsExtract);

        ShizukuShell.exec(cmd);
        if (!validMirrorDir(staging)) {
            throw new IllegalStateException("Imported staging mirror is incomplete after run-as transfer");
        }

        deleteTree(mirror);
        copyTree(staging, mirror);
        deleteTree(staging);

        if (!validMirrorDir(mirror)) {
            throw new IllegalStateException("Android/data mirror is incomplete after app-owned copy");
        }
        return mirror;
    }

    /**
     * True same-folder replacement. This only works when the target package itself permits
     * run-as, because Android 15 blocks shell UID writes into another app's Android/data tree.
     */
    public static File syncWorldInPlace(Context context, WorldRef world) throws Exception {
        if (!validMirror(context, world)) throw new IllegalStateException("Import/open the world mirror first");
        if (!canTargetRunAs(world.source)) {
            throw new IllegalStateException("Target package does not permit run-as; use edited .mcworld import instead");
        }
        if (isSourceRunning(world.source)) {
            throw new IllegalStateException("Close " + world.source.label + " completely before in-place sync");
        }

        File mirrorDb = new File(mirrorDir(context, world), "db");
        File backup = backupDir(context, world);
        File backupParent = backup.getParentFile();
        if (backupParent != null && !backupParent.mkdirs() && !backupParent.isDirectory()) {
            throw new IllegalStateException("Could not create backup parent directory");
        }

        String selfPkg = context.getPackageName();
        String targetPkg = world.source.packageName;
        String destWorld = world.source.worldRoot + "/" + world.folder;
        String destDb = destWorld + "/db";
        String tempDb = destWorld + "/db.__bie_new";
        String oldDb = destWorld + "/db.__bie_old";

        File privateBackupStage = new File(context.getFilesDir(), "sync-backup-staging/" + world.source.key + "/" + encode(world.folder));
        deleteTree(privateBackupStage);

        String targetBackupPack = "tar -C " + ShizukuShell.quote(destWorld) + " -cf - db";
        String selfBackupExtract = "rm -rf " + ShizukuShell.quote(privateBackupStage.getAbsolutePath()) +
                "; mkdir -p " + ShizukuShell.quote(privateBackupStage.getAbsolutePath()) +
                " && tar -xf - -C " + ShizukuShell.quote(privateBackupStage.getAbsolutePath());
        String backupCmd =
                "run-as " + ShizukuShell.quote(targetPkg) + " sh -c " + ShizukuShell.quote(targetBackupPack) +
                " | run-as " + ShizukuShell.quote(selfPkg) + " sh -c " + ShizukuShell.quote(selfBackupExtract);
        ShizukuShell.exec(backupCmd);
        if (!new File(privateBackupStage, "db").isDirectory()) {
            throw new IllegalStateException("Destination backup failed; sync was not attempted");
        }
        deleteTree(backup);
        copyTree(privateBackupStage, backup);
        deleteTree(privateBackupStage);

        // run-as may not reliably traverse app-specific external storage on every Android build,
        // so stage the edited DB privately before streaming it to the target package.
        File privateDbStage = new File(context.getFilesDir(), "sync-db-staging/" + world.source.key + "/" + encode(world.folder));
        deleteTree(privateDbStage);
        copyTree(mirrorDb, privateDbStage);

        String selfPack = "cd " + ShizukuShell.quote(privateDbStage.getAbsolutePath()) + " && tar -cf - .";
        String targetExtract = "rm -rf " + ShizukuShell.quote(tempDb) +
                "; mkdir -p " + ShizukuShell.quote(tempDb) +
                " && tar -xf - -C " + ShizukuShell.quote(tempDb) +
                " && [ -f " + ShizukuShell.quote(tempDb + "/CURRENT") + " ]";
        String stageCmd =
                "run-as " + ShizukuShell.quote(selfPkg) + " sh -c " + ShizukuShell.quote(selfPack) +
                " | run-as " + ShizukuShell.quote(targetPkg) + " sh -c " + ShizukuShell.quote(targetExtract);
        ShizukuShell.exec(stageCmd);
        deleteTree(privateDbStage);

        String swap = "dst=" + ShizukuShell.quote(destDb) +
                "; tmp=" + ShizukuShell.quote(tempDb) +
                "; old=" + ShizukuShell.quote(oldDb) + "; " +
                "[ -d \"$dst\" ] && [ -f \"$tmp/CURRENT\" ] || exit 44; " +
                "rm -rf \"$old\"; mv \"$dst\" \"$old\" || exit 45; " +
                "if mv \"$tmp\" \"$dst\"; then rm -rf \"$old\"; " +
                "else mv \"$old\" \"$dst\"; rm -rf \"$tmp\"; exit 46; fi";
        ShizukuShell.exec(
                "run-as " + ShizukuShell.quote(targetPkg) + " sh -c " + ShizukuShell.quote(swap)
        );

        return backup;
    }

    private static File backupDir(Context context, WorldRef world) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File root = new File(visibleRoot(context), "backups/sync/" + world.source.key + "/" + encode(world.folder));
        return new File(root, stamp);
    }

    private static void copyTree(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Could not create " + dst.getAbsolutePath());
            File[] children = src.listFiles();
            if (children == null) return;
            for (File child : children) copyTree(child, new File(dst, child.getName()));
            return;
        }
        File parent = dst.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("Could not create " + parent.getAbsolutePath());
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        }
        dst.setLastModified(src.lastModified());
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        try { file.delete(); } catch (Throwable ignored) {}
    }

    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
