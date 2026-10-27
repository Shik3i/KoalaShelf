# ProGuard / R8 Rules for KoalaShelf Android

# Kotlinx Serialization & Generic Type Signatures (Ktor ContentNegotiation)
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.SerializationKt
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class net.koalastuff.koalashelf.**$$serializer { *; }
-keepclassmembers class net.koalastuff.koalashelf.** {
    *** Companion;
}
-keepclasseswithmembers class net.koalastuff.koalashelf.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class net.koalastuff.koalashelf.** { *; }
-keep class * implements kotlinx.serialization.KSerializer { *; }

# Guava ListenableFuture & Coroutines Guava bridge (Media3 MediaLibraryService)
-keep class com.google.common.util.concurrent.** { *; }
-dontwarn com.google.common.util.concurrent.**

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
