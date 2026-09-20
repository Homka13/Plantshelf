package com.plantshelf.app.ui;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.button.MaterialButton;
import com.plantshelf.app.R;
import com.plantshelf.app.data.calendar.CalendarIntegrationHelper;
import com.plantshelf.app.data.calendar.CalendarSyncManager;
import com.plantshelf.app.data.entity.PlantEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SettingsBulkSyncTest {

    private Context themedContext;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        themedContext = new ContextThemeWrapper(context, R.style.Theme_Plantshelf);
    }

    @Test
    public void testSettingsLayoutHasBulkSyncButton() {
        View view = LayoutInflater.from(themedContext).inflate(R.layout.dialog_notification_settings, null);
        assertNotNull(view);

        MaterialButton btnBulkSync = view.findViewById(R.id.btnBulkSyncCalendar);
        assertNotNull("Bulk sync calendar button should exist in settings layout", btnBulkSync);
        assertTrue(btnBulkSync.getText().toString().contains("синхронізація"));
    }

    @Test
    public void testBulkSyncBatchSyncEmptyList() throws Exception {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
        shadowApp.grantPermissions(Manifest.permission.WRITE_CALENDAR);

        CalendarSyncManager syncManager = CalendarSyncManager.getInstance(themedContext);
        assertTrue(syncManager.hasCalendarPermission());

        // With empty plant list, bulk sync safely returns 0
        int count = CalendarIntegrationHelper.bulkSyncAllPlants(themedContext, Collections.emptyList());
        assertEquals(0, count);
    }

    @Test
    public void testBulkSyncCallbackHandling() throws Exception {
        ShadowApplication shadowApp = Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
        shadowApp.grantPermissions(Manifest.permission.WRITE_CALENDAR);

        CalendarSyncManager syncManager = CalendarSyncManager.getInstance(themedContext);

        List<PlantEntity> plants = new ArrayList<>();
        PlantEntity monstera = new PlantEntity("monstera_1");
        monstera.setName("Монстера Деліціоза");
        monstera.setIntervalDays(5);
        monstera.setIntervalDaysWinter(10);
        plants.add(monstera);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean callbackTriggered = new AtomicBoolean(false);

        syncManager.syncAllPlants(plants, new CalendarSyncManager.SyncCallback() {
            @Override
            public void onProgress(int current, int total) {}

            @Override
            public void onSuccess(int syncedCount) {
                callbackTriggered.set(true);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                callbackTriggered.set(true);
                latch.countDown();
            }
        });

        // Allow background thread to post to main handler and idle looper
        for (int i = 0; i < 20; i++) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            if (latch.await(100, TimeUnit.MILLISECONDS)) {
                break;
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue(callbackTriggered.get());
    }
}
