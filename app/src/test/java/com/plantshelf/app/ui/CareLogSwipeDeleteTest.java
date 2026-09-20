package com.plantshelf.app.ui;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.ui.adapter.CareLogAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CareLogSwipeDeleteTest {

    private Context context;
    private PlantshelfDatabase database;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, PlantshelfDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void testCareLogLocalizedKindTitles() {
        CareLogEntity waterLog = new CareLogEntity("log1", "plant1", "water", "2026-09-19", System.currentTimeMillis());
        assertEquals("Полив", CareLogAdapter.getLocalizedKindTitle(waterLog));

        CareLogEntity fertLog = new CareLogEntity("log2", "plant1", "fert", "2026-09-19", System.currentTimeMillis());
        assertEquals("Внесення добрив", CareLogAdapter.getLocalizedKindTitle(fertLog));

        CareLogEntity mistLog = new CareLogEntity("log3", "plant1", "mist", "2026-09-19", System.currentTimeMillis());
        assertEquals("Обприскування", CareLogAdapter.getLocalizedKindTitle(mistLog));

        CareLogEntity treatWithDrug = new CareLogEntity("log4", "plant1", "treatment", "2026-09-19", System.currentTimeMillis());
        treatWithDrug.setTreatmentDrug("Актара");
        assertEquals("Лікування (Актара)", CareLogAdapter.getLocalizedKindTitle(treatWithDrug));

        CareLogEntity treatNoDrug = new CareLogEntity("log5", "plant1", "treatment", "2026-09-19", System.currentTimeMillis());
        assertEquals("Лікування / Обробка", CareLogAdapter.getLocalizedKindTitle(treatNoDrug));
    }

    @Test
    public void testCareLogFormattedDate() {
        long ts = 1789400000000L;
        CareLogEntity tsLog = new CareLogEntity("log_ts", "plant1", "water", "2026-09-15", ts);
        String formatted = CareLogAdapter.getFormattedDate(tsLog);
        assertNotNull(formatted);
        assertTrue("Formatted date should contain September in Ukrainian: " + formatted,
                formatted.contains("вересня") || formatted.contains("09"));

        CareLogEntity stringOnlyLog = new CareLogEntity("log_str", "plant1", "water", "2026-09-18", 0);
        assertEquals("2026-09-18", CareLogAdapter.getFormattedDate(stringOnlyLog));
    }

    @Test
    public void testCareLogAdapterGetItemBounds() {
        CareLogAdapter adapter = new CareLogAdapter();
        CareLogEntity log1 = new CareLogEntity("l1", "p1", "water", "2026-09-19", 1000L);
        CareLogEntity log2 = new CareLogEntity("l2", "p1", "fert", "2026-09-19", 2000L);

        adapter.setLogs(Arrays.asList(log1, log2));

        assertEquals(2, adapter.getItemCount());
        assertEquals(log1, adapter.getItem(0));
        assertEquals(log2, adapter.getItem(1));
        assertNull(adapter.getItem(-1));
        assertNull(adapter.getItem(2));
    }

    @Test
    public void testDeleteAndRestoreCareLogWorkflow() {
        PlantEntity plant = new PlantEntity("plant_monstera");
        plant.setName("Монстера");
        database.plantDao().insert(plant);

        CareLogEntity waterLog = new CareLogEntity("water_log_1", "plant_monstera", "water", "2026-09-18", 1789400000000L);
        CareLogEntity fertLog = new CareLogEntity("fert_log_1", "plant_monstera", "fert", "2026-09-19", 1789486400000L);
        database.careLogDao().insert(waterLog);
        database.careLogDao().insert(fertLog);

        List<CareLogEntity> logsBefore = database.careLogDao().getLogsForPlantSync("plant_monstera");
        assertEquals(2, logsBefore.size());

        // Simulate swipe-to-delete of fert record
        database.careLogDao().delete(fertLog);
        List<CareLogEntity> logsAfterDelete = database.careLogDao().getLogsForPlantSync("plant_monstera");
        assertEquals(1, logsAfterDelete.size());
        assertEquals("water_log_1", logsAfterDelete.get(0).getId());

        // Simulate undo action from Snackbar
        database.careLogDao().insert(fertLog);
        List<CareLogEntity> logsRestored = database.careLogDao().getLogsForPlantSync("plant_monstera");
        assertEquals(2, logsRestored.size());
    }
}
