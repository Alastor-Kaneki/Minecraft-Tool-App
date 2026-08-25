package dev.alastorkaneki.inventoryeditor;

public enum WorldSource {
    MINECRAFT(
            "minecraft",
            "Minecraft",
            "com.mojang.minecraftpe",
            "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/"
    ),
    MBLOADER(
            "mbloader",
            "MBLoader",
            "io.github.bambosan.mbloader",
            "/storage/emulated/0/Android/data/io.github.bambosan.mbloader/files/games/com.mojang/minecraftWorlds/"
    );

    public final String key;
    public final String label;
    public final String packageName;
    public final String worldRoot;

    WorldSource(String key, String label, String packageName, String worldRoot) {
        this.key = key;
        this.label = label;
        this.packageName = packageName;
        this.worldRoot = trimTrailingSlash(worldRoot);
    }

    private static String trimTrailingSlash(String s) {
        while (s.endsWith("/") && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s;
    }
}
