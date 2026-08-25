package dev.alastorkaneki.inventoryeditor.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact native fallback catalog. Custom/unknown identifiers are always accepted by the editor. */
public final class ItemCatalog {
    private ItemCatalog() {}

    private static final String[] ITEMS={
            "minecraft:air","minecraft:stone","minecraft:granite","minecraft:diorite","minecraft:andesite","minecraft:deepslate","minecraft:cobbled_deepslate","minecraft:cobblestone","minecraft:bedrock","minecraft:dirt","minecraft:grass_block","minecraft:podzol","minecraft:mycelium","minecraft:sand","minecraft:red_sand","minecraft:gravel","minecraft:clay","minecraft:snow","minecraft:ice","minecraft:packed_ice","minecraft:blue_ice",
            "minecraft:oak_log","minecraft:spruce_log","minecraft:birch_log","minecraft:jungle_log","minecraft:acacia_log","minecraft:dark_oak_log","minecraft:mangrove_log","minecraft:cherry_log","minecraft:crimson_stem","minecraft:warped_stem","minecraft:oak_planks","minecraft:spruce_planks","minecraft:birch_planks","minecraft:jungle_planks","minecraft:acacia_planks","minecraft:dark_oak_planks","minecraft:mangrove_planks","minecraft:cherry_planks",
            "minecraft:glass","minecraft:tinted_glass","minecraft:obsidian","minecraft:crying_obsidian","minecraft:netherrack","minecraft:soul_sand","minecraft:soul_soil","minecraft:glowstone","minecraft:blackstone","minecraft:end_stone","minecraft:purpur_block","minecraft:prismarine","minecraft:sea_lantern","minecraft:sculk","minecraft:sculk_sensor","minecraft:sculk_catalyst","minecraft:sculk_shrieker",
            "minecraft:coal","minecraft:charcoal","minecraft:iron_ingot","minecraft:gold_ingot","minecraft:copper_ingot","minecraft:diamond","minecraft:emerald","minecraft:netherite_ingot","minecraft:netherite_scrap","minecraft:lapis_lazuli","minecraft:redstone","minecraft:quartz","minecraft:amethyst_shard","minecraft:echo_shard","minecraft:blaze_rod","minecraft:ender_pearl","minecraft:ender_eye","minecraft:ghast_tear","minecraft:nether_star","minecraft:heart_of_the_sea","minecraft:nautilus_shell",
            "minecraft:wooden_sword","minecraft:stone_sword","minecraft:iron_sword","minecraft:golden_sword","minecraft:diamond_sword","minecraft:netherite_sword","minecraft:wooden_pickaxe","minecraft:stone_pickaxe","minecraft:iron_pickaxe","minecraft:golden_pickaxe","minecraft:diamond_pickaxe","minecraft:netherite_pickaxe","minecraft:wooden_axe","minecraft:stone_axe","minecraft:iron_axe","minecraft:golden_axe","minecraft:diamond_axe","minecraft:netherite_axe","minecraft:wooden_shovel","minecraft:stone_shovel","minecraft:iron_shovel","minecraft:golden_shovel","minecraft:diamond_shovel","minecraft:netherite_shovel","minecraft:wooden_hoe","minecraft:stone_hoe","minecraft:iron_hoe","minecraft:golden_hoe","minecraft:diamond_hoe","minecraft:netherite_hoe",
            "minecraft:bow","minecraft:crossbow","minecraft:trident","minecraft:mace","minecraft:shield","minecraft:fishing_rod","minecraft:flint_and_steel","minecraft:shears","minecraft:brush","minecraft:compass","minecraft:recovery_compass","minecraft:clock","minecraft:spyglass","minecraft:elytra",
            "minecraft:leather_helmet","minecraft:leather_chestplate","minecraft:leather_leggings","minecraft:leather_boots","minecraft:chainmail_helmet","minecraft:chainmail_chestplate","minecraft:chainmail_leggings","minecraft:chainmail_boots","minecraft:iron_helmet","minecraft:iron_chestplate","minecraft:iron_leggings","minecraft:iron_boots","minecraft:golden_helmet","minecraft:golden_chestplate","minecraft:golden_leggings","minecraft:golden_boots","minecraft:diamond_helmet","minecraft:diamond_chestplate","minecraft:diamond_leggings","minecraft:diamond_boots","minecraft:netherite_helmet","minecraft:netherite_chestplate","minecraft:netherite_leggings","minecraft:netherite_boots","minecraft:turtle_helmet","minecraft:wolf_armor",
            "minecraft:apple","minecraft:golden_apple","minecraft:enchanted_golden_apple","minecraft:bread","minecraft:cooked_beef","minecraft:cooked_porkchop","minecraft:cooked_chicken","minecraft:cooked_mutton","minecraft:cooked_rabbit","minecraft:carrot","minecraft:golden_carrot","minecraft:potato","minecraft:baked_potato","minecraft:beetroot","minecraft:melon_slice","minecraft:sweet_berries","minecraft:glow_berries","minecraft:chorus_fruit","minecraft:honey_bottle","minecraft:mushroom_stew","minecraft:rabbit_stew","minecraft:suspicious_stew",
            "minecraft:totem_of_undying","minecraft:experience_bottle","minecraft:enchanted_book","minecraft:book","minecraft:writable_book","minecraft:written_book","minecraft:map","minecraft:empty_map","minecraft:name_tag","minecraft:saddle","minecraft:lead","minecraft:firework_rocket","minecraft:firework_star","minecraft:goat_horn","minecraft:bundle",
            "minecraft:chest","minecraft:trapped_chest","minecraft:barrel","minecraft:ender_chest","minecraft:shulker_box","minecraft:hopper","minecraft:dispenser","minecraft:dropper","minecraft:furnace","minecraft:blast_furnace","minecraft:smoker","minecraft:brewing_stand","minecraft:crafter","minecraft:crafting_table","minecraft:anvil","minecraft:enchanting_table","minecraft:smithing_table","minecraft:stonecutter","minecraft:cartography_table","minecraft:loom","minecraft:grindstone",
            "minecraft:redstone_torch","minecraft:repeater","minecraft:comparator","minecraft:piston","minecraft:sticky_piston","minecraft:observer","minecraft:target","minecraft:lever","minecraft:tripwire_hook","minecraft:daylight_detector","minecraft:lightning_rod","minecraft:tnt","minecraft:rail","minecraft:powered_rail","minecraft:detector_rail","minecraft:activator_rail","minecraft:minecart","minecraft:chest_minecart","minecraft:hopper_minecart","minecraft:tnt_minecart","minecraft:command_block_minecart",
            "minecraft:spawn_egg","minecraft:armor_stand","minecraft:painting","minecraft:item_frame","minecraft:glow_item_frame","minecraft:boat","minecraft:oak_boat","minecraft:spruce_boat","minecraft:birch_boat","minecraft:jungle_boat","minecraft:acacia_boat","minecraft:dark_oak_boat","minecraft:mangrove_boat","minecraft:cherry_boat",
            "minecraft:command_block","minecraft:chain_command_block","minecraft:repeating_command_block","minecraft:structure_block","minecraft:structure_void","minecraft:jigsaw","minecraft:barrier","minecraft:light_block","minecraft:border_block","minecraft:allow","minecraft:deny","minecraft:camera","minecraft:info_update","minecraft:info_update2","minecraft:reserved6","minecraft:moving_block","minecraft:piston_arm_collision","minecraft:sticky_piston_arm_collision","minecraft:end_gateway","minecraft:end_portal","minecraft:portal","minecraft:water","minecraft:flowing_water","minecraft:lava","minecraft:flowing_lava","minecraft:bubble_column","minecraft:fire","minecraft:soul_fire","minecraft:unknown"
    };

