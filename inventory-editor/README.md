# Bedrock Inventory Editor 0.2.0-alpha

Native Android player-inventory workshop for Minecraft Bedrock and MBLoader worlds.

## 0.2.0 workshop upgrade

This release brings the native Android editor closer to the Bedrock Workshop / Bedrock-World-Editor feature set while keeping the Shizuku mirror workflow and Android 15-safe Apply fallback.

### Player editing

- Inventory (36), Armor (4), Offhand (1), and Ender Chest (27).
- Search/filter by slot, item identifier, and displayed enchantment data.
- Native Item Studio fallback catalog including normal and technical/hidden Bedrock identifiers.
- Item ID, count 0-255, signed-short damage, and enchant editing.
- Enchants accept numeric IDs or names, with signed-short levels including illegal/custom values.
- Unknown item NBT is preserved when editing existing items and when cloning/moving/swapping raw item compounds.

### Workshop tools

- 48-step serialized-NBT Undo/Redo history.
- Duplicate to first empty slot.
- Copy, move, and swap slots.
- Quick stack counts 1 / 64 / 127 / 255.
- Bulk stack counts 64 / 127 / 255.
- A-Z section sorting.
- Clear current section with Undo support.
- Player numeric stat editor for detected Level/Health/GameMode/Dimension/Spawn/etc. tags.
- Searchable/selectable raw `~local_player` NBT view with clipboard copy.
- Original-player backup on first mutation plus manual current-state NBT snapshots.

### World access / apply

- Scans Minecraft and MBLoader protected world roots through Shizuku.
- Imports a private offline mirror before LevelDB editing.
- Saves `~local_player` edits into the mirror immediately.
- Attempts true in-place replacement only when the target package permits `run-as`.
- Otherwise exports an edited `.mcworld` and hands it to the target app so that app performs the Android-protected write itself.

This is alpha software. Keep the original world until the edited result has loaded successfully in Minecraft.
