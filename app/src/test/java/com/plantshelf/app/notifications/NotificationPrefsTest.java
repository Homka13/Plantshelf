package com.plantshelf.app.notifications;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class NotificationPrefsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Clear prefs before each test
        context.getSharedPreferences("plantshelf_notifications", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void testDefaults() {
        assertTrue("Notifications should be enabled by default", NotificationPrefs.isNotificationsEnabled(context));
        assertEquals("Default hour should be 9", NotificationPrefs.DEFAULT_HOUR, NotificationPrefs.getReminderHour(context));
        assertEquals("Default minute should be 0", NotificationPrefs.DEFAULT_MINUTE, NotificationPrefs.getReminderMinute(context));
        assertEquals("Default formatted time should be 09:00", "09:00", NotificationPrefs.getFormattedTime(context));
        assertFalse("Permission prompt should not be marked shown initially", NotificationPrefs.isPermissionPromptShown(context));
    }

    @Test
    public void testSetReminderTime() {
        NotificationPrefs.setReminderTime(context, 14, 35);
        assertEquals(14, NotificationPrefs.getReminderHour(context));
        assertEquals(35, NotificationPrefs.getReminderMinute(context));
        assertEquals("14:35", NotificationPrefs.getFormattedTime(context));
    }

    @Test
    public void testToggleEnabled() {
        NotificationPrefs.setNotificationsEnabled(context, false);
        assertFalse(NotificationPrefs.isNotificationsEnabled(context));

        NotificationPrefs.setNotificationsEnabled(context, true);
        assertTrue(NotificationPrefs.isNotificationsEnabled(context));
    }

    @Test
    public void testPermissionPromptShownFlag() {
        NotificationPrefs.setPermissionPromptShown(context, true);
        assertTrue(NotificationPrefs.isPermissionPromptShown(context));

        NotificationPrefs.setPermissionPromptShown(context, false);
        assertFalse(NotificationPrefs.isPermissionPromptShown(context));
    }

    @Test
    public void testSchedulerSafeExecution() {
        // Must execute without crashing under test environment
        NotificationScheduler.scheduleCareReminder(context);

        NotificationPrefs.setNotificationsEnabled(context, false);
        NotificationScheduler.scheduleCareReminder(context);
    }
}
