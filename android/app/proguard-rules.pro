# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.savyor.app.data.local.entity.** { *; }
-dontwarn androidx.room.paging.**

# Keep Gson, TypeTokens & Model Schemas (R8 Full Mode safe)
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.savyor.app.domain.model.** { *; }
-keep class com.savyor.app.data.remote.KeyMomentWithImage { *; }
-keep class com.savyor.app.data.remote.ExtractedFramesResult { *; }
-keep class com.savyor.app.data.remote.ReelDownloadResult { *; }
-keep class com.savyor.app.data.remote.GeminiFilesClient$RemoteFileResource { *; }
-keep class com.savyor.app.data.remote.GeminiApiResponse { *; }
-keep class com.savyor.app.data.remote.GeminiCandidate { *; }
-keep class com.savyor.app.data.remote.GeminiContent { *; }
-keep class com.savyor.app.data.remote.GeminiPart { *; }
-keep class com.savyor.app.data.remote.GeminiModelOption { *; }
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers @androidx.annotation.Keep class * { *; }

# Keep WorkManager Workers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep OkHttp / Okio / Tink
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**

# Keep youtubedl-android & internal Jackson / Apache Commons Compress dependencies
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
