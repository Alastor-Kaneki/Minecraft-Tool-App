# NullForge Studio

NullForge Studio is an Android-first Minecraft pack workspace built specifically for creating, inspecting, assembling, validating, and exporting Minecraft content.

The interface is inspired by Minecraft's modern OreUI visual language, rebuilt as an original red, purple, black, and split-light theme based on Alastor Kaneki. It does not bundle or redistribute Mojang asset archives inside the APK. Official assets are fetched directly to the user's device and cached for offline use.

## Current features

### Separate edition workspaces

- Bedrock Edition projects and caches are isolated from Java Edition projects and caches.
- Bedrock project types: resource pack, behavior pack, paired add-on, skin pack, and world template.
- Java project types: resource pack, data pack, and combined resource/data workspace.

### Complete official asset vault

- Bedrock uses the official `Mojang/bedrock-samples` GitHub releases API.
- Stable and Preview channels are selectable independently.
- The app locates the complete release asset dynamically instead of hardcoding a release URL.
- The archive is cached and safely extracted into a versioned offline vault.
- Every file and folder in the extracted release can be browsed, previewed, selected, and copied into a project.
- Java uses Mojang's official version manifest, client metadata, client JAR, asset index, and asset object service.
- Java client `assets/` and `data/` trees are extracted, and every object referenced by the official asset index is downloaded into its correct path.

### Pack workspace

- Full project file browser.
- JSON, MCMeta, Lang, JavaScript, TypeScript, shader, and function text editor.
- PNG, JPEG, and WebP preview.
- File import, rename, deletion, and project-relative path handling.
- Templates for blocks, items, entities, recipes, loot tables, functions, animations, animation controllers, particles, render controllers, UI files, scripts, Java models, blockstates, advancements, tags, predicates, and more.
- Bedrock manifest generation with fresh UUIDs.
- Paired add-on dependency generation.
- Java `pack.mcmeta` generation with configurable pack format.
- JSON and manifest validation.
- Export to `.mcpack`, `.mcaddon`, or `.zip` through Android's Storage Access Framework.

### Offline behavior

- Each downloaded version is stored as its own snapshot.
- Previously cached snapshots remain selectable without a network connection.
- Bedrock Stable, Bedrock Preview, and Java caches remain independent.

## Build

The project uses Java 17, Android Gradle Plugin 8.10.1, Gradle 8.11.1, compile SDK 35, target SDK 35, and minimum SDK 26.

```bash
gradle :app:assembleDebug
```

GitHub Actions builds the debug APK and publishes it as the `NullForge-Studio-debug` workflow artifact.

## Asset sources

- Bedrock samples and vanilla asset releases: `https://github.com/Mojang/bedrock-samples`
- Minecraft Creator documentation: `https://learn.microsoft.com/minecraft/creator/`
- Java version manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
- Java asset objects: `https://resources.download.minecraft.net/`

## Legal

NullForge Studio is an independent creator tool and is not an official Minecraft product. It is not approved by or associated with Mojang or Microsoft. Minecraft assets remain subject to Mojang and Microsoft terms. The application downloads official assets directly to the user's device and does not commit those assets to this repository.
