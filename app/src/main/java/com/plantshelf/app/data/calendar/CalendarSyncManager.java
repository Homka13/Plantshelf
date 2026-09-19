package com.plantshelf.app.data.calendar;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.PlantEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton managing Android Calendar synchronization, batch inserts, and lifecycle cleanup.
 */
public class CalendarSyncManager {

    private static final String TAG = "CalendarSyncManager";
    public static final String ID_TAG_PREFIX = "Plantshelf_ID:";

    private static volatile CalendarSyncManager instance;

    private final Context appContext;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface SyncCallback {
        void onProgress(int current, int total);
        void onSuccess(int syncedCount);
        void onError(String message);
    }

    private CalendarSyncManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static CalendarSyncManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CalendarSyncManager.class) {
                if (instance == null) {
                    instance = new CalendarSyncManager(context);
                }
            }
        }
        return instance;
    }

    public boolean hasCalendarPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    public Long findPrimaryCalendarId() {
        if (!hasCalendarPermission()) return null;
        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.VISIBLE
        };
        try (Cursor cursor = appContext.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Calendars.IS_PRIMARY + " DESC, " + CalendarContract.Calendars._ID + " ASC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed querying calendars", e);
        }
        return null;
    }

    /**
     * Batch synchronizes care events for all plants into Android Calendar.
     */
    public void syncAllPlants(List<PlantEntity> plants, SyncCallback callback) {
        if (!hasCalendarPermission()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Немає дозволу на доступ до календаря"));
            }
            return;
        }

        executor.execute(() -> {
            Long calendarId = findPrimaryCalendarId();
            if (calendarId == null) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Не знайдено доступного календаря на пристрої"));
                }
                return;
            }

            if (plants == null || plants.isEmpty()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(0));
                }
                return;
            }

            int total = plants.size();
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            Map<Integer, PlantEntity> indexToPlantMap = new HashMap<>();

            for (PlantEntity plant : plants) {
                int interval = plant.getIntervalDays() > 0 ? plant.getIntervalDays() : 7;
                Calendar startTime = Calendar.getInstance();
                startTime.set(Calendar.HOUR_OF_DAY, 9);
                startTime.set(Calendar.MINUTE, 0);
                startTime.set(Calendar.SECOND, 0);

                int daysLeft = plant.getDaysUntilWatering();
                if (daysLeft > 0) {
                    startTime.add(Calendar.DAY_OF_YEAR, daysLeft);
                }

                String title = "🌱 Полив: " + plant.getName();
                String desc = "Рослина: " + plant.getName() + "\n";
                if (plant.getVariety() != null && !plant.getVariety().isEmpty()) {
                    desc += "Сорт: " + plant.getVariety() + "\n";
                }
                desc += "Графік: кожні " + interval + " дн. влітку, " + plant.getIntervalDaysWinter() + " дн. взимку\n";
                if (plant.getRecommendedFertilizers() != null && !plant.getRecommendedFertilizers().isEmpty()) {
                    desc += "Добрива: " + plant.getRecommendedFertilizers() + "\n";
                }
                desc += "\n" + ID_TAG_PREFIX + plant.getId() + "\nСтворено у додатку Plantshelf";

                if (plant.getCalendarEventId() != null && !plant.getCalendarEventId().isEmpty()) {
                    // Update existing
                    try {
                        long eventId = Long.parseLong(plant.getCalendarEventId());
                        Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                        ops.add(ContentProviderOperation.newUpdate(eventUri)
                                .withValue(CalendarContract.Events.TITLE, title)
                                .withValue(CalendarContract.Events.DESCRIPTION, desc)
                                .withValue(CalendarContract.Events.RRULE, "FREQ=DAILY;INTERVAL=" + interval)
                                .build());
                    } catch (Exception e) {
                        // If ID invalid, fallback to insert
                        plant.setCalendarEventId(null);
                    }
                }

                if (plant.getCalendarEventId() == null || plant.getCalendarEventId().isEmpty()) {
                    int eventOpIndex = ops.size();
                    indexToPlantMap.put(eventOpIndex, plant);

                    ops.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                            .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                            .withValue(CalendarContract.Events.TITLE, title)
                            .withValue(CalendarContract.Events.DESCRIPTION, desc)
                            .withValue(CalendarContract.Events.DTSTART, startTime.getTimeInMillis())
                            .withValue(CalendarContract.Events.DTEND, startTime.getTimeInMillis() + (30 * 60 * 1000))
                            .withValue(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID())
                            .withValue(CalendarContract.Events.RRULE, "FREQ=DAILY;INTERVAL=" + interval)
                            .withValue(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
                            .build());

                    // Add 15-minute alert reminder
                    ops.add(ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                            .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOpIndex)
                            .withValue(CalendarContract.Reminders.MINUTES, 15)
                            .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                            .build());
                }
            }

            int syncedCount = 0;
            try {
                if (!ops.isEmpty()) {
                    ContentProviderResult[] results = appContext.getContentResolver().applyBatch(CalendarContract.AUTHORITY, ops);
                    for (Map.Entry<Integer, PlantEntity> entry : indexToPlantMap.entrySet()) {
                        int opIndex = entry.getKey();
                        PlantEntity plant = entry.getValue();
                        if (opIndex < results.length && results[opIndex].uri != null) {
                            long createdId = ContentUris.parseId(results[opIndex].uri);
                            plant.setCalendarEventId(String.valueOf(createdId));
                            PlantshelfDatabase.getInstance(appContext).plantDao().update(plant);
                        }
                    }
                    syncedCount = total;
                }
                final int finalCount = syncedCount;
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(finalCount));
                }
            } catch (Exception e) {
                Log.e(TAG, "Batch calendar sync error", e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Помилка пакетної синхронізації: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * Updates an existing event or creates one if not present.
     */
    public void updateEventForPlant(PlantEntity plant) {
        if (plant == null || !hasCalendarPermission()) return;

        executor.execute(() -> {
            boolean updated = false;
            if (plant.getCalendarEventId() != null && !plant.getCalendarEventId().isEmpty()) {
                updated = CalendarIntegrationHelper.updateCalendarEventSchedule(appContext, plant);
            }

            // Fallback search by ID tag if direct ID update did not succeed
            if (!updated) {
                String selection = CalendarContract.Events.DESCRIPTION + " LIKE ?";
                String[] selectionArgs = new String[]{"%" + ID_TAG_PREFIX + plant.getId() + "%"};
                int interval = plant.getIntervalDays() > 0 ? plant.getIntervalDays() : 7;
                ContentValues cv = new ContentValues();
                cv.put(CalendarContract.Events.TITLE, "🌱 Полив: " + plant.getName());
                cv.put(CalendarContract.Events.RRULE, "FREQ=DAILY;INTERVAL=" + interval);
                try {
                    int rows = appContext.getContentResolver().update(CalendarContract.Events.CONTENT_URI, cv, selection, selectionArgs);
                    if (rows > 0) updated = true;
                } catch (Exception e) {
                    Log.w(TAG, "Fallback event update failed", e);
                }
            }
        });
    }

    /**
     * Deletes any associated calendar events when a plant is deleted, with ID tag fallback.
     */
    public void deleteEventForPlant(PlantEntity plant) {
        if (plant == null || !hasCalendarPermission()) return;

        executor.execute(() -> {
            // 1. Direct delete by ID
            if (plant.getCalendarEventId() != null && !plant.getCalendarEventId().isEmpty()) {
                CalendarIntegrationHelper.deleteCalendarEvent(appContext, plant.getCalendarEventId());
            }

            // 2. Fallback search & delete by ID tag
            try {
                String selection = CalendarContract.Events.DESCRIPTION + " LIKE ?";
                String[] selectionArgs = new String[]{"%" + ID_TAG_PREFIX + plant.getId() + "%"};
                appContext.getContentResolver().delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs);
            } catch (Exception e) {
                Log.w(TAG, "Fallback event deletion failed", e);
            }
        });
    }
}
