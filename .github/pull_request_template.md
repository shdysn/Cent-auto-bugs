## Pull Request Checklist

### 📦 Release APK Size Budget Compliance (2 MB - 3 MB)
- [ ] Have you tested the release build using `./gradlew assembleRelease`?
- [ ] Is the release APK size between **2.0 MB** and **3.0 MB**? (Current baseline: ~2.3 MB)
- [ ] Are R8 minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) intact?
- [ ] No heavy external third-party libraries added without tree-shaking verification.
- [ ] All new icons/drawables are vector drawables (XML) rather than large raster bitmaps (PNG/JPG).

### 🧪 Quality & Tests
- [ ] Build compiles successfully (`./gradlew assembleDebug` or `./gradlew assembleRelease`).
- [ ] No regression in core file management features (Explorer, Dual-Pane, WebShare, FTP, Cleaner).
