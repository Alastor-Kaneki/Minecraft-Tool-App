Storage/signing refresh build notes:
- Working mirrors move to the editor's app-specific external storage under Android/data/dev.alastorkaneki.inventoryeditor/files/world-mirrors/.
- Existing legacy private mirrors are migrated on first import/open when possible.
- .mcworld exports are also published to Download/mcworlds on Android 10+ using MediaStore.
- CI uses an explicitly generated keystore at inventory-editor/.signing/debug.keystore and caches that exact file; Gradle is forced to use it for debug signing.
