package com.plantshelf.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.plantshelf.app.data.calendar.CalendarIntegrationHelper;
import com.plantshelf.app.data.entity.PlantEntity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CalendarIntegrationTest {

    @Test
    public void testIcsContentGeneration() {
        List<PlantEntity> plants = new ArrayList<>();

        PlantEntity monstera = new PlantEntity("m_1");
        monstera.setName("Монстера");
        monstera.setVariety("Альба");
        monstera.setIntervalDays(9);
        monstera.setIntervalDaysWinter(14);
        monstera.setSoil("Ароїдний мікс");
        plants.add(monstera);

        PlantEntity ficus = new PlantEntity("f_2");
        ficus.setName("Фікус");
        ficus.setIntervalDays(7);
        plants.add(ficus);

        String ics = CalendarIntegrationHelper.generateIcsContent(plants);

        assertNotNull(ics);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));

        // Check events
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:🌱 Полив: Монстера"));
        assertTrue(ics.contains("RRULE:FREQ=DAILY;INTERVAL=9"));

        assertTrue(ics.contains("SUMMARY:🌱 Полив: Фікус"));
        assertTrue(ics.contains("RRULE:FREQ=DAILY;INTERVAL=7"));
    }
}
