# 🧩 Cent File Manager - Modular Architecture & Import Guidelines
# 🧩 سینٹ فائل مینیجر - ماڈیولر آرکیٹیکچر اور امپورٹ کے قوانین

> **MODULAR ARCHITECTURE SPECIFICATION:**  
> This application is built as a **fully modularized, clean architecture Android application**.  
> When importing this repository into **Google AI Studio**:  
> 1. **DO NOT REVERT TO THE PREVIOUS MONOLITHIC MODEL.**  
> 2. **DO NOT ROLL BACK TO AN OLDER UNMODULARIZED OR BOILERPLATE CODEBASE.**  
> 3. **PRESERVE AND ADVANCE THE MODULAR ARCHITECTURE DETAILED BELOW.**

---

## 🇵🇰 اردو میں لازمی ہدایات (Mandatory Urdu Instructions)

جب بھی یہ پراجیکٹ AI Studio میں امپورٹ کیا جائے:
1. **پچھلے پرانے ماڈل پر واپس نہ جائیں (Do NOT import previous model):**  
   ایپ اب ایک مکمل ماڈیولر آرکیٹیکچر پر مشتمل ہے۔ کسی بھی صورت میں پرانے مونو لیتھک کوڈ یا کسی ڈیفالٹ ٹیمپلیٹ کو دوبارہ امپورٹ نہ کریں۔
2. **ماڈیولر اسٹرکچر کا تحفظ:**  
   ہر فیچر کا کوڈ اپنی مخصوص ڈائریکٹری میں محفوظ ہے۔ کوئی فائل ڈیلیٹ نہ کریں۔
3. **مستقل سیٹنگز:**  
   - `applicationId = "com.pkstudio.ctexplorer.app"`
   - `namespace = "com.ct.explorer"`
   - ایپ کا نام: **"Cent File Manager"**

---

## 🏗️ Modular Architecture Map

```
app/src/main/java/com/ct/explorer/
│
├── core/                                # Core abstractions & decoupled buses
│   ├── base/
│   │   └── BaseFeatureViewModel.kt     # Safe coroutine dispatching & lifecycle management
│   ├── events/
│   │   ├── AppEventBus.kt              # Decoupled universal file & system notification bus
│   │   └── FileOperationBus.kt         # Decoupled copy, cut, paste operation events
│   └── navigation/
│       ├── NavigationManager.kt        # Decoupled stateful navigation manager & backstack
│       └── Screen.kt                   # 22 typed screen destination enums
│
├── data/                                # Data layer
│   ├── model/                          # Domain entity models (FileItem, TrashItem, etc.)
│   └── repository/                     # 13 isolated single-responsibility domain repositories
│       ├── FileRepository.kt           # Filesystem I/O, queries, size calculations
│       ├── VaultRepository.kt          # AES-256 encrypted vault storage & biometric verify
│       ├── TrashRepository.kt          # Recycle bin staging & original-path restore
│       ├── AppsRepository.kt           # Installed application metadata & APK extraction
│       ├── ZipRepository.kt            # Archive inspection, compression, extraction
│       ├── StorageAnalyzerRepository.kt# Categorized storage usage breakdowns
│       ├── DuplicateRepository.kt      # MD5/SHA256 duplicate content scanner
│       ├── FastShareRepository.kt      # Peer-to-peer file sharing protocol
│       ├── FavoritesRepository.kt      # Pinned files and folder shortcuts
│       ├── NetworkStorageRepository.kt # SMB/FTP remote network drive mounting
│       ├── SmartCollectionsRepository.kt# Tag and rule-based smart collections
│       ├── TagsRepository.kt           # Color and metadata tagging system
│       └── TimeMachineRepository.kt    # Chronological file alteration history
│
├── features/                            # Isolated feature business logic
│   ├── vault/
│   │   └── VaultViewModel.kt           # Isolated Vault feature viewmodel
│   └── network/
│       └── NetworkServerViewModel.kt   # Isolated FTP & WebShare server viewmodel
│
├── ui/                                  # Presentation layer
│   ├── components/                     # 23 reusable compose components (Dialogs, Bars, Players)
│   ├── screens/                        # 21 dedicated screen composables
│   ├── theme/                          # Material Design 3 theming (AMOLED & Dynamic)
│   └── viewmodel/                      # Screen state aggregators
│
└── utils/                              # Independent utilities & platform engines
    ├── ArchiveHelper.kt                # Zip-Slip defended archive engine
    ├── BiometricHelper.kt              # AndroidX Biometric authentication helper
    ├── FileOpener.kt                   # Android Intent & MIME type dispatcher
    ├── HashCalculator.kt               # Cryptographic file hashing
    ├── InAppPackageInstallerHelper.kt  # Android PackageInstaller session commit
    ├── FtpServer.kt                    # Embedded standalone FTP server
    ├── webshare/WebShareServer.kt      # Embedded HTTP WebShare server
    └── shredder/FileShredderHelper.kt  # DoD 5220.22-M multi-pass file shredder
```

---

## 🛡️ Import Directives for Future Sessions

Whenever this project is opened or imported:
1. **Execute-First Minimal Check:** Verify build with `compile_applet` immediately.
2. **Preserve Modularity:** When adding or updating features, do not stuff logic into a single monolithic class; use `BaseFeatureViewModel` and the dedicated repository in `data/repository/`.
3. **No Legacy Regression:** Treat the current modular layout as the baseline. Do not replace it with earlier revisions.
