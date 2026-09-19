package com.plantshelf.app.repository;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PlantRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private PlantshelfDatabase db;
    private PlantRepository repository;
    private ExecutorService directExecutor;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, PlantshelfDatabase.class)
                .allowMainThreadQueries()
                .build();

        // Direct synchronous executor for deterministic testing
        directExecutor = Executors.newSingleThreadExecutor();

        repository = new PlantRepository(
                context,
                db.categoryDao(),
                db.plantDao(),
                db.careLogDao(),
                db.photoDao(),
                directExecutor
        );
    }

    @After
    public void tearDown() {
        directExecutor.shutdown();
        db.close();
    }

    @Test
    public void testInsertAndRetrieveCategoryAndPlant() throws Exception {
        CategoryEntity cat = new CategoryEntity("cat_living_room", "Вітальня", "#4CAF50", "ic_shelf", false);
        db.categoryDao().insert(cat);

        PlantEntity plant = new PlantEntity("plant_monstera");
        plant.setName("Монстера");
        plant.setCategoryId("cat_living_room");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(14);
        plant.setLastWatered("2026-09-10");
        db.plantDao().insert(plant);

        PlantEntity retrieved = db.plantDao().getPlantByIdSync("plant_monstera");
        assertNotNull(retrieved);
        assertEquals("Монстера", retrieved.getName());
        assertEquals(7, retrieved.getIntervalDays());
        assertEquals(14, retrieved.getIntervalDaysWinter());
        assertEquals("cat_living_room", retrieved.getCategoryId());
    }

    @Test
    public void testRecordWateringUpdatesPlantAndAddsLog() throws Exception {
        PlantEntity plant = new PlantEntity("plant_ficus");
        plant.setName("Фікус");
        plant.setLastWatered("2026-09-01");
        db.plantDao().insert(plant);

        // Record watering
        repository.recordWatering("plant_ficus");
        // Give background thread brief moment to complete
        Thread.sleep(200);

        PlantEntity updated = db.plantDao().getPlantByIdSync("plant_ficus");
        assertNotNull(updated);
        assertNotNull(updated.getLastWatered());
        assertFalse("2026-09-01".equals(updated.getLastWatered()));

        List<CareLogEntity> logs = db.careLogDao().getLogsForPlantSync("plant_ficus");
        assertFalse(logs.isEmpty());
        assertEquals("water", logs.get(0).getKind());
    }

    @Test
    public void testRecordTreatmentAndQuarantine() throws Exception {
        PlantEntity plant = new PlantEntity("plant_spathy");
        plant.setName("Спатифілум");
        db.plantDao().insert(plant);

        repository.recordTreatment("plant_spathy", "Актара", "Виявлено щитівку", true, 14, "Щитівка");
        Thread.sleep(200);

        PlantEntity updated = db.plantDao().getPlantByIdSync("plant_spathy");
        assertNotNull(updated);
        assertTrue(updated.isQuarantined());
        assertEquals("Щитівка", updated.getQuarantineReason());

        List<CareLogEntity> logs = db.careLogDao().getLogsForPlantSync("plant_spathy");
        assertFalse(logs.isEmpty());
        assertEquals("treatment", logs.get(0).getKind());
    }

    @Test
    public void testSmartFertilizingFieldsAndCalendarEventIdPersistence() throws Exception {
        PlantEntity plant = new PlantEntity("plant_calathea");
        plant.setName("Калатея");
        plant.setRecommendedFertilizers("Органічне для декоративно-листяних NPK 6-3-6");
        plant.setFertilizeIntervalSummerDays(14);
        plant.setFertilizeIntervalWinterDays(0);
        plant.setCalendarEventId("12345");
        db.plantDao().insert(plant);

        PlantEntity retrieved = db.plantDao().getPlantByIdSync("plant_calathea");
        assertNotNull(retrieved);
        assertEquals("Органічне для декоративно-листяних NPK 6-3-6", retrieved.getRecommendedFertilizers());
        assertEquals(14, retrieved.getFertilizeIntervalSummerDays());
        assertEquals(0, retrieved.getFertilizeIntervalWinterDays());
        assertEquals("12345", retrieved.getCalendarEventId());

        // Update calendar event ID and schedule
        retrieved.setCalendarEventId("67890");
        retrieved.setFertilizeIntervalSummerDays(10);
        repository.updatePlant(retrieved);
        Thread.sleep(200);

        PlantEntity updated = db.plantDao().getPlantByIdSync("plant_calathea");
        assertNotNull(updated);
        assertEquals("67890", updated.getCalendarEventId());
        assertEquals(10, updated.getFertilizeIntervalSummerDays());
    }
}
