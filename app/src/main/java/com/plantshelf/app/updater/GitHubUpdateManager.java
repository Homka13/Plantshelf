package com.plantshelf.app.updater;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles checking for updates from GitHub Releases and triggering in-app APK installation.
 * 100% Pure Java, offline-resilient.
 */
public class GitHubUpdateManager {

    private static final String GITHUB_OWNER = "Homka13";
    private static final String GITHUB_REPO = "Plantshelf";
    private static final String API_URL = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private static final String PREFS_UPDATER = "plantshelf_updater_prefs";
    public static final String KEY_AUTO_CHECK_UPDATES = "auto_check_updates";
    public static final String KEY_LAST_CHECK_TS = "last_check_timestamp";
    public static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Handler mainHandler;

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
        if (!isAutoCheckEnabled(context)) {
            return false;
        }
        long last = getLastCheckTimestamp(context);
        return (System.currentTimeMillis() - last) >= CHECK_INTERVAL_MS;
    }

    private static synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        return mainHandler;
    }

    public static void checkForUpdates(Context context, boolean isUserInitiated) {
        recordCheckTimestamp(context);
        if (isUserInitiated) {
            Toast.makeText(context, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Plantshelf-Android-App");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 404) {
                    // No releases published yet
                    if (isUserInitiated) {
                        getMainHandler().post(() -> Toast.makeText(context, "На GitHub поки що немає опублікованих релізів.", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                if (responseCode != 200) {
                    if (isUserInitiated) {
                        getMainHandler().post(() -> Toast.makeText(context, "Помилка перевірки оновлень: HTTP " + responseCode, Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject releaseJson = new JSONObject(response.toString());
                String tagName = releaseJson.optString("tag_name", "");
                String releaseTitle = releaseJson.optString("name", tagName);
                String releaseBody = releaseJson.optString("body", "");

                // Find APK asset
                String apkDownloadUrl = null;
                JSONArray assets = releaseJson.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }

                final String finalApkUrl = apkDownloadUrl;
                final String finalTag = tagName;

                getMainHandler().post(() -> {
                    String currentVersion = getCurrentAppVersion(context);
                    if (isNewerVersion(finalTag, currentVersion)) {
                        showUpdateDialog(context, finalTag, releaseTitle, releaseBody, finalApkUrl);
                    } else if (isUserInitiated) {
                        Toast.makeText(context, context.getString(R.string.update_already_latest, currentVersion), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                if (isUserInitiated) {
                    getMainHandler().post(() -> Toast.makeText(context, "Не вдалося з'єднатися з GitHub: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void showUpdateDialog(Context context, String tagName, String title, String body, String apkUrl) {
        String msg = (body != null && !body.trim().isEmpty())
                ? body
                : "Нове оновлення Plantshelf " + tagName + " доступне на GitHub.";

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_dialog_title, tagName))
                .setMessage(msg)
                .setNegativeButton(R.string.btn_cancel, null);

        if (apkUrl != null && !apkUrl.isEmpty()) {
            builder.setPositiveButton(R.string.update_dialog_btn_download, (dialog, which) -> {
                downloadAndInstallApk(context, apkUrl, tagName);
            });
        } else {
            builder.setPositiveButton("Відкрити реліз", (dialog, which) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases"));
                context.startActivity(browserIntent);
            });
        }

        builder.show();
    }

    @SuppressWarnings("deprecation")
    private static void downloadAndInstallApk(Context context, String apkUrl, String tagName) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(context.getString(R.string.update_downloading));
        progressDialog.setMessage("Завантаження APK з GitHub...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        executor.execute(() -> {
            HttpURLConnection conn = null;
            InputStream input = null;
            FileOutputStream output = null;
            try {
                URL url = new URL(apkUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Plantshelf-Android-App");
                conn.connect();

                // Handle HTTP redirects (GitHub releases redirect to AWS S3)
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(newUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.connect();
                }

                int fileLength = conn.getContentLength();
                input = conn.getInputStream();

                File cacheDir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
                File updatesDir = new File(cacheDir, "updates");
                if (!updatesDir.exists()) {
                    updatesDir.mkdirs();
                }

                File apkFile = new File(updatesDir, "Plantshelf-" + tagName + ".apk");
                output = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        getMainHandler().post(() -> progressDialog.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }
                output.flush();

                getMainHandler().post(() -> {
                    progressDialog.dismiss();
                    installApk(context, apkFile);
                });

            } catch (Exception e) {
                getMainHandler().post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(context, context.getString(R.string.update_download_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                    if (conn != null) conn.disconnect();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static void installApk(Context context, File apkFile) {
        if (!apkFile.exists()) return;

        // On Android 8.0+, verify unknown sources install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(context, "Дозвольте встановлення з цього джерела для завершення оновлення", Toast.LENGTH_LONG).show();
                Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:" + context.getPackageName()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(permissionIntent);
                return;
            }
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(installIntent);
        } catch (Exception e) {
            Toast.makeText(context, "Помилка запуску інсталятора: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Compares remote tag (e.g. "v1.0.1") with current BuildConfig version (e.g. "1.0.0").
     */
    public static boolean isNewerVersion(String remoteTag, String currentVersion) {
        if (remoteTag == null || remoteTag.isEmpty() || currentVersion == null || currentVersion.isEmpty()) {
            return false;
        }

        String remote = remoteTag.replaceAll("^[vV]", "").trim();
        String current = currentVersion.replaceAll("^[vV]", "").trim();

        String[] remoteParts = remote.split("[.-]");
        String[] currentParts = current.split("[.-]");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int r = 0;
            int c = 0;
            if (i < remoteParts.length) {
                try {
                    r = Integer.parseInt(remoteParts[i]);
                } catch (NumberFormatException ignored) {
                }
            }
            if (i < currentParts.length) {
                try {
                    c = Integer.parseInt(currentParts[i]);
                } catch (NumberFormatException ignored) {
                }
            }
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    public static String getCurrentAppVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}
