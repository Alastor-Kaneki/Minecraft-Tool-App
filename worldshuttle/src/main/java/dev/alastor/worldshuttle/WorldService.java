package dev.alastor.worldshuttle;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class WorldService extends Binder {
    public static final String DESCRIPTOR = "dev.alastor.worldshuttle.IWorldService";
    public static final int TRANSACTION_MOVE_TO_EDITOR = FIRST_CALL_TRANSACTION;
    public static final int TRANSACTION_MOVE_TO_MINECRAFT = FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_DESTROY = 16777115;

    public static final String MINECRAFT_WORLDS = "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds";
    public static final String EDITOR_WORLDS = "/storage/emulated/0/Android/data/dev.astler.inveditormc/files/minecraftWorlds";

    public WorldService() { }
    public WorldService(Context context) { }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == TRANSACTION_DESTROY) {
            if (reply != null) reply.writeNoException();
            new Thread(() -> {
                try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                System.exit(0);
            }).start();
            return true;
        }

        if (code == TRANSACTION_MOVE_TO_EDITOR || code == TRANSACTION_MOVE_TO_MINECRAFT) {
            data.enforceInterface(DESCRIPTOR);
            try {
                forceStopMinecraft();
                String result = code == TRANSACTION_MOVE_TO_EDITOR
                        ? moveWorlds(new File(MINECRAFT_WORLDS), new File(EDITOR_WORLDS), "Inventory Editor")
                        : moveWorlds(new File(EDITOR_WORLDS), new File(MINECRAFT_WORLDS), "Minecraft");
                reply.writeNoException();
                reply.writeString(result);
            } catch (Throwable t) {
                reply.writeNoException();
                String message = t.getMessage();
                if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName();
                reply.writeString("ERROR: " + message);
            }
            return true;
        }

        return super.onTransact(code, data, reply, flags);
    }

    private static void forceStopMinecraft() {
        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", "am force-stop com.mojang.minecraftpe")
                    .redirectErrorStream(true).start();
            process.waitFor();
        } catch (Throwable ignored) {
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String moveWorlds(File sourceRoot, File destinationRoot, String destinationName) throws IOException {
        if (!sourceRoot.isDirectory()) {
            return "Source folder not found: " + sourceRoot.getAbsolutePath();
        }
        File[] children = sourceRoot.listFiles();
        if (children == null) throw new IOException("Cannot list source folder: " + sourceRoot);

        List<File> worlds = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".worldshuttle_")) worlds.add(child);
        }
        if (worlds.isEmpty()) return "No world folders found in source.";

        if (!destinationRoot.exists() && !destinationRoot.mkdirs()) {
            throw new IOException("Cannot create destination: " + destinationRoot);
        }

        int moved = 0;
        for (File world : worlds) {
            moveOneTransactional(world, new File(destinationRoot, world.getName()));
            moved++;
        }
        return "Moved " + moved + " world" + (moved == 1 ? "" : "s") + " to " + destinationName + ".";
    }

    private static void moveOneTransactional(File source, File destination) throws IOException {
        String suffix = ".worldshuttle_" + System.currentTimeMillis();
        File backup = new File(destination.getParentFile(), destination.getName() + suffix + ".backup");
        File staging = new File(destination.getParentFile(), destination.getName() + suffix + ".staging");
        boolean hadDestination = destination.exists();

        try {
            if (hadDestination) {
                if (!destination.renameTo(backup)) {
                    copyRecursive(destination, backup);
                    if (!sameTree(destination, backup)) {
                        throw new IOException("Could not verify destination backup for " + destination.getName());
                    }
                    deleteRecursive(destination);
                }
            }

            if (source.renameTo(destination)) {
                if (backup.exists()) deleteRecursive(backup);
                return;
            }

            if (staging.exists()) deleteRecursive(staging);
            copyRecursive(source, staging);
            if (!sameTree(source, staging)) {
                throw new IOException("Verification failed while staging " + source.getName());
            }

            if (!staging.renameTo(destination)) {
                copyRecursive(staging, destination);
                if (!sameTree(staging, destination)) {
                    throw new IOException("Verification failed at destination for " + source.getName());
                }
                deleteRecursive(staging);
            }

            if (!sameTree(source, destination)) {
                throw new IOException("Final verification failed for " + source.getName());
            }
            deleteRecursive(source);
            if (backup.exists()) deleteRecursive(backup);
        } catch (IOException | RuntimeException e) {
            try {
                if (destination.exists() && hadDestination) deleteRecursive(destination);
                if (backup.exists() && !backup.renameTo(destination)) {
                    copyRecursive(backup, destination);
                }
                if (staging.exists()) deleteRecursive(staging);
            } catch (Throwable ignored) { }
            throw e;
        }
    }

    private static void copyRecursive(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("mkdir failed: " + destination);
            }
            File[] files = source.listFiles();
            if (files == null) throw new IOException("list failed: " + source);
            for (File child : files) copyRecursive(child, new File(destination, child.getName()));
            return;
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdir failed: " + parent);
        }

        byte[] buffer = new byte[1024 * 1024];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.getFD().sync();
        }
        destination.setLastModified(source.lastModified());
    }

    private static boolean sameTree(File a, File b) {
        if (a.isDirectory() != b.isDirectory()) return false;
        if (a.isFile()) return a.length() == b.length();
        File[] aa = a.listFiles();
        File[] bb = b.listFiles();
        if (aa == null || bb == null || aa.length != bb.length) return false;
        for (File child : aa) {
            if (!sameTree(child, new File(b, child.getName()))) return false;
        }
        return true;
    }

    private static void deleteRecursive(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list for delete: " + file);
            for (File child : children) deleteRecursive(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("Could not delete: " + file);
    }
}