    private static final LinkedHashMap<Integer,String> ENCHANTMENTS=new LinkedHashMap<>();
    static{
        put(0,"protection");put(1,"fire_protection");put(2,"feather_falling");put(3,"blast_protection");put(4,"projectile_protection");put(5,"thorns");put(6,"respiration");put(7,"depth_strider");put(8,"aqua_affinity");
        put(9,"sharpness");put(10,"smite");put(11,"bane_of_arthropods");put(12,"knockback");put(13,"fire_aspect");put(14,"looting");put(15,"efficiency");put(16,"silk_touch");put(17,"unbreaking");put(18,"fortune");
        put(19,"power");put(20,"punch");put(21,"flame");put(22,"infinity");put(23,"luck_of_the_sea");put(24,"lure");put(25,"frost_walker");put(26,"mending");put(27,"binding");put(28,"vanishing");
        put(29,"impaling");put(30,"riptide");put(31,"loyalty");put(32,"channeling");put(33,"multishot");put(34,"piercing");put(35,"quick_charge");put(36,"soul_speed");put(37,"swift_sneak");put(38,"wind_burst");put(39,"density");put(40,"breach");
    }
    private static void put(int id,String name){ENCHANTMENTS.put(id,name);}

    public static List<String> search(String query,int limit){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> out=new ArrayList<>();
        for(String id:ITEMS){if(q.isEmpty()||id.toLowerCase(Locale.ROOT).contains(q)){out.add(id);if(out.size()>=limit)break;}}
        return out;
    }

    public static int enchantId(String token){
        String s=token.trim().toLowerCase(Locale.ROOT).replace("minecraft:","");
        try{return Integer.parseInt(s);}catch(NumberFormatException ignored){}
        for(Map.Entry<Integer,String> e:ENCHANTMENTS.entrySet())if(e.getValue().equals(s))return e.getKey();
        throw new IllegalArgumentException("Unknown enchant name/id: "+token);
    }

    public static String enchantName(int id){String s=ENCHANTMENTS.get(id);return s==null?Integer.toString(id):s;}
    public static String enchantLegend(){
        StringBuilder b=new StringBuilder();
        for(Map.Entry<Integer,String> e:ENCHANTMENTS.entrySet()){if(b.length()>0)b.append(" · ");b.append(e.getKey()).append('=').append(e.getValue());}
        return b.toString();
    }
}
