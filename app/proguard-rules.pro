# Keep kotlinx.serialization generated serializers.
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

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *

# WorkManager workers are instantiated by name via reflection.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
