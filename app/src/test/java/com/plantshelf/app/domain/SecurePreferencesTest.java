package com.plantshelf.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.plantshelf.app.data.ai.GeminiPlantAiService;
import com.plantshelf.app.domain.security.DatabaseSecurityConfig;
import com.plantshelf.app.domain.security.SecurePreferencesManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SecurePreferencesTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Clear preferences
        context.getSharedPreferences(SecurePreferencesManager.SECURE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(SecurePreferencesManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void testPutAndGetSecret() {
        SecurePreferencesManager.putSecret(context, "test_secret_key", "AIzaSySecret12345");
        String secret = SecurePreferencesManager.getSecret(context, "test_secret_key", null);
        assertEquals("AIzaSySecret12345", secret);
    }

    @Test
    public void testMigrationFromLegacyPlaintext() {
        // Simulate a pre-existing key in legacy unencrypted shared preferences
        SharedPreferences legacy = context.getSharedPreferences(SecurePreferencesManager.LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        legacy.edit().putString(GeminiPlantAiService.KEY_GEMINI_API_KEY, "AIzaSyLegacyKey999").commit();

        // Reading through SecurePreferencesManager should automatically migrate it
        String migrated = GeminiPlantAiService.getSavedApiKey(context);
        assertEquals("AIzaSyLegacyKey999", migrated);

        // Verify that the legacy unencrypted file has had the secret removed
        assertEquals(null, legacy.getString(GeminiPlantAiService.KEY_GEMINI_API_KEY, null));
    }

    @Test
    public void testDatabaseSecurityConfigPassphrase() {
        String passphrase1 = DatabaseSecurityConfig.getOrCreateDatabasePassphrase(context);
        assertNotNull(passphrase1);
        String passphrase2 = DatabaseSecurityConfig.getOrCreateDatabasePassphrase(context);
        assertEquals("Passphrase should be consistent across calls", passphrase1, passphrase2);
    }
}
