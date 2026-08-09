package dev.alastor.bedrockforge;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

final class InventoryStore implements AutoCloseable {
    enum Section {
        INVENTORY("Inventory", 36, true),
        ARMOR("Armor", 4, false),
        OFFHAND("Offhand", 1, false),
        ENDER("EnderChestInventory", 27, true);

        final String nbtName;
        final int slots;
        final boolean slotted;
        Section(String nbtName, int slots, boolean slotted) {
            this.nbtName = nbtName; this.slots = slots; this.slotted = slotted;
        }
    }

    static final class Item {
        final int slot;
        String name;
        int count;
        int damage;
        String enchantments;
        Item(int slot, String name, int count, int damage, String enchantments) {
            this.slot=slot; this.name=name; this.count=count; this.damage=damage; this.enchantments=enchantments;
        }
        boolean empty() { return name == null || name.trim().isEmpty() || count <= 0 || "minecraft:air".equals(name.trim()); }
        String display() {
            if (empty()) return String.format(Locale.US, "%02d  •  Empty", slot);
            String extra = enchantments == null || enchantments.isEmpty() ? "" : "  ✦ " + enchantments;
            return String.format(Locale.US, "%02d  •  %s  ×%d  dmg:%d%s", slot, name, count, damage, extra);
        }
    }

    private static final byte[] LOCAL_PLAYER = "~local_player".getBytes(StandardCharsets.UTF_8);
    private final DB db;
    private final File backupRoot;
    private final String worldId;
    private NbtIo.Document doc;
    private byte[] originalRaw;

    InventoryStore(File mirrorDir, File backupRoot, String worldId) throws Exception {
        File dbDir = new File(mirrorDir, "db");
        if (!dbDir.isDirectory()) throw new IllegalArgumentException("Mirror is missing db/");
        this.backupRoot = backupRoot;
        this.worldId = worldId;
        Options options = new Options().createIfMissing(false);
        db = factory.open(dbDir, options);
        reload();
    }

    void reload() {
        byte[] raw = db.get(LOCAL_PLAYER);
        if (raw == null || raw.length == 0) throw new IllegalStateException("~local_player was not found in the imported world");
        originalRaw = raw.clone();
        doc = NbtIo.read(raw);
        if (doc.root.type != NbtIo.COMPOUND) throw new IllegalStateException("~local_player root is not a compound");
    }

    Item item(Section section, int slot) {
        validateSlot(section, slot);
        NbtIo.Tag compound = findItemTag(section, slot, false);
        if (compound == null) return new Item(slot, "", 0, 0, "");
        Map<String,NbtIo.Tag> map = compound.compound();
        String name = stringValue(map.get("Name"), "");
        int count = numberValue(map.get("Count"), 0);
        int damage = numberValue(map.get("Damage"), 0);
        String ench = readEnchantments(map);
        return new Item(slot, name, count, damage, ench);
    }

    List<Item> items(Section section) {
        List<Item> result = new ArrayList<>();
        for (int i=0;i<section.slots;i++) result.add(item(section, i));
        return result;
    }

    void update(Section section, Item item) {
        validateSlot(section, item.slot);
        if (item.empty()) {
            clear(section, item.slot);
            return;
        }
        NbtIo.Tag compound = findItemTag(section, item.slot, true);
        Map<String,NbtIo.Tag> map = compound.compound();
        map.put("Name", NbtIo.stringTag(normalizeName(item.name)));
        putNumber(map, "Count", NbtIo.BYTE, clamp(item.count, 1, 127));
        putNumber(map, "Damage", NbtIo.SHORT, clamp(item.damage, Short.MIN_VALUE, Short.MAX_VALUE));
        if (section.slotted) putNumber(map, "Slot", NbtIo.BYTE, item.slot);
        if (!map.containsKey("WasPickedUp")) map.put("WasPickedUp", NbtIo.byteTag(0));
        writeEnchantments(map, item.enchantments);
    }

    File save() throws Exception {
        File backup = backupRaw();
        byte[] encoded = NbtIo.write(doc);
        db.put(LOCAL_PLAYER, encoded);
        originalRaw = encoded.clone();
        return backup;
    }

