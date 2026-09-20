# ==============================================================================
# ProGuard / R8 Rules for Plantshelf Release Minification
# ==============================================================================

# --- Room Persistence Library ---
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Data Entities, Models & Serialization ---
# Keep all data models and entities to prevent field obfuscation during JSON (de)serialization
-keep class com.plantshelf.app.data.models.** { *; }
-keep class com.plantshelf.app.data.entity.** { *; }
-keep class com.plantshelf.app.domain.model.** { *; }
-keepclassmembers class com.plantshelf.app.data.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Gson Rules ---
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# --- AndroidX Security & Keystore Crypto ---
-keep class androidx.security.crypto.** { *; }

# --- Glide Image Loading ---
-keep public class * implements com.github.bumptech.glide.module.GlideModule
-keep class * extends com.github.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.github.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# --- WorkManager ---
-keep class * extends androidx.work.Worker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
