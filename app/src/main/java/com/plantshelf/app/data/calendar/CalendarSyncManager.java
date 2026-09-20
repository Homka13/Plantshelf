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
        return CalendarIntegrationHelper.findPrimaryCalendarId(appContext);
    }

    /**
     * Batch synchronizes care events for all plants into Android Calendar asynchronously.
     */
    public void syncAllPlants(List<PlantEntity> plants, SyncCallback callback) {
        if (!hasCalendarPermission()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Немає дозволу на доступ до календаря"));
            }
            return;
        }

        executor.execute(() -> {
            try {
                int syncedCount = syncAllPlantsBatchSync(plants);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(syncedCount));
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
     * Queries all plants from local database and batch-syncs them into Android Calendar.
     */
    public void syncAllPlants(SyncCallback callback) {
        executor.execute(() -> {
            List<PlantEntity> plants = PlantshelfDatabase.getInstance(appContext).plantDao().getAllPlantsSync();
            syncAllPlants(plants, callback);
        });
    }

    /**
     * Synchronously bulk updates and synchronizes all plant events in the system calendar
     * using ContentProviderOperation.applyBatch() for maximum efficiency.
     *
     * @param plants List of plants to update/sync in the system calendar
     * @return Number of synchronized plants
     */
    public int syncAllPlantsBatchSync(List<PlantEntity> plants) throws Exception {
        if (!hasCalendarPermission()) {
            throw new SecurityException("Немає дозволу на доступ до календаря");
        }

        if (plants == null || plants.isEmpty()) {
            return 0;
        }

        Long calendarId = findPrimaryCalendarId();
        if (calendarId == null) {
            throw new IllegalStateException("Не знайдено доступного календаря на пристрої");
        }

        // Query existing calendar events tagged with Plantshelf ID prefix to avoid duplicates
        Map<String, Long> existingTaggedEvents = new HashMap<>();
        String escapedTag = ID_TAG_PREFIX.replace("_", "\\_");
        try (Cursor c = appContext.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION},
                CalendarContract.Events.CALENDAR_ID + " = ? AND " + CalendarContract.Events.DESCRIPTION + " LIKE ? ESCAPE '\\'",
                new String[]{String.valueOf(calendarId), "%" + escapedTag + "%"},
                null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long evId = c.getLong(0);
                    String desc = c.getString(1);
                    if (desc != null) {
                        int idx = desc.indexOf(ID_TAG_PREFIX);
                        if (idx != -1) {
                            String tail = desc.substring(idx + ID_TAG_PREFIX.length()).trim();
                            String plantId = tail.split("[\\s\\n]+")[0];
                            if (!plantId.isEmpty()) {
                                existingTaggedEvents.put(plantId, evId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed pre-fetching tagged events from calendar: " + e.getMessage());
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
            if (plant.getSoil() != null && !plant.getSoil().isEmpty()) {
                desc += "Ґрунт: " + plant.getSoil() + "\n";
            }
            if (plant.getEffectiveRecommendedFertilizers() != null && !plant.getEffectiveRecommendedFertilizers().isEmpty()) {
                desc += "Добрива: " + plant.getEffectiveRecommendedFertilizers() + "\n";
            }
            desc += "\n" + ID_TAG_PREFIX + plant.getId() + "\nСтворено у додатку Plantshelf\n";

            // If plant has no calendarEventId in local database, but matches an existing tagged event in calendar:
            if ((plant.getCalendarEventId() == null || plant.getCalendarEventId().trim().isEmpty())
                    && existingTaggedEvents.containsKey(plant.getId())) {
                long matchedId = existingTaggedEvents.get(plant.getId());
                plant.setCalendarEventId(String.valueOf(matchedId));
                try {
                    PlantshelfDatabase.getInstance(appContext).plantDao().update(plant);
                } catch (Exception ignored) {}
            }

            if (plant.getCalendarEventId() != null && !plant.getCalendarEventId().trim().isEmpty()) {
                // Update existing event efficiently using ContentProviderOperation.newUpdate
                try {
                    long eventId = Long.parseLong(plant.getCalendarEventId());
                    Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                    ops.add(ContentProviderOperation.newUpdate(eventUri)
                            .withValue(CalendarContract.Events.TITLE, title)
                            .withValue(CalendarContract.Events.DESCRIPTION, desc)
                            .withValue(CalendarContract.Events.DTSTART, startTime.getTimeInMillis())
                            .withValue(CalendarContract.Events.DTEND, startTime.getTimeInMillis() + (30 * 60 * 1000))
                            .withValue(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID())
                            .withValue(CalendarContract.Events.RRULE, null)
                            .build());
                } catch (Exception e) {
                    plant.setCalendarEventId(null);
                }
            }

            if (plant.getCalendarEventId() == null || plant.getCalendarEventId().trim().isEmpty()) {
                int eventOpIndex = ops.size();
                indexToPlantMap.put(eventOpIndex, plant);

                ops.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                        .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                        .withValue(CalendarContract.Events.TITLE, title)
                        .withValue(CalendarContract.Events.DESCRIPTION, desc)
                        .withValue(CalendarContract.Events.DTSTART, startTime.getTimeInMillis())
                        .withValue(CalendarContract.Events.DTEND, startTime.getTimeInMillis() + (30 * 60 * 1000))
                        .withValue(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID())
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

        if (!ops.isEmpty()) {
            ContentProviderResult[] results = appContext.getContentResolver().applyBatch(CalendarContract.AUTHORITY, ops);
            for (Map.Entry<Integer, PlantEntity> entry : indexToPlantMap.entrySet()) {
                int opIndex = entry.getKey();
                PlantEntity plant = entry.getValue();
                if (opIndex < results.length && results[opIndex].uri != null) {
                    long createdId = ContentUris.parseId(results[opIndex].uri);
                    plant.setCalendarEventId(String.valueOf(createdId));
                    try {
                        PlantshelfDatabase.getInstance(appContext).plantDao().update(plant);
                    } catch (Exception ignored) {}
                }
            }
        }

        return total;
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
                Long calendarId = findPrimaryCalendarId();
                if (calendarId != null) {
                    String plantId = plant.getId();
                    String escapedTag = ID_TAG_PREFIX.replace("_", "\\_");
                    String selection = CalendarContract.Events.CALENDAR_ID + " = ? AND ("
                            + CalendarContract.Events.DESCRIPTION + " LIKE ? ESCAPE '\\' OR "
                            + CalendarContract.Events.DESCRIPTION + " LIKE ? ESCAPE '\\')";
                    String[] selectionArgs = new String[]{
                            String.valueOf(calendarId),
                            "%" + escapedTag + plantId + "\n%",
                            "%" + escapedTag + " " + plantId + "\n%"
                    };
                    Calendar startTime = Calendar.getInstance();
                    startTime.set(Calendar.HOUR_OF_DAY, 9);
                    startTime.set(Calendar.MINUTE, 0);
                    startTime.set(Calendar.SECOND, 0);
                    int daysLeft = plant.getDaysUntilWatering();
                    if (daysLeft > 0) {
                        startTime.add(Calendar.DAY_OF_YEAR, daysLeft);
                    }
                    ContentValues cv = new ContentValues();
                    cv.put(CalendarContract.Events.TITLE, "🌱 Полив: " + plant.getName());
                    cv.put(CalendarContract.Events.DTSTART, startTime.getTimeInMillis());
                    cv.put(CalendarContract.Events.DTEND, startTime.getTimeInMillis() + (30 * 60 * 1000));
                    cv.putNull(CalendarContract.Events.RRULE);
                    try {
                        int rows = appContext.getContentResolver().update(CalendarContract.Events.CONTENT_URI, cv, selection, selectionArgs);
                        if (rows > 0) updated = true;
                    } catch (Exception e) {
                        Log.w(TAG, "Fallback event update failed", e);
                    }
                }
            }
        });
    }

    /**
     * Deletes any associated calendar events when a plant is deleted, using stored
     * calendarEventId with a fallback lookup using 'Plantshelf_ID:' tags in event descriptions.
     */
    public void deleteEventForPlant(PlantEntity plant) {
        if (plant == null || !hasCalendarPermission()) return;

        executor.execute(() -> {
            deleteEventForPlantSync(plant);
        });
    }

    /**
     * Synchronously removes associated calendar events for a plant on the calling thread.
     *
     * @param plant The plant to delete events for
     * @return Number of events deleted
     */
    public int deleteEventForPlantSync(PlantEntity plant) {
        if (plant == null || !hasCalendarPermission()) return 0;
        return CalendarIntegrationHelper.deletePlantCalendarEvents(appContext, plant);
    }
}
