package com.plantshelf.app.calendar;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.plantshelf.app.data.calendar.CalendarIntegrationHelper;
import com.plantshelf.app.data.entity.PlantEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CalendarIntegrationHelperTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testGenerateIcsContent() {
        PlantEntity plant = new PlantEntity("test_monstera");
        plant.setName("Монстера Делікатесна");
        plant.setVariety("Borsigiana");
        plant.setIntervalDays(5);
        plant.setIntervalDaysWinter(10);
        plant.setSoil("Торф, перліт, кокосове волокно");

        String ics = CalendarIntegrationHelper.generateIcsContent(Collections.singletonList(plant));
        assertNotNull(ics);
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("END:VCALENDAR"));
        assertTrue(ics.contains("UID:plantshelf-water-test_monstera@plantshelf.app"));
        assertTrue(ics.contains("RRULE:FREQ=DAILY;INTERVAL=5"));
        assertTrue(ics.contains("Монстера Делікатесна"));
    }

    @Test
    public void testCalendarHelperNullSafety() {
        assertFalse(CalendarIntegrationHelper.deleteCalendarEvent(context, null));
        assertFalse(CalendarIntegrationHelper.deleteCalendarEvent(context, ""));
        assertFalse(CalendarIntegrationHelper.updateCalendarEventSchedule(context, null));

        PlantEntity plantWithoutEventId = new PlantEntity("plant_no_event");
        plantWithoutEventId.setName("Хлорофітум");
        assertFalse(CalendarIntegrationHelper.updateCalendarEventSchedule(context, plantWithoutEventId));

        assertNull(CalendarIntegrationHelper.insertCalendarEventDirect(context, null, "water"));
    }
}
