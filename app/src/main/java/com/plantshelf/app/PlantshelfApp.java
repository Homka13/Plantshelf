package com.plantshelf.app;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.plantshelf.app.notifications.NotificationHelper;
import com.plantshelf.app.notifications.NotificationScheduler;

public class PlantshelfApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Setup notification channels
        try {
            NotificationHelper.createNotificationChannel(this);
        } catch (Exception ignored) {}

        // Schedule daily care reminder check according to user preferences
        try {
            NotificationScheduler.scheduleCareReminder(this);
        } catch (Exception e) {
            android.util.Log.w("PlantshelfApp", "WorkManager init skipped or failed: " + e.getMessage());
        }
    }
}
