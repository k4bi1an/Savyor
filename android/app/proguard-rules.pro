# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.savyor.app.data.remote.** { *; }
-keep class com.savyor.app.domain.model.** { *; }

# Keep youtubedl-android
-keep class com.yausername.youtubedl_android.** { *; }
