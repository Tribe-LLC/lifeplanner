# ─── Kotlin Serialization ───
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class az.tribe.lifeplanner.**$$serializer { *; }
-keepclassmembers class az.tribe.lifeplanner.** { *** Companion; }
-keepclasseswithmembers class az.tribe.lifeplanner.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep serializable DTOs (sync, network, backup models) — accessed via reflection
-keepclassmembers class az.tribe.lifeplanner.data.sync.dto.** { *; }
-keepclassmembers class az.tribe.lifeplanner.data.model.** { *; }
-keepclassmembers class az.tribe.lifeplanner.domain.model.** { *; }

# ─── Ktor ───
# Only keep client engine + serialization plugin classes, not the entire library
-keep class io.ktor.client.** { *; }
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.http.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ─── Koin ───
# Keep Koin core + constructor injection
-keep class org.koin.core.** { *; }
-dontwarn org.koin.**
-keepclassmembers class az.tribe.lifeplanner.** { public <init>(...); }

# ─── SQLDelight ───
-keep class app.cash.sqldelight.driver.** { *; }
-dontwarn app.cash.sqldelight.**
-keep class az.tribe.lifeplanner.database.** { *; }

# ─── Firebase ───
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ─── Supabase KMP ───
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ─── Compose ───
# Compose handles its own keep rules via the plugin; only keep runtime
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ─── Kermit Logging ───
-keep class co.touchlab.kermit.** { *; }
-dontwarn co.touchlab.kermit.**

# ─── BuildKonfig ───
-keep class az.tribe.lifeplanner.BuildKonfig { *; }

# ─── Facebook SDK ───
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**

# ─── Enums (used in serialization and navigation) ───
-keepclassmembers enum az.tribe.lifeplanner.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── General ───
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
