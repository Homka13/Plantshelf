package com.plantshelf.app.data.calendar;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.plantshelf.app.data.entity.PlantEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure Java helper for integrating plant care schedules with Android's system calendar
 * and exporting RFC 5545 iCalendar (.ics) files.
 */
public class CalendarIntegrationHelper {

    private static final String TAG = "CalendarHelper";
    public static final String ID_TAG_PREFIX = "Plantshelf_ID:";

    public static boolean hasCalendarPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static Long findPrimaryCalendarId(Context context) {
        if (!hasCalendarPermission(context)) return null;
        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.NAME
        };
        String selection = CalendarContract.Calendars.VISIBLE + " = 1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= "
                + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                null,
                CalendarContract.Calendars.IS_PRIMARY + " DESC, " + CalendarContract.Calendars._ID + " ASC"
        )) {
            if (cursor != null) {
                Long primaryOrFirstId = null;
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID));
                    int colDisp = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
                    int colName = cursor.getColumnIndex(CalendarContract.Calendars.NAME);
                    String displayName = colDisp != -1 ? cursor.getString(colDisp) : null;
                    String name = colName != -1 ? cursor.getString(colName) : null;

                    // Priority 1: A dedicated "Plantshelf" calendar if present
                    if ("Plantshelf".equalsIgnoreCase(displayName) || "Plantshelf".equalsIgnoreCase(name)) {
                        return id;
                    }
                    if (primaryOrFirstId == null) {
                        primaryOrFirstId = id;
                    }
                }
                return primaryOrFirstId;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not query writable calendars", e);
        }
        return null;
    }

    /**
     * Deletes the calendar event associated with this plant if event ID is known.
     */
    public static boolean deleteCalendarEvent(Context context, String eventId) {
        if (eventId == null || eventId.trim().isEmpty() || !hasCalendarPermission(context)) {
            return false;
        }
        try {
            long id = Long.parseLong(eventId);
            Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
            int rows = context.getContentResolver().delete(eventUri, null, null);
            return rows > 0;
        } catch (Exception e) {
            Log.w(TAG, "Could not delete calendar event " + eventId, e);
            return false;
        }
    }

    /**
     * Removes associated calendar events for a deleted plant:
     * 1. Automatically removes the event using the stored calendarEventId.
     * 2. Runs a fallback lookup using 'Plantshelf_ID:' tags in event descriptions to ensure
     *    any orphaned or untracked events for this plant are also deleted.
     *
     * @param context Context
     * @param plant The plant being removed
     * @return Total number of calendar events deleted
     */
    public static int deletePlantCalendarEvents(Context context, PlantEntity plant) {
        if (context == null || plant == null || !hasCalendarPermission(context)) {
            return 0;
        }

        int deletedCount = 0;
        String calendarEventId = plant.getCalendarEventId();

        // 1. Direct deletion using stored calendarEventId
        if (calendarEventId != null && !calendarEventId.trim().isEmpty()) {
            try {
                long eventId = Long.parseLong(calendarEventId.trim());
                Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                int rows = context.getContentResolver().delete(eventUri, null, null);
                if (rows > 0) {
                    deletedCount += rows;
                    Log.d(TAG, "Deleted calendar event with stored calendarEventId: " + calendarEventId + " (" + rows + " row(s))");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed deleting event with calendarEventId: " + calendarEventId, e);
            }
        }

        // 2. Fallback lookup using 'Plantshelf_ID:' tags in event descriptions restricted to CALENDAR_ID
        Long calendarId = findPrimaryCalendarId(context);
        if (calendarId != null) {
            try {
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

                int fallbackRows = context.getContentResolver().delete(
                        CalendarContract.Events.CONTENT_URI,
                        selection,
                        selectionArgs
                );
                if (fallbackRows > 0) {
                    deletedCount += fallbackRows;
                    Log.d(TAG, "Fallback deleted " + fallbackRows + " calendar event(s) using Plantshelf_ID tag for plant " + plantId);
                }
            } catch (Exception e) {
                Log.w(TAG, "Fallback calendar event deletion failed for plant " + plant.getId(), e);
            }
        }

        return deletedCount;
    }

    /**
     * Efficiently bulk synchronizes all plant events in the system calendar using
     * ContentProviderOperation.applyBatch().
     *
     * @param context Application context
     * @param plants List of plants to synchronize
     * @return count of synchronized plants
     */
    public static int bulkSyncAllPlants(Context context, List<PlantEntity> plants) throws Exception {
        return CalendarSyncManager.getInstance(context).syncAllPlantsBatchSync(plants);
    }

    /**
     * Updates an existing calendar event schedule when plant watering/fertilizing frequency changes.
     * Recalculates DTSTART from the plant's next care schedule and removes static RRULE to stay in sync.
     */
    public static boolean updateCalendarEventSchedule(Context context, PlantEntity plant) {
        if (plant == null || plant.getCalendarEventId() == null || !hasCalendarPermission(context)) {
            return false;
        }
        try {
            long eventId = Long.parseLong(plant.getCalendarEventId());
            int interval = plant.getIntervalDays() > 0 ? plant.getIntervalDays() : 7;
            Calendar startTime = Calendar.getInstance();
            startTime.set(Calendar.HOUR_OF_DAY, 9);
            startTime.set(Calendar.MINUTE, 0);
            startTime.set(Calendar.SECOND, 0);

            int daysLeft = plant.getDaysUntilWatering();
            if (daysLeft > 0) {
                startTime.add(Calendar.DAY_OF_YEAR, daysLeft);
            }

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.TITLE, "🌱 Полив: " + plant.getName());
            values.put(CalendarContract.Events.DTSTART, startTime.getTimeInMillis());
            values.put(CalendarContract.Events.DTEND, startTime.getTimeInMillis() + (30 * 60 * 1000));
            values.putNull(CalendarContract.Events.RRULE);

            String desc = "Рослина: " + plant.getName() + "\n";
            if (!plant.getVariety().isEmpty()) {
                desc += "Сорт: " + plant.getVariety() + "\n";
            }
            desc += "Регулярний полив (кожні " + interval + " дн. влітку, " + plant.getIntervalDaysWinter() + " дн. взимку)\n";
            if (plant.getSoil() != null && !plant.getSoil().isEmpty()) {
                desc += "Ґрунт: " + plant.getSoil() + "\n";
            }
            if (plant.getEffectiveRecommendedFertilizers() != null && !plant.getEffectiveRecommendedFertilizers().isEmpty()) {
                desc += "Добрива: " + plant.getEffectiveRecommendedFertilizers() + "\n";
            }
            desc += "\n" + ID_TAG_PREFIX + plant.getId() + "\nОновлено у додатку Plantshelf\n";
            values.put(CalendarContract.Events.DESCRIPTION, desc);

            Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
            int rows = context.getContentResolver().update(eventUri, values, null, null);
            return rows > 0;
        } catch (Exception e) {
            Log.w(TAG, "Could not update calendar event", e);
            return false;
        }
    }

    /**
     * Directly inserts an event into the primary calendar if permission is granted.
     * Returns the created event ID or null.
     */
    public static String insertCalendarEventDirect(Context context, PlantEntity plant, String careType) {
        if (plant == null || !hasCalendarPermission(context)) return null;
        Long calendarId = findPrimaryCalendarId(context);
        if (calendarId == null) return null;

        try {
            Calendar startTime = Calendar.getInstance();
            startTime.set(Calendar.HOUR_OF_DAY, 9);
            startTime.set(Calendar.MINUTE, 0);
            startTime.set(Calendar.SECOND, 0);

            int interval = plant.getIntervalDays() > 0 ? plant.getIntervalDays() : 7;
            String title = "🌱 Полив: " + plant.getName();
            String desc = "Рослина: " + plant.getName() + "\n";

            if ("fert".equalsIgnoreCase(careType)) {
                title = "🧪 Добрива: " + plant.getName();
                interval = plant.getEffectiveFertilizeIntervalSummerDays();
                desc += "Підживлення добривами кожні " + interval + " дн.\n";
            } else if ("mist".equalsIgnoreCase(careType)) {
                title = "💧 Обприскування: " + plant.getName();
                interval = plant.getMistIntervalDays() > 0 ? plant.getMistIntervalDays() : 3;
                desc += "Обприскування кожні " + interval + " дн.\n";
            } else {
                int daysLeft = plant.getDaysUntilWatering();
                if (daysLeft > 0) {
                    startTime.add(Calendar.DAY_OF_YEAR, daysLeft);
                }
                desc += "Регулярний полив (кожні " + interval + " дн. влітку, " + plant.getIntervalDaysWinter() + " дн. взимку)\n";
            }

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
            values.put(CalendarContract.Events.TITLE, title);
            values.put(CalendarContract.Events.DESCRIPTION, desc + "\n" + ID_TAG_PREFIX + plant.getId() + "\nСтворено у додатку Plantshelf\n");
            values.put(CalendarContract.Events.DTSTART, startTime.getTimeInMillis());
            values.put(CalendarContract.Events.DTEND, startTime.getTimeInMillis() + (30 * 60 * 1000));
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE);

            Uri uri = context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            if (uri != null) {
                long eventId = ContentUris.parseId(uri);
                try {
                    ContentValues reminderValues = new ContentValues();
                    reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventId);
                    reminderValues.put(CalendarContract.Reminders.MINUTES, 15);
                    reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                    context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);
                } catch (Exception ignored) {}
                return String.valueOf(eventId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed direct calendar insert", e);
        }
        return null;
    }

    /**
     * Opens the native Android/Google Calendar app to add a recurring care event.
     */
    public static void addPlantCareToSystemCalendar(Context context, PlantEntity plant, String careType) {
        if (plant == null) return;

        Calendar startTime = Calendar.getInstance();
        startTime.set(Calendar.HOUR_OF_DAY, 9);
        startTime.set(Calendar.MINUTE, 0);
        startTime.set(Calendar.SECOND, 0);

        int interval = plant.getIntervalDays();
        String title;
        String desc = "Рослина: " + plant.getName() + "\n";

        if (!plant.getVariety().isEmpty()) {
            desc += "Сорт: " + plant.getVariety() + "\n";
        }
        if (!plant.getLatin().isEmpty()) {
            desc += "Латина: " + plant.getLatin() + "\n";
        }

        if ("fert".equalsIgnoreCase(careType)) {
            title = "🧪 Добрива: " + plant.getName();
            interval = plant.getFertIntervalDays() > 0 ? plant.getFertIntervalDays() : 14;
            desc += "Регулярне внесення добрив кожні " + interval + " дн.\n";
        } else if ("mist".equalsIgnoreCase(careType)) {
            title = "💧 Обприскування: " + plant.getName();
            interval = plant.getMistIntervalDays() > 0 ? plant.getMistIntervalDays() : 3;
            desc += "Обприскування листя кожні " + interval + " дн.\n";
        } else {
            title = "🌱 Полив: " + plant.getName();
            int daysLeft = plant.getDaysUntilWatering();
            if (daysLeft > 0) {
                startTime.add(Calendar.DAY_OF_YEAR, daysLeft);
            }
            desc += "Регулярний полив (кожні " + interval + " дн. влітку, " + plant.getIntervalDaysWinter() + " дн. взимку)\n";
        }

        if (plant.getSoil() != null && !plant.getSoil().isEmpty()) {
            desc += "Ґрунт: " + plant.getSoil() + "\n";
        }
        if (plant.getWarning() != null && !plant.getWarning().isEmpty()) {
            desc += "⚠️ " + plant.getWarning() + "\n";
        }
        desc += "\n" + ID_TAG_PREFIX + plant.getId() + "\nСтворено у додатку Plantshelf";

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime.getTimeInMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startTime.getTimeInMillis() + (30 * 60 * 1000)) // 30 mins
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.DESCRIPTION, desc)
                .putExtra(CalendarContract.Events.RRULE, "FREQ=DAILY;INTERVAL=" + interval)
                .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Не знайдено календар на пристрої: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Builds RFC 5545 iCalendar format string for a list of plants.
     */
    public static String generateIcsContent(List<PlantEntity> plants) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Plantshelf//Plant Care Schedule//UK\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");

        SimpleDateFormat icsDateFormat = new SimpleDateFormat("yyyyMMdd'T'090000", Locale.US);
        SimpleDateFormat dtStampFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        String dtStamp = dtStampFormat.format(new Date());

        for (PlantEntity plant : plants) {
            int interval = plant.getIntervalDays() > 0 ? plant.getIntervalDays() : 7;
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, Math.max(0, plant.getDaysUntilWatering()));
            String dtStart = icsDateFormat.format(cal.getTime());

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:plantshelf-water-").append(plant.getId()).append("@plantshelf.app\r\n");
            sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
            sb.append("DTSTART:").append(dtStart).append("\r\n");
            sb.append("DURATION:PT15M\r\n");
            sb.append("SUMMARY:🌱 Полив: ").append(escapeIcs(plant.getName())).append("\r\n");

            StringBuilder desc = new StringBuilder();
            desc.append("Рослина: ").append(plant.getName());
            if (!plant.getVariety().isEmpty()) desc.append(" (").append(plant.getVariety()).append(")");
            desc.append("\\nІнтервал: кожні ").append(interval).append(" дн.");
            if (plant.getSoil() != null && !plant.getSoil().isEmpty()) {
                desc.append("\\nҐрунт: ").append(plant.getSoil());
            }
            sb.append("DESCRIPTION:").append(escapeIcs(desc.toString())).append("\r\n");
            sb.append("RRULE:FREQ=DAILY;INTERVAL=").append(interval).append("\r\n");
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /**
     * Exports all plants to an .ics file and shares it.
     */
    public static File exportAndShareIcs(Context context, List<PlantEntity> plants) throws Exception {
        File cacheDir = new File(context.getCacheDir(), "calendar");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        File icsFile = new File(cacheDir, "plantshelf_schedule.ics");
        String content = generateIcsContent(plants);

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(icsFile), StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
        }

        Uri fileUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                icsFile
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/calendar");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Графік догляду за рослинами Plantshelf");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(shareIntent, "Експортувати розклад догляду");
        if (!(context instanceof android.app.Activity)) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(chooser);
        return icsFile;
    }

    private static String escapeIcs(String text) {
        if (text == null) return "";
        return text.replace(",", "\\,").replace(";", "\\;");
    }
}
