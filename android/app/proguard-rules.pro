# ── Our India ProGuard Rules ──────────────────────────────────────────

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ourindia.app.**$$serializer { *; }
-keepclassmembers class com.ourindia.app.** { *** Companion; }
-keepclasseswithmembers class com.ourindia.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Room entities
-keep class com.ourindia.app.data.local.entity.** { *; }

# Keep Retrofit interfaces
-keep,allowobfuscation interface com.ourindia.app.data.remote.OurIndiaApiService

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
