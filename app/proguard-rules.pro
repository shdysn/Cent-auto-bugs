# Keep Jetpack Compose & UI classes
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Keep Activity & Receivers
-keep public class com.ct.explorer.MainActivity
-keep public class com.ct.explorer.utils.PackageInstallerStatusReceiver
-keep public class com.ct.explorer.widget.CtStorageWidgetProvider

# Data models reflection safety
-keepclassmembers class com.ct.explorer.data.model.** {
    <fields>;
    <init>(...);
}

# ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Strip debug logging calls
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Suppress harmless warnings during optimization
-dontwarn **
