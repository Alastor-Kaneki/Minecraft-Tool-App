package com.alastorkaneki.nullforge;

import java.util.ArrayList;
import java.util.List;

public final class TemplateCatalog {
    public record Template(String name, String path, String content) {
    }

    private TemplateCatalog() {
    }

    public static List<Template> forProject(Project project) {
        List<Template> templates = new ArrayList<>();
        if (project.edition == Project.Edition.BEDROCK) {
            templates.add(new Template("Custom Block", bedrockRoot(project, true) + "blocks/custom_block.json", bedrockBlock()));
            templates.add(new Template("Custom Item", bedrockRoot(project, false) + "items/custom_item.json", bedrockItem()));
            templates.add(new Template("Entity Behavior", bedrockRoot(project, false) + "entities/custom_entity.json", bedrockEntityBehavior()));
            templates.add(new Template("Client Entity", bedrockRoot(project, true) + "entity/custom_entity.entity.json", bedrockClientEntity()));
            templates.add(new Template("Shaped Recipe", bedrockRoot(project, false) + "recipes/custom_recipe.json", bedrockRecipe()));
            templates.add(new Template("Loot Table", bedrockRoot(project, false) + "loot_tables/custom_loot.json", bedrockLoot()));
            templates.add(new Template("Function", bedrockRoot(project, false) + "functions/main.mcfunction", "say NullForge function loaded\n"));
            templates.add(new Template("Animation", bedrockRoot(project, true) + "animations/custom.animation.json", bedrockAnimation()));
            templates.add(new Template("Animation Controller", bedrockRoot(project, true) + "animation_controllers/custom.controller.json", bedrockAnimationController()));
            templates.add(new Template("Particle", bedrockRoot(project, true) + "particles/custom.particle.json", bedrockParticle()));
            templates.add(new Template("Render Controller", bedrockRoot(project, true) + "render_controllers/custom.render_controllers.json", bedrockRenderController()));
            templates.add(new Template("UI Screen", bedrockRoot(project, true) + "ui/custom_screen.json", bedrockUi()));
            templates.add(new Template("Script Entry", bedrockRoot(project, false) + "scripts/main.js", "import { world } from '@minecraft/server';\n\nworld.afterEvents.worldLoad.subscribe(() => {\n    console.warn('NullForge script loaded');\n});\n"));
        } else {
            templates.add(new Template("Block Model", "assets/minecraft/models/block/custom_block.json", javaBlockModel()));
            templates.add(new Template("Item Model", "assets/minecraft/models/item/custom_item.json", javaItemModel()));
            templates.add(new Template("Blockstate", "assets/minecraft/blockstates/custom_block.json", javaBlockstate()));
            templates.add(new Template("Language File", "assets/minecraft/lang/en_us.json", "{\n  \"block.minecraft.custom_block\": \"Custom Block\"\n}\n"));
            templates.add(new Template("Recipe", "data/minecraft/recipe/custom_recipe.json", javaRecipe()));
            templates.add(new Template("Advancement", "data/minecraft/advancement/custom_advancement.json", javaAdvancement()));
            templates.add(new Template("Loot Table", "data/minecraft/loot_table/blocks/custom_block.json", javaLoot()));
            templates.add(new Template("Function", "data/minecraft/function/main.mcfunction", "say NullForge function loaded\n"));
            templates.add(new Template("Item Tag", "data/minecraft/tags/item/custom_tag.json", "{\n  \"replace\": false,\n  \"values\": []\n}\n"));
            templates.add(new Template("Predicate", "data/minecraft/predicate/custom_predicate.json", "{\n  \"condition\": \"minecraft:entity_properties\",\n  \"entity\": \"this\",\n  \"predicate\": {}\n}\n"));
        }
        return templates;
    }

    private static String bedrockRoot(Project project, boolean resources) {
        if (project.kind == Project.Kind.BEDROCK_ADDON) {
            return resources ? "resource_pack/" : "behavior_pack/";
        }
        return "";
    }

    private static String bedrockBlock() {
        return "{\n  \"format_version\": \"1.21.0\",\n  \"minecraft:block\": {\n    \"description\": {\n      \"identifier\": \"nullforge:custom_block\",\n      \"menu_category\": { \"category\": \"construction\" }\n    },\n    \"components\": {\n      \"minecraft:destroy_time\": 1.0,\n      \"minecraft:explosion_resistance\": 6.0,\n      \"minecraft:geometry\": \"geometry.full_block\",\n      \"minecraft:material_instances\": {\n        \"*\": { \"texture\": \"custom_block\", \"render_method\": \"opaque\" }\n      }\n    }\n  }\n}\n";
    }

    private static String bedrockItem() {
        return "{\n  \"format_version\": \"1.21.0\",\n  \"minecraft:item\": {\n    \"description\": {\n      \"identifier\": \"nullforge:custom_item\",\n      \"menu_category\": { \"category\": \"items\" }\n    },\n    \"components\": {\n      \"minecraft:icon\": \"custom_item\",\n      \"minecraft:max_stack_size\": 64\n    }\n  }\n}\n";
    }

    private static String bedrockEntityBehavior() {
        return "{\n  \"format_version\": \"1.21.0\",\n  \"minecraft:entity\": {\n    \"description\": {\n      \"identifier\": \"nullforge:custom_entity\",\n      \"is_spawnable\": true,\n      \"is_summonable\": true,\n      \"is_experimental\": false\n    },\n    \"components\": {\n      \"minecraft:health\": { \"value\": 20, \"max\": 20 },\n      \"minecraft:movement\": { \"value\": 0.25 },\n      \"minecraft:movement.basic\": {},\n      \"minecraft:navigation.walk\": {},\n      \"minecraft:physics\": {},\n      \"minecraft:pushable\": {}\n    }\n  }\n}\n";
    }

