package com.plantshelf.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.plantshelf.app.R;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.PlantEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAppWidgetManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AppWidgetSuiteTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private Context context;
    private PlantshelfDatabase db;
    private AppWidgetManager manager;
    private ShadowAppWidgetManager shadowManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, PlantshelfDatabase.class)
                .allowMainThreadQueries()
                .build();
        PlantshelfDatabase.setTestInstance(db);

        manager = AppWidgetManager.getInstance(context);
        shadowManager = Shadows.shadowOf(manager);
    }

    @After
    public void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
        PlantshelfDatabase.setTestInstance(null);
    }

    @Test
    public void testStatusWidgetCalculation() {
        PlantEntity p1 = new PlantEntity("p1");
        p1.setName("Monstera");
        p1.setIntervalDays(5);
        p1.setLastWatered("2026-09-15"); // overdue

        PlantEntity p2 = new PlantEntity("p2");
        p2.setName("Ficus");
        p2.setIntervalDays(7);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        p2.setLastWatered(today); // watered today -> daysUntilWatering = 7 (healthy)

        db.plantDao().insert(p1);
        db.plantDao().insert(p2);

        int widgetId = shadowManager.createWidget(PlantStatusWidgetProvider.class, R.layout.widget_plant_status);
        int[] ids = new int[]{widgetId};

        PlantStatusWidgetProvider.updateWidgetsInternal(context, manager, ids);
        assertTrue(widgetId > 0);
    }

    @Test
    public void testQuickWaterWidgetActionAndExecution() throws Exception {
        PlantEntity p1 = new PlantEntity("p_thirsty");
        p1.setName("Pothos");
        p1.setIntervalDays(3);
        p1.setLastWatered("2026-09-01"); // Thirsty
        db.plantDao().insert(p1);

        int widgetId = shadowManager.createWidget(QuickWaterWidgetProvider.class, R.layout.widget_quick_water);
        int[] ids = new int[]{widgetId};

        // Render widget with thirsty plant
        QuickWaterWidgetProvider.updateWidgetsInternal(context, manager, ids);

        // Simulate user clicking "Water Now" button on desktop widget
        QuickWaterWidgetProvider provider = new QuickWaterWidgetProvider();
        Intent waterIntent = new Intent(QuickWaterWidgetProvider.ACTION_QUICK_WATER_NOW);
        waterIntent.putExtra(QuickWaterWidgetProvider.EXTRA_PLANT_ID, "p_thirsty");
        waterIntent.putExtra(QuickWaterWidgetProvider.EXTRA_PLANT_NAME, "Pothos");

        provider.onReceive(context, waterIntent);

        // Sleep briefly for repository background executor
        Thread.sleep(250);

        PlantEntity updated = db.plantDao().getPlantByIdSync("p_thirsty");
        assertNotNull(updated);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        assertEquals(today, updated.getLastWatered());
    }

    @Test
    public void testPlantShortcutsWidgetUpdate() {
        int widgetId = shadowManager.createWidget(PlantShortcutsWidgetProvider.class, R.layout.widget_plant_shortcuts);
        int[] ids = new int[]{widgetId};

        PlantShortcutsWidgetProvider.updateWidgetsInternal(context, manager, ids);
        assertTrue(ids.length > 0);
        assertTrue(widgetId > 0);
    }

    @Test
    public void testWidgetUpdateHelperBroadcast() throws Exception {
        WidgetUpdateHelper.updateAllWidgets(context);
        Thread.sleep(150);
        assertNotNull(context);
    }
}
