package com.plantshelf.app.updater;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.plantshelf.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles checking for updates from GitHub Releases and triggering in-app APK installation.
 * 100% Pure Java, offline-resilient.
 */
public class GitHubUpdateManager {

    private static final String GITHUB_OWNER = "Homka13";
    private static final String GITHUB_REPO  = "Plantshelf";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private static final String PREFS_UPDATER = "plantshelf_updater_prefs";
    public static final String KEY_AUTO_CHECK_UPDATES = "auto_check_updates";
    public static final String KEY_LAST_CHECK_TS      = "last_check_timestamp";
    public static final long   CHECK_INTERVAL_MS      = 24 * 60 * 60 * 1000L; // 24 hours

    /** HTTP header name reused across requests. */
    private static final String HEADER_USER_AGENT  = "User-Agent";
    /** HTTP User-Agent value sent to GitHub / S3. */
    private static final String USER_AGENT_VALUE   = "Plantshelf-Android-App";
    /** GitHub API Accept header. */
    private static final String HEADER_ACCEPT_VALUE = "application/vnd.github.v3+json";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Handler mainHandler;

    /** Utility class — do not instantiate. */
    private GitHubUpdateManager() {}

    // ─────────────────────────────────────────────────────── prefs helpers ──

    public static boolean isAutoCheckEnabled(Context context) {
        return context.getSharedPreferences(PREFS_UPDATER, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_CHECK_UPDATES, true);
    }

