package com.plantshelf.app.domain.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Manages encrypted SharedPreferences backed by the Android Keystore.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Plaintext SharedPreferences store secrets in readable XML files under the app's internal
 * data directory. On rooted devices or during system backup inspection, sensitive BYOK
 * (Bring Your Own Key) secrets such as Gemini API tokens can be compromised.
 *
 * <p>This manager provides hardware-backed AES-256 GCM encryption via AndroidX Security,
 * while maintaining transparent migration from legacy unencrypted preference files
 * and graceful fallback for unit-testing/Robolectric environments where Keystore providers
 * may be unavailable.
 */
public final class SecurePreferencesManager {

    private static final String TAG = "SecurePreferences";

    /**
     * File name for Keystore-encrypted preferences.
     * Excluded from cloud backups via backup_rules.xml and data_extraction_rules.xml.
     */
    public static final String SECURE_PREFS_NAME = "plantshelf_secure_ai_prefs";

    /**
     * Legacy plaintext preference file name used for migration.
     */
    public static final String LEGACY_PREFS_NAME = "plantshelf_ai_prefs";

    private SecurePreferencesManager() {
        // Utility class: prevent instantiation
    }

    /**
     * Obtains the encrypted SharedPreferences instance with automatic Keystore master key generation.
     * If Android Keystore encryption fails (e.g. in certain unit test or legacy environments),
     * safely falls back to standard private SharedPreferences to prevent app crashes.
     *
     * @param context Android context
     * @return SharedPreferences instance (encrypted when supported)
     */
    @NonNull
    public static SharedPreferences getSecurePreferences(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Throwable t) {
            Log.w(TAG, "Hardware Keystore encryption unavailable; falling back to private prefs", t);
            return appContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * Retrieves a secret string from secure preferences.
     * If the secret does not yet exist in encrypted storage, checks legacy plaintext preferences,
     * migrates the secret into encrypted storage, and removes it from the legacy file.
     *
     * @param context  Android context
     * @param key      Preference key (e.g., gemini_api_key)
     * @param defValue Default fallback value
     * @return Decrypted secret or default value
     */
    @Nullable
    public static String getSecret(@NonNull Context context, @NonNull String key, @Nullable String defValue) {
        SharedPreferences securePrefs = getSecurePreferences(context);
        if (securePrefs.contains(key)) {
            return securePrefs.getString(key, defValue);
        }

        // Migrate from legacy unencrypted prefs if present
        SharedPreferences legacyPrefs = context.getApplicationContext()
                .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);

        if (legacyPrefs.contains(key)) {
            String legacyVal = legacyPrefs.getString(key, null);
            if (legacyVal != null && !legacyVal.trim().isEmpty()) {
                securePrefs.edit().putString(key, legacyVal.trim()).apply();
                legacyPrefs.edit().remove(key).apply();
                return legacyVal.trim();
            }
        }

        return defValue;
    }

    /**
     * Encrypts and saves a secret string into Keystore-backed storage.
     *
     * @param context Android context
     * @param key     Preference key
     * @param value   Secret value to store
     */
    public static void putSecret(@NonNull Context context, @NonNull String key, @Nullable String value) {
        SharedPreferences securePrefs = getSecurePreferences(context);
        if (value == null || value.trim().isEmpty()) {
            securePrefs.edit().remove(key).apply();
        } else {
            securePrefs.edit().putString(key, value.trim()).apply();
        }

        // Clean up legacy unencrypted file if the key was previously stored there
        try {
            SharedPreferences legacyPrefs = context.getApplicationContext()
                    .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            if (legacyPrefs.contains(key)) {
                legacyPrefs.edit().remove(key).apply();
            }
        } catch (Exception ignored) {
        }
    }
}
