package dev.alastor.bedrockforge;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;

final class ShizukuBridge {
    private ShizukuBridge() {}

    static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable ignored) { return false; }
    }

    static boolean granted() {
        try { return running() && Shizuku.checkSelfPermission() == 0; } catch (Throwable ignored) { return false; }
    }

    static void requestPermission(int requestCode) {
        Shizuku.requestPermission(requestCode);
    }

    static String exec(String shellCommand) throws Exception {
        if (!running()) throw new IllegalStateException("Shizuku is not running");
        if (!granted()) throw new SecurityException("Bedrock Forge does not have Shizuku permission");

        Process process = newShizukuProcess(new String[]{"/system/bin/sh", "-c", shellCommand + " 2>&1"});
        String output;
        try (InputStream in = process.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            output = out.toString(StandardCharsets.UTF_8.name());
        }
        int code = process.waitFor();
        if (code != 0) throw new IllegalStateException("shell exit " + code + ": " + output.trim());
        return output;
    }

    private static Process newShizukuProcess(String[] command) throws Exception {
        Throwable last = null;
        for (Method method : Shizuku.class.getDeclaredMethods()) {
            if (!method.getName().equals("newProcess") || method.getParameterTypes().length != 3) continue;
            try {
                method.setAccessible(true);
                Object result = method.invoke(null, command, null, null);
                if (result instanceof Process) return (Process) result;
            } catch (Throwable t) {
                last = t;
            }
        }
        throw new IllegalStateException("Direct Shizuku process bridge unavailable", last);
    }

    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
