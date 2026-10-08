# 🚨 AI Studio Import & Preservation Policy (لازمی ہدایات برائے AI Studio)

> **PROJECT PRESERVATION DIRECTIVE:**  
> This application is an established, feature-complete Android application. When importing or building this GitHub repository, **EVERY PIECE OF EXISTING CODE, CONFIGURATION, AND DATA MODEL MUST BE PRESERVED 100% UNCHANGED**.

---

### 🇵🇰 اردو خلاصہ (Summary in Urdu)
جب بھی AI Studio میں یہ ریپازٹری امپورٹ کی جائے:
1. **کوئی بھی پرانا فیچر یا کوڈ تبدیل نہ کریں**: ایپ کا تمام کوڈ مکمل اور ٹیسٹ شدہ ہے۔
2. **پیکج اور آئی ڈی مستقل ہیں**:
   - `applicationId = "com.pkstudio.ctexplorer.app"`
   - `namespace = "com.ct.explorer"`
   - ایپ کا نام: **"Cent File Manager"**
3. **کوئی فائل ڈیلیٹ نہ کریں**: 22 اسکرینز، ماڈلز، اور ویو ماڈلز کو جوں کا توں محفوظ رکھیں۔
4. **ماڈیولر آرکیٹیکچر کا تحفظ (Do NOT import previous model)**: یہ ایپ ماڈیولر بیسڈ ہے (`core/`, `features/`, `data/repository/`)۔ پچھلے مونو لیتھک ماڈل پر واپس جانے کی سخت ممانعت ہے۔

---

### 🛡️ Immutable Settings (Do Not Alter)

```kotlin
// In app/build.gradle.kts:
namespace = "com.ct.explorer"
defaultConfig {
    applicationId = "com.pkstudio.ctexplorer.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
}
```

```json
// In metadata.json:
{
  "name": "Cent File Manager",
  "description": "Clean, modern, and high-performance file manager with recent files feed, categorized media, storage cleaner, and file browser.",
  "requestFramePermissions": [],
  "majorCapabilities": ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]
}
```

### 🎯 Rules of Engagement for AI Agent
1. **Never reset to boilerplate / template.**
2. **Never change `applicationId` to `com.aistudio.*`.**
3. **Never rename directory paths under `app/src/main/java/com/ct/explorer`.**
4. **Compile cleanly using `compile_applet`.**
5. **Preserve R8 minification and the 2.3 MB APK size budget.**
6. **Preserve Modular Architecture (DO NOT import/revert to previous models):** Keep the codebase modular-based (`core/`, `features/`, `data/repository/`, `ui/components/`, `ui/screens/`). Never collapse isolated components back into legacy monolithic structures or import outdated non-modular code.
