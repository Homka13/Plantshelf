package com.plantshelf.app.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

public class NotificationPrefs {

    private static final String PREF_NAME = "plantshelf_notifications";
    private static final String KEY_ENABLED = "notifications_enabled";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_PERMISSION_PROMPT_SHOWN = "permission_prompt_shown";

    public static final int DEFAULT_HOUR = 9;
    public static final int DEFAULT_MINUTE = 0;

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isNotificationsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int getReminderHour(Context context) {
        return getPrefs(context).getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR);
    }

    public static int getReminderMinute(Context context) {
        return getPrefs(context).getInt(KEY_REMINDER_MINUTE, DEFAULT_MINUTE);
    }

    public static void setReminderTime(Context context, int hour, int minute) {
        getPrefs(context).edit()
                .putInt(KEY_REMINDER_HOUR, hour)
                .putInt(KEY_REMINDER_MINUTE, minute)
                .apply();
    }

    public static String getFormattedTime(Context context) {
        int hour = getReminderHour(context);
        int minute = getReminderMinute(context);
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    public static boolean isPermissionPromptShown(Context context) {
        return getPrefs(context).getBoolean(KEY_PERMISSION_PROMPT_SHOWN, false);
    }

    public static void setPermissionPromptShown(Context context, boolean shown) {
        getPrefs(context).edit().putBoolean(KEY_PERMISSION_PROMPT_SHOWN, shown).apply();
    }
}
