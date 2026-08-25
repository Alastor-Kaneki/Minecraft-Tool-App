package dev.alastorkaneki.inventoryeditor;

import android.content.pm.PackageManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;

public final class ShizukuShell {
    private ShizukuShell() {}

    public static boolean binderAlive() {
        try { return Shizuku.pingBinder(); }
        catch (Throwable ignored) { return false; }
    }

    public static boolean permissionGranted() {
        try {
            return binderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) { return false; }
    }

    public static Process start(String command) throws Exception {
        Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        return (Process) m.invoke(null, new String[]{"sh", "-c", command}, null, null);
    }

    public static String exec(String command) throws Exception {
        Process p = start(command);
        byte[] stdout;
        byte[] stderr;
        try (InputStream out = p.getInputStream(); InputStream err = p.getErrorStream()) {
            stdout = readAll(out);
            stderr = readAll(err);
        }
        int code = p.waitFor();
        if (code != 0) {
            String msg = new String(stderr, StandardCharsets.UTF_8).trim();
            throw new IllegalStateException("shell exited " + code + (msg.isEmpty() ? "" : ": " + msg));
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    public static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
