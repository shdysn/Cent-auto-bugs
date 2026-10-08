# 📦 Release APK Size Guidelines (2 MB - 3 MB Target)

## 🇵🇰 اردو ہدایات (Instructions in Urdu)

یہ ہدایات اس بات کو یقینی بنانے کے لیے ہیں کہ جب بھی **Cent File Manager** کی ریلیز APK بلڈ (`Release APK`) بنائی جائے، تو اس کا سائز لازماً **2 MB سے 3 MB** کے درمیان رہے۔

### 🎯 بنیادی اصول اور ہدایات:

1. **R8 Minification & Resource Shrinking ہمیشہ آن رکھیں**:
   - `app/build.gradle.kts` میں ریلیز بلڈ ٹائپ میں یہ سیٹنگز کبھی بند نہ کریں:
     ```kotlin
     buildTypes {
         release {
             isDebuggable = false
             isMinifyEnabled = true
             isShrinkResources = true
             proguardFiles(
                 getDefaultProguardFile("proguard-android-optimize.txt"),
                 "proguard-rules.pro"
             )
         }
     }
     ```
   - یہ سیٹنگز تمام غیر ضروری کلاسز، میتھوڈز اور ریسورسز کو خودکار طریقے سے ختم کر کے سائز کو 2.3 MB تک رکھتی ہیں۔

2. **غیر ضروری لائبریریاں (Bloated Dependencies) شامل نہ کریں**:
   - صرف ضروری اور لائٹ ویٹ Jetpack Compose لائبریریاں استعمال کریں۔
   - بھاری ایکسٹرنل لائبریریاں (مثلاً پوری Firebase سوٹ، ExoPlayer فل بنڈل، یا بڑی جار فائلز) شامل نہ کریں۔

3. **پیکجنگ ایکسکلوزنز (Packaging Excludes)**:
   - `app/build.gradle.kts` میں موجود `packaging` بلاک کو برقرار رکھیں تاکہ تمام میٹا ڈیٹا، لائسنس ٹیکسٹ، اور کوٹلن ماڈیول فائلیں APK سے خارج رہیں:
     ```kotlin
     packaging {
         resources {
             excludes += setOf(
                 "META-INF/*.version",
                 "META-INF/DEPENDENCIES",
                 "META-INF/LICENSE*",
                 "META-INF/NOTICE*",
                 "META-INF/*.properties",
                 "META-INF/*.kotlin_module",
                 "DebugProbesKt.bin"
             )
         }
     }
     ```

4. **ویکٹر گرافکس (Vector Drawables)**:
   - ہمیشہ XML ویکٹر ڈرائنگز (`VectorDrawable`) استعمال کریں، بھاری PNG یا JPG تصاویر سے گریز کریں۔

5. **ریلیز APK بلڈ کرنے اور سائز چیک کرنے کا طریقہ**:
   ```bash
   ./gradlew assembleRelease
   ```
   بلڈ کے بعد APK کا سائز چیک کریں:
   ```bash
   ls -lh app/build/outputs/apk/release/app-release.apk
   ```
   موجودہ ٹیسٹ شدہ سائز: **2.3 MB (2,310,691 bytes)** جو کہ مطلوبہ 2 سے 3 MB کے بالکل اندر ہے۔

6. **GitHub Actions خودکار تصدیق**:
   - `.github/workflows/build-apk.yml` میں سائز چیکنگ کا مرحلہ شامل ہے جو ہر بلڈ پر خودکار طریقے سے سائز کی تصدیق کرتا ہے اور 3 MB سے زیادہ ہونے پر الرٹ دیتا ہے۔

---

## 🇬🇧 English Guidelines

This document outlines the strict guidelines to ensure that every release APK build of **Cent File Manager** stays strictly within the **2.0 MB to 3.0 MB** target budget.

### 📐 Configuration Details & Size Budget

- **Target Range:** `2.00 MB` (2,000,000 bytes) to `3.00 MB` (3,145,728 bytes)
- **Current Verified Size:** **2.30 MB** (`2,310,691 bytes`)

### 🛠️ Mandatory Build Rules

1. **Keep R8 Minification and Resource Shrinking Active**:
   - `isMinifyEnabled = true` enables full R8 dead-code elimination, tree-shaking, and bytecode optimization.
   - `isShrinkResources = true` removes all unreferenced drawables, strings, and layouts.

2. **Strict Packaging Excludes**:
   - Maintain the `packaging.resources.excludes` block in `app/build.gradle.kts` to strip unused `META-INF` manifests, license text files, and Kotlin runtime debug probes.

3. **ProGuard Rules Optimization**:
   - `app/proguard-rules.pro` strips debug logging (`android.util.Log.d`, `i`, `v`) and safely obfuscates non-public components.

4. **Zero Bloat Dependencies**:
   - Avoid heavy multi-megabyte SDKs.
   - Native Java/Android NIO and standard runtime APIs are used for HTTP/FTP/Archive tasks instead of multi-megabyte third-party engines.

5. **Automated CI Enforcement**:
   - The GitHub Actions workflow (`.github/workflows/build-apk.yml`) automatically checks the APK byte size before creating releases or artifacts. If the size exceeds 3.0 MB, the workflow will fail with an error log indicating resource bloat.
