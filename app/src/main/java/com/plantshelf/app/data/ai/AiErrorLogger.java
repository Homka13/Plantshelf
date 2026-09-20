package com.plantshelf.app.data.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local file-based error logging and diagnosis system for Google Gemini API interactions.
 * Stores error traces locally on device (0 telemetry, completely private and offline).
 * Provides clear, user-friendly Ukrainian feedback for all API failure conditions.
 */
public class AiErrorLogger {

    private static final String TAG = "AiErrorLogger";
    public static final String LOG_FILE_NAME = "gemini_error_log.txt";
    private static final long MAX_LOG_SIZE_BYTES = 64 * 1024; // 64 KB limit for local logs

    /**
     * Translates any error, exception or HTTP failure into clear, actionable Ukrainian guidance.
     */
    public static String getUserFriendlyMessage(@Nullable Throwable error) {
        if (error == null) {
            return "Сталася невідома помилка під час роботи з Gemini AI. Спробуйте ще раз.";
        }

        Throwable root = getRootCause(error);
        String msg = error.getMessage() != null ? error.getMessage() : "";
        String rootMsg = root.getMessage() != null ? root.getMessage() : "";
        String combined = (msg + " " + rootMsg).toLowerCase(Locale.ROOT);

        // Network / connectivity errors
        if (root instanceof UnknownHostException || root instanceof ConnectException || root instanceof SocketTimeoutException
                || combined.contains("unable to resolve host") || combined.contains("timeout") || combined.contains("connection refused")) {
            return "🌐 Відсутній зв'язок із серверами Gemini або перевищено час очікування відповіді. Будь ласка, перевірте з'єднання з інтернетом.";
        }

        // Missing API Key
        if (combined.contains("не встановлено") || combined.contains("missing_key") || combined.contains("api ключ") || combined.contains("api-ключ")) {
            return "🔑 API-ключ Gemini не налаштовано. Будь ласка, вкажіть ваш ключ у налаштуваннях додатка (отримати безкоштовно на aistudio.google.com).";
        }

        // 401 / 403 Invalid or inactive API key
        if (combined.contains("403") || combined.contains("401") || combined.contains("api key not valid")
                || combined.contains("permission_denied") || combined.contains("api_key_invalid")) {
            return "🔑 Недійсний або заблокований API-ключ Gemini. Будь ласка, перевірте та оновіть ключ у налаштуваннях додатка.";
        }

        // 404 Model not found / deprecated
        if (combined.contains("404") || combined.contains("not found") || combined.contains("is not found for api version")) {
            return "⚠️ Обрана модель Gemini недоступна або застаріла. Оберіть актуальну модель (наприклад, 'gemini-3.8-flash' або 'gemini-flash-latest') у налаштуваннях AI.";
        }

        // 429 Quota / Rate limit
        if (combined.contains("429") || combined.contains("resource_exhausted") || combined.contains("quota exceeded") || combined.contains("rate limit")) {
            return "⏳ Перевищено ліміт запитів до Gemini API. Зачекайте 1-2 хвилини перед наступним запитом або перевірте квоту у вашому Google AI Studio акаунті.";
        }

        // 500 / 503 Server errors
        if (combined.contains("500") || combined.contains("502") || combined.contains("503") || combined.contains("504")
                || combined.contains("internal server error") || combined.contains("service unavailable")) {
            return "☁️ Сервери Google Gemini тимчасово перевантажені або проводять оновлення. Спробуйте повторити запит через кілька хвилин.";
        }

        // Safety blocks
        if (combined.contains("safety") || combined.contains("recitation") || combined.contains("blocklist") || combined.contains("заблоковано")) {
            return "🛡️ Запит або фото заблоковано фільтрами безпеки вмісту Gemini. Спробуйте зробити інше фото рослини без сторонніх предметів.";
        }

        // Image loading / decoding issues
        if (combined.contains("фото") || combined.contains("image") || combined.contains("bitmap") || combined.contains("зображення")) {
            return "📷 Не вдалося завантажити або оптимізувати фотографію рослини. Спробуйте зробити новий знімок або обрати інше зображення.";
        }

        // Parsing issues
        if (combined.contains("json") || combined.contains("parse") || combined.contains("структуру")) {
            return "🌱 Штучний інтелект не зміг чітко розпізнати рослину або скласти паспорт. Спробуйте сфотографувати рослину ближче при кращому освітленні.";
        }

        // If error message already contains friendly Ukrainian text, return it
        if (msg.matches(".*[а-яА-ЯіІїЇєЄґҐ].*") && msg.length() > 15) {
            return msg;
        }

        return "Виникла помилка під час звернення до Gemini: " + (msg.isEmpty() ? root.getClass().getSimpleName() : msg) + ". Спробуйте ще раз.";
    }

