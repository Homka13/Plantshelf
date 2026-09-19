package com.plantshelf.app.notifications;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class NotificationScheduler {

    private static final String TAG = "NotificationScheduler";
    public static final String WORK_NAME_CARE_REMINDER = "care_reminder_work";

    public static void scheduleCareReminder(Context context) {
        try {
            if (!NotificationPrefs.isNotificationsEnabled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CARE_REMINDER);
                Log.d(TAG, "Care reminder work cancelled (notifications disabled)");
                return;
            }

            int targetHour = NotificationPrefs.getReminderHour(context);
            int targetMinute = NotificationPrefs.getReminderMinute(context);

            Calendar now = Calendar.getInstance();
            Calendar target = Calendar.getInstance();
            target.set(Calendar.HOUR_OF_DAY, targetHour);
            target.set(Calendar.MINUTE, targetMinute);
            target.set(Calendar.SECOND, 0);
            target.set(Calendar.MILLISECOND, 0);

            if (target.getTimeInMillis() <= now.getTimeInMillis()) {
                target.add(Calendar.DAY_OF_YEAR, 1);
            }

            long initialDelayMillis = target.getTimeInMillis() - now.getTimeInMillis();

            PeriodicWorkRequest reminderRequest =
                    new PeriodicWorkRequest.Builder(CareReminderWorker.class, 24, TimeUnit.HOURS)
                            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                            .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_CARE_REMINDER,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    reminderRequest
            );

            Log.d(TAG, "Scheduled care reminder for " + targetHour + ":" + targetMinute + " (delay: " + (initialDelayMillis / 60000) + " min)");
        } catch (Exception e) {
            Log.w(TAG, "Failed scheduling care reminder: " + e.getMessage());
        }
    }
}
