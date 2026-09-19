package com.plantshelf.app.data.ai;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local file-based error logger for Gemini API and AI operations.
 * Operates purely locally on device (0 telemetry, zero remote tracking).
 */
public class AiErrorLogger {

    private static final String TAG = "AiErrorLogger";
    private static final String LOG_FILE_NAME = "gemini_error_log.txt";
    private static final long MAX_LOG_SIZE_BYTES = 64 * 1024; // 64 KB limit

    public static synchronized void log(Context context, String action, Throwable error, String extraInfo) {
        if (context == null) return;
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);

            // Rotate / clear if exceeds max size
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                // Delete old log to prevent growth
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            StringBuilder entry = new StringBuilder();
            entry.append("[").append(timestamp).append("] Action: ").append(action).append("\n");
            if (extraInfo != null && !extraInfo.isEmpty()) {
                entry.append("Details: ").append(extraInfo).append("\n");
            }
            if (error != null) {
                entry.append("Error: ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append("\n");
            }
            entry.append("----------------------------------------\n");

            try (FileOutputStream fos = new FileOutputStream(logFile, true);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(entry.toString());
                writer.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write local AI error log", e);
        }
    }

    public static synchronized String getRecentLogs(Context context) {
        if (context == null) return "";
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
            if (!logFile.exists()) return "";
            byte[] bytes = java.nio.file.Files.readAllBytes(logFile.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read local AI error log", e);
            return "";
        }
    }

    public static synchronized void clearLogs(Context context) {
        if (context == null) return;
        try {
            File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
            if (logFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear local AI error log", e);
        }
    }
}