    private static Throwable getRootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * Logs an error to the local file.
     */
    public static synchronized void log(Context context, String action, Throwable error, String extraInfo) {
        if (context == null) return;
        try {
            File logFile = getLogFile(context);
            checkAndRotateLogFile(logFile);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String friendlyUkrainian = getUserFriendlyMessage(error);

            StringBuilder entry = new StringBuilder();
            entry.append("========================================\n");
            entry.append("[").append(timestamp).append("] Дія: ").append(action).append("\n");
            if (extraInfo != null && !extraInfo.isEmpty()) {
                entry.append("Параметри: ").append(extraInfo).append("\n");
            }
            if (error != null) {
                entry.append("Тип помилки: ").append(error.getClass().getName()).append("\n");
                entry.append("Повідомлення: ").append(error.getMessage()).append("\n");
                if (error.getCause() != null) {
                    entry.append("Першопричина: ").append(error.getCause().toString()).append("\n");
                }
            }
            entry.append("Зрозуміле пояснення: ").append(friendlyUkrainian).append("\n");
            entry.append("========================================\n\n");

            try (FileOutputStream fos = new FileOutputStream(logFile, true);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(entry.toString());
                writer.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write local AI error log", e);
        }
    }

    /**
     * Dedicated logging for HTTP API failures.
     */
    public static synchronized void logHttpError(Context context, String action, String model, int httpCode, String errorBody, Throwable error) {
        String details = "model=" + model + ", httpCode=" + httpCode;
        if (errorBody != null && !errorBody.trim().isEmpty()) {
            String snippet = errorBody.length() > 300 ? errorBody.substring(0, 300) + "..." : errorBody;
            details += ", body=" + snippet;
        }
        log(context, action, error, details);
    }

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    private static void checkAndRotateLogFile(File logFile) {
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
            try {
                // Keep the second half of the file to preserve latest entries
                byte[] allBytes = java.nio.file.Files.readAllBytes(logFile.toPath());
                int keepOffset = allBytes.length / 2;
                // Find next entry start
                while (keepOffset < allBytes.length && allBytes[keepOffset] != '=') {
                    keepOffset++;
                }
                if (keepOffset < allBytes.length) {
                    byte[] kept = new byte[allBytes.length - keepOffset];
                    System.arraycopy(allBytes, keepOffset, kept, 0, kept.length);
                    try (FileOutputStream fos = new FileOutputStream(logFile, false)) {
                        fos.write(kept);
                    }
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    logFile.delete();
                }
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
        }
    }

    /**
     * Reads all logged entries from the local file.
     */
    public static synchronized String getRecentLogs(Context context) {
        if (context == null) return "";
        try {
            File logFile = getLogFile(context);
            if (!logFile.exists() || logFile.length() == 0) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read local AI error log", e);
            return "";
        }
    }

    public static synchronized boolean hasLogs(Context context) {
        if (context == null) return false;
        File logFile = getLogFile(context);
        return logFile.exists() && logFile.length() > 0;
    }

    /**
     * Clears all entries in the local log file.
     */
    public static synchronized void clearLogs(Context context) {
        if (context == null) return;
        try {
            File logFile = getLogFile(context);
            if (logFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear local AI error log", e);
        }
    }

    /**
     * Shows a user-friendly Material 3 error dialog in Ukrainian with option to retry
     * and inspect the error log.
     */
    public static void showErrorFeedbackDialog(Context context, @Nullable String title, Throwable error, @Nullable Runnable onRetry) {
        if (context == null) return;
        String friendlyMessage = getUserFriendlyMessage(error);
        String dialogTitle = (title != null && !title.isEmpty()) ? title : context.getString(R.string.ai_error_dialog_title);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(dialogTitle)
                .setMessage(friendlyMessage)
                .setNegativeButton(R.string.btn_cancel, null)
                .setNeutralButton(R.string.ai_error_action_details, (dialog, which) -> showLogsDialog(context));

        if (onRetry != null) {
            builder.setPositiveButton(R.string.ai_error_action_retry, (dialog, which) -> onRetry.run());
        }

        builder.show();
    }

    /**
     * Opens a dialog displaying the local AI error log with copy and clear actions.
     */
    public static void showLogsDialog(Context context) {
        if (context == null) return;
        String logs = getRecentLogs(context);

        ScrollView scrollView = new ScrollView(context);
        TextView tvLogs = new TextView(context);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        tvLogs.setPadding(pad, pad, pad, pad);
        tvLogs.setTextIsSelectable(true);
        tvLogs.setText(logs.isEmpty() ? context.getString(R.string.ai_logs_empty) : logs);
        scrollView.addView(tvLogs);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.ai_logs_title)
                .setView(scrollView)
                .setPositiveButton("OK", null)
                .setNeutralButton(R.string.ai_logs_copy, (dialog, which) -> {
                    if (logs.isEmpty()) {
                        Toast.makeText(context, R.string.ai_logs_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText("Gemini AI Error Log", logs);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(context, R.string.ai_logs_copied, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.ai_logs_clear, (dialog, which) -> {
                    clearLogs(context);
                    Toast.makeText(context, R.string.ai_logs_cleared, Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
