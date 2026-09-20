package com.plantshelf.app.domain.security;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Architectural blueprint and configuration helper for Database Encryption.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * For home plant tracking, standard SQLite/Room storage is sufficient and lightweight.
 * However, when evolving this architecture for the upcoming "PillShelf" application
 * (managing prescription medication schedules, dosages, and personal health telemetry),
 * unencrypted SQLite exposes confidential health data to physical memory extraction
 * and violates HIPAA / GDPR security baselines.
 *
 * <p>This class outlines the transparent migration path to SQLCipher (net.zetetic:android-database-sqlcipher):
 * <pre>{@code
 *   // In PillShelf / Encrypted Room initialization:
 *   byte[] passphrase = DatabaseSecurityConfig.getOrCreateDbPassphrase(context);
 *   SupportFactory factory = new SupportFactory(passphrase);
 *   Room.databaseBuilder(context, AppDatabase.class, "pillshelf.db")
 *       .openHelperFactory(factory)
 *       .build();
 * }</pre>
 */
public final class DatabaseSecurityConfig {

    private static final String PREF_DB_ENCRYPTION_KEY = "db_encryption_passphrase";

    private DatabaseSecurityConfig() {
        // Utility class
    }

    /**
     * Retrieves or generates a hardware-protected 256-bit passphrase
     * for SQLCipher database encryption using Keystore-backed SecurePreferences.
     *
     * @param context Application context
     * @return Base64 or byte passphrase string
     */
    @NonNull
    public static String getOrCreateDatabasePassphrase(@NonNull Context context) {
        String existing = SecurePreferencesManager.getSecret(context, PREF_DB_ENCRYPTION_KEY, null);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }

        // Generate a cryptographically secure 256-bit random key
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String generated = android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP);

        SecurePreferencesManager.putSecret(context, PREF_DB_ENCRYPTION_KEY, generated);
        return generated;
    }

    /**
     * Evaluates whether database encryption is enabled for the current build profile.
     */
    public static boolean isEncryptionRequired() {
        // For Plantshelf, local SQLite is unencrypted by default;
        // Subclasses / PillShelf forks flip this flag to true.
        return false;
    }
}
