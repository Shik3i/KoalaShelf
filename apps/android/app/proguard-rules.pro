# ProGuard / R8 Rules for KoalaShelf Android

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking @kotlinx.serialization.Serializable class * { *; }

# Bundled SQLite Driver & Room
-keep class androidx.sqlite.driver.bundled.** { *; }
-keep class androidx.room3.** { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }

# Coil 3
-keep class coil3.** { *; }

# Ktor HTTP Client
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn java.lang.management.**
