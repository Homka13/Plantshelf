package com.plantshelf.app.calendar;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.plantshelf.app.data.calendar.CalendarSyncManager;
import com.plantshelf.app.data.entity.PlantEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CalendarSyncManagerTest {

    private Context context;
    private CalendarSyncManager syncManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        syncManager = CalendarSyncManager.getInstance(context);
    }

    @Test
    public void testSingletonInstance() {
        assertNotNull(syncManager);
        assertSame(syncManager, CalendarSyncManager.getInstance(context));
    }

    @Test
    public void testNullSafetyMethods() {
        // Must not throw exceptions
        syncManager.updateEventForPlant(null);
        syncManager.deleteEventForPlant(null);

        PlantEntity dummy = new PlantEntity("dummy_id");
        dummy.setName("Драцена");
        syncManager.updateEventForPlant(dummy);
        syncManager.deleteEventForPlant(dummy);
        assertEquals(0, syncManager.deleteEventForPlantSync(dummy));
        assertEquals(0, syncManager.deleteEventForPlantSync(null));
    }

    @Test
    public void testSyncAllPlantsPermissionOrEmptyHandling() {
        AtomicBoolean callbackTriggered = new AtomicBoolean(false);
        syncManager.syncAllPlants(new ArrayList<>(), new CalendarSyncManager.SyncCallback() {
            @Override
            public void onProgress(int current, int total) {}

            @Override
            public void onSuccess(int syncedCount) {
                callbackTriggered.set(true);
            }

            @Override
            public void onError(String message) {
                callbackTriggered.set(true);
            }
        });

        // Idle the main looper to execute callback posted on mainHandler
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue(callbackTriggered.get());
    }
}
