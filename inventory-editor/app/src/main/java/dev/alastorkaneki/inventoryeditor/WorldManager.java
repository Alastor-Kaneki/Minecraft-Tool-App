package dev.alastorkaneki.inventoryeditor;

import android.content.Context;
import android.util.Base64;

import java.io.File;
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

    /**
     * Mirrors live in internal app storage so the Java LevelDB implementation can open them.
     * Protected Android/data files are streamed through Shizuku and extracted by run-as,
     * which gives the mirror the editor app's UID instead of the shell UID.
     */
    public static File mirrorDir(Context context, WorldRef world) {
        File base = new File(context.getFilesDir(), "world-mirrors/" + world.source.key);
        return new File(base, encode(world.folder));
    }

    public static boolean validMirror(Context context, WorldRef world) {
        File dir = mirrorDir(context, world);
        return new File(dir, "level.dat").isFile() && new File(dir, "db").isDirectory();
    }

    public static File importWorld(Context context, WorldRef world) throws Exception {
        if (isSourceRunning(world.source)) {
            throw new IllegalStateException("Close " + world.source.label + " completely before importing this world");
        }

        File mirror = mirrorDir(context, world);
        String src = world.source.worldRoot + "/" + world.folder;
        String pkg = context.getPackageName();
        String dst = mirror.getAbsolutePath();

        String runAsExtract = "rm -rf " + ShizukuShell.quote(dst) +
                "; mkdir -p " + ShizukuShell.quote(dst) +
                " && tar -xf - -C " + ShizukuShell.quote(dst);

        String cmd = "src=" + ShizukuShell.quote(src) + "; " +
                "[ -f \"$src/level.dat\" ] && [ -d \"$src/db\" ] || exit 31; " +
                "tar -C \"$src\" -cf - . | run-as " + ShizukuShell.quote(pkg) +
                " sh -c " + ShizukuShell.quote(runAsExtract);

        ShizukuShell.exec(cmd);
        if (!validMirror(context, world)) {
            throw new IllegalStateException("Imported mirror is incomplete after run-as transfer");
        }
        return mirror;
    }

    public static File syncWorld(Context context, WorldRef world) throws Exception {
        if (!validMirror(context, world)) throw new IllegalStateException("Import/open the world mirror first");
        if (isSourceRunning(world.source)) {
            throw new IllegalStateException("Close " + world.source.label + " completely before syncing");
        }

        File mirror = mirrorDir(context, world);
        File mirrorDb = new File(mirror, "db");
        File backup = backupDir(context, world);
        File backupParent = backup.getParentFile();
        if (backupParent != null && !backupParent.mkdirs() && !backupParent.isDirectory()) {
            throw new IllegalStateException("Could not create backup parent directory");
        }

        String pkg = context.getPackageName();
        String destWorld = world.source.worldRoot + "/" + world.folder;
        String destDb = destWorld + "/db";
        String tempDb = destWorld + "/db.__bie_new";
        String oldDb = destWorld + "/db.__bie_old";

        // 1) Back up the destination db into app-private storage via Shizuku -> run-as.
        String backupExtract = "rm -rf " + ShizukuShell.quote(backup.getAbsolutePath()) +
                "; mkdir -p " + ShizukuShell.quote(backup.getAbsolutePath()) +
                " && tar -xf - -C " + ShizukuShell.quote(backup.getAbsolutePath());
        String backupCmd = "world=" + ShizukuShell.quote(destWorld) + "; " +
                "[ -d \"$world/db\" ] || exit 41; " +
                "tar -C \"$world\" -cf - db | run-as " + ShizukuShell.quote(pkg) +
                " sh -c " + ShizukuShell.quote(backupExtract);
        ShizukuShell.exec(backupCmd);
        if (!new File(backup, "db").isDirectory()) {
            throw new IllegalStateException("Destination backup failed; sync was not attempted");
        }

        // 2) Stream the app-private mirror db out through run-as into a shell-owned staging db.
        String runAsPack = "cd " + ShizukuShell.quote(mirrorDb.getAbsolutePath()) + " && tar -cf - .";
        String stageCmd = "tmp=" + ShizukuShell.quote(tempDb) + "; " +
                "rm -rf \"$tmp\"; mkdir -p \"$tmp\" || exit 42; " +
                "run-as " + ShizukuShell.quote(pkg) + " sh -c " + ShizukuShell.quote(runAsPack) +
                " | tar -xf - -C \"$tmp\"; " +
                "[ -f \"$tmp/CURRENT\" ] || { rm -rf \"$tmp\"; exit 43; }";
        ShizukuShell.exec(stageCmd);

        // 3) Atomic-ish directory swap. The old db is retained until the new one is in place.
        String swapCmd = "dst=" + ShizukuShell.quote(destDb) +
                "; tmp=" + ShizukuShell.quote(tempDb) +
                "; old=" + ShizukuShell.quote(oldDb) + "; " +
                "[ -d \"$dst\" ] && [ -f \"$tmp/CURRENT\" ] || exit 44; " +
                "rm -rf \"$old\"; " +
                "mv \"$dst\" \"$old\" || exit 45; " +
                "if mv \"$tmp\" \"$dst\"; then rm -rf \"$old\"; " +
                "else mv \"$old\" \"$dst\"; rm -rf \"$tmp\"; exit 46; fi";
        ShizukuShell.exec(swapCmd);

        return backup;
    }

    private static File backupDir(Context context, WorldRef world) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File root = new File(context.getFilesDir(), "sync-backups/" + world.source.key + "/" + encode(world.folder));
        return new File(root, stamp);
    }

    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
