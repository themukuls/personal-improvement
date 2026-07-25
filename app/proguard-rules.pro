# ── kotlinx.serialization ─────────────────────────────────────────────────────
# Keep generated serializers and the Companion that exposes serializer().
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.chiefofstaff.**$$serializer { *; }
-keepclassmembers class com.chiefofstaff.** {
    *** Companion;
}
# Keep @Serializable model classes themselves (fields are referenced reflectively by name).
-keep @kotlinx.serialization.Serializable class com.chiefofstaff.** { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# ── WorkManager ───────────────────────────────────────────────────────────────
# Workers are instantiated by name via reflection.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Ktor client + engine ──────────────────────────────────────────────────────
# Ktor resolves engines and plugins via service loaders / reflection.
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**            # Ktor logging references SLF4J, which we don't ship.

# ── Health Connect ────────────────────────────────────────────────────────────
-dontwarn androidx.health.connect.**

# ── App enums used across serialization / Room ────────────────────────────────
-keepclassmembers enum com.chiefofstaff.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
