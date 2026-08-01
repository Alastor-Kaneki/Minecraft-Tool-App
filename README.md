# NullForge Studio

NullForge Studio is a native Android-first Minecraft creator workspace.

The interface uses an original AMOLED OreUI-inspired layout with red, purple, black, and split-light accents based on Alastor Kaneki. The application does not use WebViews for its tweak selectors.

## Native Tweaks Library

The app includes three independent native provider tabs:

### Vanilla Tweaks

- Reads the official `VanillaTweaks/packs` GitHub repository.
- Lists the data-pack source folders that are publicly available there.
- Exports selected source bundles with the required upstream-build warning.
- Keeps Vanilla Tweaks credits and links separate from the other providers.

The public repository states that it contains source for some packs and uses Python, Poetry, and Beet for builds. It is not a complete mirror of the Vanilla Tweaks website catalog.

### Bedrock Tweaks

- Reads structured catalogs from `BedrockTweaks/Files` on the `devel` branch.
- Supports Resource Packs, Addons, and Crafting Tweaks.
- Reads categories, names, descriptions, and conflict metadata natively.
- Resolves selected source folders through the GitHub tree API.
- Assembles selected files on-device into `.mcpack` or `.mcaddon` output.

### BEComTweaks

- Reads the separate `resource-packs`, `behaviour-packs`, and `crafting-tweaks` repositories.
- Loads each repository's native JSON pack metadata.
- Supports search, categories, selection persistence, and conflict warnings.
- Assembles selected source files on-device.
- Preserves separate BEComTweaks and original-project attribution.

## Additional workspace features

- Bedrock and Java project creation.
- Fresh Bedrock manifest UUID generation.
- Java `pack.mcmeta` generation.
- Local app-private project storage.
- Official Mojang Bedrock sample and Java asset-source destinations.
- Android Storage Access Framework exports.
- Immersive AMOLED interface.
- Animated rainbow card outlines.

## Build

The project uses Java 17, Android Gradle Plugin 8.10.1, Gradle 8.11.1, compile SDK 35, target SDK 35, and minimum SDK 26.

```bash
gradle :app:assembleDebug
```

GitHub Actions builds the debug APK and publishes it as the `NullForge-Studio-debug` workflow artifact.

## Upstream projects

- Vanilla Tweaks: `https://vanillatweaks.net`
- Vanilla Tweaks source: `https://github.com/VanillaTweaks/packs`
- Bedrock Tweaks: `https://www.bedrocktweaks.net`
- Bedrock Tweaks GitHub: `https://github.com/BedrockTweaks`
- BEComTweaks: `https://becomtweaks.github.io`
- BEComTweaks GitHub: `https://github.com/BEComTweaks`
- Mojang Bedrock samples: `https://github.com/Mojang/bedrock-samples`
- Java version manifest: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`

## Attribution

NullForge Studio is unofficial and is not affiliated with Mojang, Microsoft, Vanilla Tweaks, Bedrock Tweaks, or BEComTweaks.

Minecraft belongs to Mojang Studios and Microsoft. Every upstream project and individual pack creator retains ownership of their work. NullForge Studio does not remove, replace, or claim upstream attribution.
