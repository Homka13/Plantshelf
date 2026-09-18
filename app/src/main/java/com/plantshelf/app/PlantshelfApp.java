package com.plantshelf.app;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.plantshelf.app.notifications.CareReminderWorker;
import com.plantshelf.app.notifications.NotificationHelper;

import java.util.concurrent.TimeUnit;

public class PlantshelfApp extends Application {

    private static final String WORK_NAME_CARE_REMINDER = "care_reminder_work";

    @Override
    public void onCreate() {
        super.onCreate();

        // Setup notification channels
        NotificationHelper.createNotificationChannel(this);

        // Schedule daily care reminder check
        scheduleCareReminder();
    }

    private void scheduleCareReminder() {
        PeriodicWorkRequest reminderRequest =
                new PeriodicWorkRequest.Builder(CareReminderWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(1, TimeUnit.HOURS)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WORK_NAME_CARE_REMINDER,
                ExistingPeriodicWorkPolicy.KEEP,
                reminderRequest
        );
    }
}
