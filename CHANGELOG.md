# Changelog

## 0.4.0

- Rebuilt the Tweaks Library to avoid oversized view trees and callbacks touching closed activities.
- Added paged catalog rendering with 32 packs per batch and safe load-more controls.
- Added safe-mode recovery instead of allowing Tweaks rendering failures to terminate the app.
- Standardized the launcher, Tweaks Library, Bedrock and Java workspaces, Asset Vault, Asset Browser, recovery screens, inputs, tabs, cards, footers, status panels, and dialogs around one OreUI-inspired component system.
- Replaced mixed styling with compact dark panels, square-rounded controls, strong focus states, and red-purple accents.
- Removed the animated border renderer and every deliberate UI animation.
- Replaced indeterminate loading spinners with static status bars.
- Disabled activity and dialog window transitions.

## 0.3.1

- Reworked launcher startup so feature activity classes are loaded only when opened.
- Deferred immersive-mode changes until the window view is attached.
- Added a recovery launcher instead of allowing startup rendering failures to terminate the app.

## 0.3.0

- Replaced the link-only Asset Vault with direct official asset downloading.
- Added separate versioned offline caches for Bedrock stable, Bedrock preview, Java releases, and Java snapshots.
- Added full Bedrock release archive extraction from `Mojang/bedrock-samples`.
- Added Java client JAR extraction plus complete asset-index object downloading.
- Added recursive file and folder browsing, search, image previews, and text previews.
- Added file-level, folder-level, and entire-tree selection.
- Added edition-aware copying into existing Bedrock and Java projects with collision reporting.
- Added cache deletion and automatic reuse of existing snapshots.

## 0.2.0

- Added the native Tweaks Library.
- Added Vanilla Tweaks, Bedrock Tweaks, and BEComTweaks provider tabs.
- Added separate provider and project credits.
- Added native GitHub catalog loading and six-hour local caching.
- Added search, categories, independent selections, and conflict warnings.
- Added on-device `.zip`, `.mcpack`, and `.mcaddon` assembly.
- Restored the missing launcher, project creation, and asset source activities.
- Removed the obsolete source hydration workflow.
