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

    public static File mirrorDir(Context context, WorldRef world) {
        File base = new File(context.getExternalFilesDir(null), "world-mirrors/" + world.source.key);
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
        File parent = mirror.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IllegalStateException("Could not create mirror folder");
        }
        String src = world.source.worldRoot + "/" + world.folder;
        String cmd = "src=" + ShizukuShell.quote(src) + "; dst=" + ShizukuShell.quote(mirror.getAbsolutePath()) + "; " +
                "[ -f \"$src/level.dat\" ] && [ -d \"$src/db\" ] || exit 31; " +
                "rm -rf \"$dst\"; mkdir -p \"${dst%/*}\"; cp -R \"$src\" \"$dst\"";
        ShizukuShell.exec(cmd);
        if (!validMirror(context, world)) throw new IllegalStateException("Imported mirror is incomplete");
        return mirror;
    }

    public static File syncWorld(Context context, WorldRef world) throws Exception {
        if (!validMirror(context, world)) throw new IllegalStateException("Import/open the world mirror first");
        if (isSourceRunning(world.source)) {
            throw new IllegalStateException("Close " + world.source.label + " completely before syncing");
        }
        File mirror = mirrorDir(context, world);
        File backup = backupDir(context, world);
        File backupDb = new File(backup, "db");
        File mirrorDb = new File(mirror, "db");
        String destWorld = world.source.worldRoot + "/" + world.folder;
        String destDb = destWorld + "/db";
        String tempDb = destWorld + "/db.__bie_new";
        String oldDb = destWorld + "/db.__bie_old";

        String cmd = "src=" + ShizukuShell.quote(mirrorDb.getAbsolutePath()) + "; " +
                "dst=" + ShizukuShell.quote(destDb) + "; tmp=" + ShizukuShell.quote(tempDb) + "; old=" + ShizukuShell.quote(oldDb) + "; " +
                "bak=" + ShizukuShell.quote(backupDb.getAbsolutePath()) + "; " +
                "[ -d \"$src\" ] && [ -d \"$dst\" ] || exit 41; " +
                "rm -rf \"$bak\" \"$tmp\" \"$old\"; mkdir -p \"${bak%/*}\"; " +
                "cp -R \"$dst\" \"$bak\" || exit 42; " +
                "cp -R \"$src\" \"$tmp\" || exit 43; " +
                "mv \"$dst\" \"$old\" || exit 44; " +
                "if mv \"$tmp\" \"$dst\"; then rm -rf \"$old\"; else mv \"$old\" \"$dst\"; exit 45; fi";
        ShizukuShell.exec(cmd);
        return backup;
    }

    private static File backupDir(Context context, WorldRef world) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File root = new File(context.getExternalFilesDir(null), "sync-backups/" + world.source.key + "/" + encode(world.folder));
        return new File(root, stamp);
    }

    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
