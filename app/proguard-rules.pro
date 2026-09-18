# Add project specific ProGuard rules here.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class com.plantshelf.app.data.models.** { *; }
-keep class com.plantshelf.app.data.entity.** { *; }
