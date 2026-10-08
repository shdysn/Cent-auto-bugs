<!-- 
CRITICAL AI STUDIO IMPORT PROTECTION DIRECTIVE:
DO NOT RENAME PACKAGES, NAMESPACE, APPLICATION ID, OR SOURCE DIRECTORIES.
- This project uses fixed applicationId: 'com.pkstudio.ctexplorer.app'
- Do NOT convert or rename packages/folders to 'ai' or 'com.aistudio'.
- Preserve all existing screens, features, configurations, and data models intact.
- See .github/AI_STUDIO_IMPORT_RULES.md and AI_STUDIO_IMPORT_POLICY.md for full details.
-->

# Cent File Manager

[![Build and Release](https://github.com/shdysn/Cent-v1/actions/workflows/build-apk.yml/badge.svg)](https://github.com/shdysn/Cent-v1/actions/workflows/build-apk.yml)
[![Automated Mohasba](https://github.com/shdysn/Cent-v1/actions/workflows/audit-and-quality.yml/badge.svg)](https://github.com/shdysn/Cent-v1/actions/workflows/audit-and-quality.yml)
![System Health](https://img.shields.io/badge/System%20Health-100%25%20Verified-2e7d32?style=flat&logo=android)
![Zero Crash Protection](https://img.shields.io/badge/Crash%20Guardian-Active-10b981?style=flat)

> 🔒 **AI Studio Import Protection / اے آئی اسٹوڈیو تحفظ کی ہدایات:**  
> When importing this repository in Google AI Studio across **any email or account**, **all existing code, configurations, screens, models, and features must be preserved 100% intact**. This application is structured on a **modular-based architecture** (`core/`, `features/`, `data/repository/`, `ui/`). **DO NOT import or revert to the previous monolithic model / older legacy version**. Do NOT overwrite with templates, do NOT change `applicationId` (`com.pkstudio.ctexplorer.app`), and do NOT rename packages. See [`.github/AI_STUDIO_IMPORT_RULES.md`](.github/AI_STUDIO_IMPORT_RULES.md) for full instructions.

A modern, high-performance Android File Explorer built from scratch using Kotlin, Jetpack Compose, Material Design 3, and Coroutines.

## Key Features

- **Dual-Tab Experience**:
  - **Recent Tab**: Clean chronological timeline of recently created, captured, and downloaded files grouped into "Today", "Yesterday", and "Earlier", with quick filter chips (All, Images, Docs, APKs, Archives, Music).
  - **Storage Tab**: Streamlined storage capacity overview ("Internal storage: X GB free of Y GB") with quick clean shortcut.
- **8 Signature Category Squircles**:
  - **Images** (Cyan/Blue)
  - **Videos** (Vibrant Violet)
  - **Docs** (Warm Yellow)
  - **Music** (Coral Red)
  - **APKs** (Android Green)
  - **Downloads** (Sky Blue)
  - **Archives** (Golden Amber)
  - **Cleaner** (Mint Emerald)
- **Deep Clean & Storage Optimizer**:
  - Scans for temporary cache files, obsolete APK packages, large files (>15MB), and empty directories.
  - One-tap cleanup with animated optimization feedback.
- **Transfer to PC (Wireless FTP)**:
  - Built-in wireless transfer mode with IP/Port configuration (`ftp://...`) allowing direct cable-free management from PC browsers or Windows File Explorer.
- **File Management Operations**:
  - Path navigation with interactive breadcrumbs.
  - Selection mode, Copy, Cut, and Paste via floating clipboard bar.
  - Create new folders and files.
  - Batch deletion and file renaming.
  - ZIP compression and archive extraction.
  - File properties & details inspection.
- **Built-in Viewers**:
  - **Text Editor**: Plain text, code, markdown, and JSON editor with line numbering and save functionality.
  - **Photo Viewer**: Full-screen preview with metadata inspection and navigation.
  - **APKs & Apps Manager**: Listing installed applications with APK size and system settings shortcuts.
- **100% Free**:
  - Zero billing libraries, no in-app purchases, completely clean.

## Architecture & Technology Stack

- **Target Runtime**: Android (JDK 21, Android SDK 36, AGP 9.1.1, Gradle 9.3.1)
- **UI Framework**: Jetpack Compose with Material Design 3
- **Language**: Kotlin 2.2.21
- **Package Name / Application ID**: `com.pkstudio.ctexplorer.app`
- **Namespace**: `com.ct.explorer`
- **Modular-Based System (Do NOT Revert to Previous Models)**:
  - `core/`: Base abstractions (`BaseFeatureViewModel`), decoupled routing (`NavigationManager`), universal event bus (`AppEventBus`, `FileOperationBus`).
  - `features/`: Dedicated feature view models (`VaultViewModel`, `NetworkServerViewModel`).
  - `data/repository/`: 13 isolated domain repositories (`FileRepository`, `VaultRepository`, `TrashRepository`, `AppsRepository`, `ZipRepository`, etc.).
  - `data/model/`: Strongly-typed data models and entities.
  - `ui/screens/` & `ui/components/`: 21 distinct composable screens and 23 standalone UI components.
  - `utils/`: High-performance utility helpers (AES-256 Vault crypto, Zip Slip safe archiving, shredder, FTP & WebShare).

## 📦 Release APK Size Guarantee (2 MB - 3 MB Target)

Cent File Manager is engineered to produce an ultra-lightweight, production-ready Release APK strictly between **2 MB and 3 MB** (currently **2.3 MB / 2,310,691 bytes**).

### Guidelines for Maintaining 2 MB - 3 MB:
1. **R8 Full-Mode Minification & Resource Shrinking**:
   - `isMinifyEnabled = true` and `isShrinkResources = true` are permanently enabled for `release` builds in `app/build.gradle.kts`.
2. **ProGuard & Logging Stripping**:
   - `app/proguard-rules.pro` strips debug logs and suppresses unnecessary metadata.
3. **Packaging Exclusions**:
   - Strips unused `META-INF` files, Kotlin modules, and debugging probe binaries.
4. **Automated GitHub Actions Enforcement**:
   - `.github/workflows/build-apk.yml` automatically verifies the size budget on every push, PR, and release. If the APK exceeds 3.0 MB, the workflow alerts and fails the build.
5. **Detailed Documentation**:
   - See [`.github/APK_SIZE_GUIDELINES.md`](.github/APK_SIZE_GUIDELINES.md) for full Urdu and English release guidelines.