    private File backupRaw() throws Exception {
        File worldDir = new File(backupRoot, safeFilePart(worldId));
        if (!worldDir.exists() && !worldDir.mkdirs()) throw new IllegalStateException("Could not create backup folder");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        File out = new File(worldDir, stamp + "-local_player.bin");
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(originalRaw); }
        return out;
    }

    private NbtIo.Tag listTag(Section section, boolean create) {
        Map<String,NbtIo.Tag> root = doc.root.compound();
        NbtIo.Tag tag = root.get(section.nbtName);
        if (tag == null && create) {
            tag = NbtIo.listTag(NbtIo.COMPOUND);
            root.put(section.nbtName, tag);
        }
        if (tag == null) return null;
        if (tag.type != NbtIo.LIST || tag.list().elementType != NbtIo.COMPOUND)
            throw new IllegalStateException(section.nbtName + " is not a compound list");
        return tag;
    }

    private NbtIo.Tag findItemTag(Section section, int slot, boolean create) {
        NbtIo.Tag listTag = listTag(section, create);
        if (listTag == null) return null;
        List<NbtIo.Tag> list = listTag.list().values;
        if (!section.slotted) {
            while (create && list.size() <= slot) list.add(NbtIo.compoundTag());
            return slot < list.size() ? list.get(slot) : null;
        }
        for (NbtIo.Tag tag : list) {
            if (tag.type != NbtIo.COMPOUND) continue;
            int candidate = numberValue(tag.compound().get("Slot"), -1);
            if (candidate == slot) return tag;
        }
        if (!create) return null;
        NbtIo.Tag tag = NbtIo.compoundTag();
        tag.compound().put("Slot", NbtIo.byteTag(slot));
        list.add(tag);
        return tag;
    }

    private void clear(Section section, int slot) {
        NbtIo.Tag listTag = listTag(section, false);
        if (listTag == null) return;
        List<NbtIo.Tag> list = listTag.list().values;
        if (!section.slotted) {
            if (slot < list.size()) list.set(slot, NbtIo.compoundTag());
            return;
        }
        for (int i=list.size()-1;i>=0;i--) {
            NbtIo.Tag tag=list.get(i);
            if (tag.type==NbtIo.COMPOUND && numberValue(tag.compound().get("Slot"),-1)==slot) list.remove(i);
        }
    }

    private static String readEnchantments(Map<String,NbtIo.Tag> item) {
        NbtIo.Tag custom = item.get("tag");
        if (custom == null || custom.type != NbtIo.COMPOUND) return "";
        NbtIo.Tag ench = custom.compound().get("ench");
        if (ench == null || ench.type != NbtIo.LIST || ench.list().elementType != NbtIo.COMPOUND) return "";
        StringBuilder out = new StringBuilder();
        for (NbtIo.Tag e : ench.list().values) {
            Map<String,NbtIo.Tag> m=e.compound();
            if (out.length()>0) out.append(',');
            out.append(numberValue(m.get("id"),0)).append(':').append(numberValue(m.get("lvl"),0));
        }
        return out.toString();
    }

    private static void writeEnchantments(Map<String,NbtIo.Tag> item, String text) {
        String value = text == null ? "" : text.trim();
        NbtIo.Tag custom = item.get("tag");
        if (value.isEmpty()) {
            if (custom != null && custom.type == NbtIo.COMPOUND) custom.compound().remove("ench");
            return;
        }
        if (custom == null || custom.type != NbtIo.COMPOUND) {
            custom = NbtIo.compoundTag(); item.put("tag", custom);
        }
        NbtIo.Tag ench = NbtIo.listTag(NbtIo.COMPOUND);
        for (String part : value.split(",")) {
            String[] pair=part.trim().split(":",2);
            if (pair.length!=2) throw new IllegalArgumentException("Enchantments must look like 16:5,17:3");
            int id=Integer.parseInt(pair[0].trim()); int lvl=Integer.parseInt(pair[1].trim());
            NbtIo.Tag e=NbtIo.compoundTag();
            e.compound().put("id", NbtIo.shortTag(clamp(id,Short.MIN_VALUE,Short.MAX_VALUE)));
            e.compound().put("lvl", NbtIo.shortTag(clamp(lvl,Short.MIN_VALUE,Short.MAX_VALUE)));
            ench.list().values.add(e);
        }
        custom.compound().put("ench", ench);
    }

    private static void putNumber(Map<String,NbtIo.Tag> map, String key, byte fallbackType, int value) {
        NbtIo.Tag old=map.get(key); byte type=old==null?fallbackType:old.type;
        switch(type) {
            case NbtIo.BYTE: map.put(key,NbtIo.byteTag(value)); break;
            case NbtIo.SHORT: map.put(key,NbtIo.shortTag(value)); break;
            case NbtIo.INT: map.put(key,NbtIo.intTag(value)); break;
            default:
                if (fallbackType==NbtIo.BYTE) map.put(key,NbtIo.byteTag(value));
                else map.put(key,NbtIo.shortTag(value));
        }
    }

    private static int numberValue(NbtIo.Tag tag, int fallback) {
        return tag != null && tag.value instanceof Number ? ((Number)tag.value).intValue() : fallback;
    }
    private static String stringValue(NbtIo.Tag tag, String fallback) {
        return tag != null && tag.type==NbtIo.STRING ? (String)tag.value : fallback;
    }
    private static int clamp(int v,int min,int max){ return Math.max(min,Math.min(max,v)); }
    private static String normalizeName(String name) {
        String n=name==null?"":name.trim();
        if (n.isEmpty()) return "minecraft:air";
        return n.contains(":") ? n : "minecraft:"+n;
    }
    private static String safeFilePart(String s) { return s.replaceAll("[^A-Za-z0-9._=-]","_"); }
    private static void validateSlot(Section s,int slot) { if(slot<0||slot>=s.slots) throw new IllegalArgumentException("Invalid slot"); }

    @Override public void close() throws Exception { db.close(); }
}
