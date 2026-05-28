# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

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
-keep,includedescriptorclasses class tridefender.llama.snapdragon.ui.theme.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class tridefender.llama.snapdragon.**$$serializer { *; }
-keepclassmembers class tridefender.llama.snapdragon.** {
    *** Companion;
}
-keepclasseswithmembers class tridefender.llama.snapdragon.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep model classes for serialization
-keep class tridefender.llama.snapdragon.model.** { *; }
-keep class tridefender.llama.snapdragon.model.KernelSource { *; }
-keep class tridefender.llama.snapdragon.model.KernelVersion { *; }
-keep class tridefender.llama.snapdragon.model.KernelConfig { *; }
-keep class tridefender.llama.snapdragon.model.ServerConfig { *; }
