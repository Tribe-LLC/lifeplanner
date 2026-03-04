# ─── Kotlin Serialization ───
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class az.tribe.lifeplanner.**$$serializer { *; }
-keepclassmembers class az.tribe.lifeplanner.** { *** Companion; }
-keepclasseswithmembers class az.tribe.lifeplanner.** { kotlinx.serialization.KSerializer serializer(...); }

# ─── Ktor ───
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ─── Koin ───
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keepclassmembers class * { public <init>(...); }

# ─── SQLDelight ───
-keep class app.cash.sqldelight.** { *; }
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
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─── Kermit Logging ───
-keep class co.touchlab.kermit.** { *; }
-dontwarn co.touchlab.kermit.**

# ─── BuildKonfig ───
-keep class az.tribe.lifeplanner.BuildKonfig { *; }

# ─── General ───
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
