# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Navigation Compose classes
-keep class androidx.navigation.compose.NavHost
-keep class androidx.navigation.compose.NavHostController
-keep class androidx.navigation.compose.composable

# Keep DataStore preferences
-keep class androidx.datastore.preferences.core.* { *; }
-keep class androidx.datastore.* { *; }

# Remove warnings for unused arguments (Jetpack Compose)
-dontwarn androidx.compose.runtime.**

# Compose
-dontnote kotlinx.coroutines.DebugKt
-keep,includedescriptorclasses class com.example.llamaserver.ui.theme.** { *; }
-keep,includedescriptorclasses class com.example.llamaserver.ui.components.** { *; }