    private static String bedrockClientEntity() {
        return "{\n  \"format_version\": \"1.10.0\",\n  \"minecraft:client_entity\": {\n    \"description\": {\n      \"identifier\": \"nullforge:custom_entity\",\n      \"materials\": { \"default\": \"entity_alphatest\" },\n      \"textures\": { \"default\": \"textures/entity/custom_entity\" },\n      \"geometry\": { \"default\": \"geometry.custom_entity\" },\n      \"render_controllers\": [\"controller.render.custom_entity\"]\n    }\n  }\n}\n";
    }

    private static String bedrockRecipe() {
        return "{\n  \"format_version\": \"1.21.0\",\n  \"minecraft:recipe_shaped\": {\n    \"description\": { \"identifier\": \"nullforge:custom_recipe\" },\n    \"tags\": [\"crafting_table\"],\n    \"pattern\": [\"AAA\", \"ABA\", \"AAA\"],\n    \"key\": {\n      \"A\": { \"item\": \"minecraft:stone\" },\n      \"B\": { \"item\": \"minecraft:diamond\" }\n    },\n    \"result\": { \"item\": \"nullforge:custom_item\", \"count\": 1 }\n  }\n}\n";
    }

    private static String bedrockLoot() {
        return "{\n  \"pools\": [\n    {\n      \"rolls\": 1,\n      \"entries\": [\n        { \"type\": \"item\", \"name\": \"nullforge:custom_item\", \"weight\": 1 }\n      ]\n    }\n  ]\n}\n";
    }

    private static String bedrockAnimation() {
        return "{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n    \"animation.custom.idle\": {\n      \"loop\": true,\n      \"animation_length\": 1.0,\n      \"bones\": {}\n    }\n  }\n}\n";
    }

    private static String bedrockAnimationController() {
        return "{\n  \"format_version\": \"1.10.0\",\n  \"animation_controllers\": {\n    \"controller.animation.custom\": {\n      \"initial_state\": \"default\",\n      \"states\": {\n        \"default\": { \"animations\": [\"idle\"] }\n      }\n    }\n  }\n}\n";
    }

    private static String bedrockParticle() {
        return "{\n  \"format_version\": \"1.10.0\",\n  \"particle_effect\": {\n    \"description\": {\n      \"identifier\": \"nullforge:custom_particle\",\n      \"basic_render_parameters\": {\n        \"material\": \"particles_alpha\",\n        \"texture\": \"textures/particle/particles\"\n      }\n    },\n    \"components\": {\n      \"minecraft:emitter_rate_instant\": { \"num_particles\": 1 },\n      \"minecraft:particle_lifetime_expression\": { \"max_lifetime\": 1.0 },\n      \"minecraft:particle_appearance_billboard\": {\n        \"size\": [0.2, 0.2],\n        \"facing_camera_mode\": \"lookat_xyz\",\n        \"uv\": { \"texture_width\": 128, \"texture_height\": 128, \"uv\": [0, 0], \"uv_size\": [8, 8] }\n      }\n    }\n  }\n}\n";
    }

    private static String bedrockRenderController() {
        return "{\n  \"format_version\": \"1.8.0\",\n  \"render_controllers\": {\n    \"controller.render.custom_entity\": {\n      \"geometry\": \"Geometry.default\",\n      \"materials\": [{ \"*\": \"Material.default\" }],\n      \"textures\": [\"Texture.default\"]\n    }\n  }\n}\n";
    }

    private static String bedrockUi() {
        return "{\n  \"namespace\": \"nullforge\",\n  \"custom_screen\": {\n    \"type\": \"panel\",\n    \"size\": [\"100%\", \"100%\"],\n    \"controls\": []\n  }\n}\n";
    }

    private static String javaBlockModel() {
        return "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": { \"all\": \"minecraft:block/custom_block\" }\n}\n";
    }

    private static String javaItemModel() {
        return "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": { \"layer0\": \"minecraft:item/custom_item\" }\n}\n";
    }

    private static String javaBlockstate() {
        return "{\n  \"variants\": {\n    \"\": { \"model\": \"minecraft:block/custom_block\" }\n  }\n}\n";
    }

    private static String javaRecipe() {
        return "{\n  \"type\": \"minecraft:crafting_shaped\",\n  \"category\": \"misc\",\n  \"pattern\": [\"AAA\", \"ABA\", \"AAA\"],\n  \"key\": {\n    \"A\": \"minecraft:stone\",\n    \"B\": \"minecraft:diamond\"\n  },\n  \"result\": { \"id\": \"minecraft:diamond\", \"count\": 1 }\n}\n";
    }

    private static String javaAdvancement() {
        return "{\n  \"criteria\": {\n    \"tick\": { \"trigger\": \"minecraft:tick\" }\n  },\n  \"rewards\": { \"function\": \"minecraft:main\" }\n}\n";
    }

    private static String javaLoot() {
        return "{\n  \"type\": \"minecraft:block\",\n  \"pools\": [\n    {\n      \"rolls\": 1,\n      \"entries\": [{ \"type\": \"minecraft:item\", \"name\": \"minecraft:stone\" }]\n    }\n  ]\n}\n";
    }
}
