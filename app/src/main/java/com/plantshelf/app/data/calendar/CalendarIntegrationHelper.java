package com.plantshelf.app.data.calendar;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.Toast;

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

/**
 * Pure Java helper for integrating plant care schedules with Android's system calendar
 * and exporting RFC 5545 iCalendar (.ics) files.
 */
public class CalendarIntegrationHelper {

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
        desc += "\nСтворено у додатку Plantshelf";

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

        context.startActivity(Intent.createChooser(shareIntent, "Експортувати розклад догляду"));
        return icsFile;
    }

    private static String escapeIcs(String text) {
        if (text == null) return "";
        return text.replace(",", "\\,").replace(";", "\\;");
    }
}