    public static void setAutoCheckEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_UPDATER, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_CHECK_UPDATES, enabled)
                .apply();
    }

    public static long getLastCheckTimestamp(Context context) {
        return context.getSharedPreferences(PREFS_UPDATER, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CHECK_TS, 0L);
    }

    public static void recordCheckTimestamp(Context context) {
        context.getSharedPreferences(PREFS_UPDATER, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_CHECK_TS, System.currentTimeMillis())
                .apply();
    }

    public static boolean shouldPerformPeriodicCheck(Context context) {
        if (!isAutoCheckEnabled(context)) return false;
        long last = getLastCheckTimestamp(context);
        return (System.currentTimeMillis() - last) >= CHECK_INTERVAL_MS;
    }

    private static synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        return mainHandler;
    }

    // ──────────────────────────────────────────────── public check methods ──

    /**
     * Checks for updates with optional user-visible feedback.
     * If {@code isUserInitiated}, shows a Toast while checking and reports "already latest" state.
     */
    public static void checkForUpdates(Context context, boolean isUserInitiated) {
        recordCheckTimestamp(context);
        if (isUserInitiated) {
            Toast.makeText(context, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }
        executor.execute(() -> {
            ReleaseInfo info = fetchLatestRelease(context, isUserInitiated);
            if (info == null) return;

            getMainHandler().post(() -> {
                String currentVersion = getCurrentAppVersion(context);
                if (isNewerVersion(info.tagName, currentVersion)) {
                    showUpdateDialog(context, info.tagName, info.body, info.apkUrl);
                } else if (isUserInitiated) {
                    Toast.makeText(context,
                            context.getString(R.string.update_already_latest, currentVersion),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * Checks for updates silently on app launch.
     * If a newer version exists — shows a Snackbar with an "Update" action button.
     * Does NOT show any message when already up to date or on network error.
     *
     * @param anchorView The view to anchor the Snackbar to (e.g. the root layout or FAB).
     */
    public static void checkForUpdatesOnLaunch(Context context, View anchorView) {
        recordCheckTimestamp(context);
        executor.execute(() -> {
            ReleaseInfo info = fetchLatestReleaseSilent();
            if (info == null) return;

            getMainHandler().post(() -> {
                String currentVersion = getCurrentAppVersion(context);
                if (!isNewerVersion(info.tagName, currentVersion)) return;

                Snackbar.make(anchorView,
                                context.getString(R.string.update_snackbar_message, info.tagName),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.update_snackbar_action, v ->
                                showUpdateDialog(context, info.tagName, info.body, info.apkUrl))
                        .show();
            });
        });
    }

    // ─────────────────────────────────────────────── network fetch helpers ──

    /** Simple container for a parsed GitHub release. */
    private static final class ReleaseInfo {
        final String tagName;
        final String body;
        final String apkUrl;

        ReleaseInfo(String tagName, String body, String apkUrl) {
            this.tagName = tagName;
            this.body    = body;
            this.apkUrl  = apkUrl;
        }
    }

    /**
     * Fetches the latest GitHub release; posts user-visible error Toasts on failure.
     * Returns {@code null} if the release could not be retrieved.
     */
    private static ReleaseInfo fetchLatestRelease(Context context, boolean isUserInitiated) {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(API_URL);
            int responseCode = conn.getResponseCode();

            if (responseCode == 404) {
                if (isUserInitiated) {
                    getMainHandler().post(() ->
                            Toast.makeText(context, "На GitHub поки що немає опублікованих релізів.", Toast.LENGTH_SHORT).show());
                }
                return null;
            }
            if (responseCode != 200) {
                if (isUserInitiated) {
                    getMainHandler().post(() ->
                            Toast.makeText(context, "Помилка перевірки оновлень: HTTP " + responseCode, Toast.LENGTH_SHORT).show());
                }
                return null;
            }
            return parseRelease(readResponse(conn));
        } catch (Exception e) {
            if (isUserInitiated) {
                getMainHandler().post(() ->
                        Toast.makeText(context, "Не вдалося з'єднатися з GitHub: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Fetches the latest GitHub release silently (no user-visible feedback on failure).
     * Returns {@code null} if the release could not be retrieved.
     */
    private static ReleaseInfo fetchLatestReleaseSilent() {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(API_URL);
            if (conn.getResponseCode() != 200) return null;
            return parseRelease(readResponse(conn));
        } catch (Exception ignored) {
            // Silent — don't bother the user with network errors on launch
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Opens and configures a GET connection with common headers. */
    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new IOException("Invalid URL: " + urlStr, e);
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", HEADER_ACCEPT_VALUE);
        conn.setRequestProperty(HEADER_USER_AGENT, USER_AGENT_VALUE);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        return conn;
    }

    /** Reads the full response body from a connection as a String. */
    private static String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    /** Parses a GitHub releases JSON payload into a {@link ReleaseInfo}. */
    private static ReleaseInfo parseRelease(String json) throws Exception {
        JSONObject release = new JSONObject(json);
        String tagName = release.optString("tag_name", "");
        String body    = release.optString("body", "");
        String apkUrl  = findApkUrl(release.optJSONArray("assets"));
        return new ReleaseInfo(tagName, body, apkUrl);
    }

    /** Scans the assets array for the first {@code .apk} download URL. */
    private static String findApkUrl(JSONArray assets) throws Exception {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset.optString("name", "").endsWith(".apk")) {
                return asset.optString("browser_download_url", null);
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────────── UI helpers ─────────

    private static void showUpdateDialog(Context context, String tagName, String body, String apkUrl) {
        String msg = (body != null && !body.trim().isEmpty())
                ? body
                : "Нове оновлення Plantshelf " + tagName + " доступне на GitHub.";

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_dialog_title, tagName))
                .setMessage(msg)
                .setNegativeButton(R.string.btn_cancel, null);

        if (apkUrl != null && !apkUrl.isEmpty()) {
            builder.setPositiveButton(R.string.update_dialog_btn_download,
                    (dialog, which) -> downloadAndInstallApk(context, apkUrl, tagName));
        } else {
            builder.setPositiveButton("Відкрити реліз",
                    (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases"))));
        }

        builder.show();
    }

    private static void downloadAndInstallApk(Context context, String apkUrl, String tagName) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView tvMsg = new TextView(context);
        tvMsg.setText("Завантаження APK з GitHub...");
        tvMsg.setTextSize(14f);
        layout.addView(tvMsg);

        LinearProgressIndicator progressIndicator = new LinearProgressIndicator(context);
        progressIndicator.setIndeterminate(false);
        progressIndicator.setMax(100);
        progressIndicator.setProgress(0);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = (int) (12 * context.getResources().getDisplayMetrics().density);
        progressIndicator.setLayoutParams(lp);
        layout.addView(progressIndicator);

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_downloading))
                .setView(layout)
                .setCancelable(false)
                .show();

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = openDownloadConnection(apkUrl);
                int fileLength = conn.getContentLength();

                File apkFile = prepareApkFile(context, tagName);
                writeApkFromStream(conn.getInputStream(), apkFile, fileLength, progressIndicator, tvMsg);

                getMainHandler().post(() -> {
                    progressDialog.dismiss();
                    installApk(context, apkFile);
                });
            } catch (Exception e) {
                getMainHandler().post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(context,
                            context.getString(R.string.update_download_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /** Opens a connection for APK download, following redirects manually if needed. */
    private static HttpURLConnection openDownloadConnection(String apkUrl) throws IOException {
        URL url;
        try {
            url = new URL(apkUrl);
        } catch (MalformedURLException e) {
            throw new IOException("Invalid APK URL: " + apkUrl, e);
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty(HEADER_USER_AGENT, USER_AGENT_VALUE);
        conn.connect();

        // Handle HTTP redirects (GitHub releases redirect to AWS S3)
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(newUrl).openConnection();
            conn.setRequestProperty(HEADER_USER_AGENT, USER_AGENT_VALUE);
            conn.connect();
        }
        return conn;
    }

    /** Prepares the target APK file inside the app's updates cache directory. */
    private static File prepareApkFile(Context context, String tagName) {
        File cacheDir   = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
        File updatesDir = new File(cacheDir, "updates");
        if (!updatesDir.exists()) updatesDir.mkdirs();
        return new File(updatesDir, "Plantshelf-" + tagName + ".apk");
    }

    /** Streams download bytes to {@code dest}, posting progress updates to the dialog. */
    private static void writeApkFromStream(InputStream input, File dest, int fileLength,
                                           LinearProgressIndicator progressIndicator,
                                           TextView statusText) throws Exception {
        try (FileOutputStream output = new FileOutputStream(dest)) {
            byte[] data  = new byte[8192];
            long   total = 0;
            int    count;
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    long downloadedMb = total / (1024 * 1024);
                    long totalMb = fileLength / (1024 * 1024);
                    getMainHandler().post(() -> {
                        progressIndicator.setProgressCompat(progress, true);
                        if (totalMb > 0) {
                            statusText.setText(String.format(Locale.getDefault(), "Завантаження: %d%% (%d / %d MB)", progress, downloadedMb, totalMb));
                        } else {
                            statusText.setText(String.format(Locale.getDefault(), "Завантаження: %d%%", progress));
                        }
                    });
                }
                output.write(data, 0, count);
            }
            output.flush();
        }
    }

    private static void installApk(Context context, File apkFile) {
        if (!apkFile.exists()) return;

        // On Android 8.0+, verify unknown sources install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(context, "Дозвольте встановлення з цього джерела для завершення оновлення", Toast.LENGTH_LONG).show();
            context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + context.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile);
            context.startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(context, "Помилка запуску інсталятора: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────── version comparison ──────

    /**
     * Compares remote tag (e.g. "v1.0.1") with current BuildConfig version (e.g. "1.0.0").
     */
    public static boolean isNewerVersion(String remoteTag, String currentVersion) {
        if (remoteTag == null || remoteTag.isEmpty()
                || currentVersion == null || currentVersion.isEmpty()) {
            return false;
        }

        String[] remoteParts  = remoteTag.replaceAll("^[vV]", "").trim().split("[.-]");
        String[] currentParts = currentVersion.replaceAll("^[vV]", "").trim().split("[.-]");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int r = parsePart(remoteParts, i);
            int c = parsePart(currentParts, i);
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    /** Safely parses an integer segment from a version parts array. */
    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            // Non-numeric segment (e.g. "beta") treated as 0
            return 0;
        }
    }

    public static String getCurrentAppVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}
