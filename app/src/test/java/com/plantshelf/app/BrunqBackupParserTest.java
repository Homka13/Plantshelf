package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plantshelf.app.data.entity.PlantEntity;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BrunqBackupParserTest {

    @Test
    public void testJsonStructureParsing() {
        String testJson = "{"
                + "\"categories\":[{\"id\":\"cat_1\",\"name\":\"Вітальня\",\"color\":\"#4E8D7C\",\"icon\":\"sofa\",\"smart\":false}],"
                + "\"plants\":[{\"id\":\"p_1\",\"name\":\"Монстера\",\"variety\":\"Тайське сузір'я\",\"intervalDays\":9,\"intervalDaysWinter\":14,\"lastWatered\":\"2026-09-07\"}]"
                + "}";

        JsonObject root = JsonParser.parseString(testJson).getAsJsonObject();
        assertTrue(root.has("categories"));
        assertTrue(root.has("plants"));

        JsonArray categories = root.getAsJsonArray("categories");
        assertEquals(1, categories.size());
        assertEquals("Вітальня", categories.get(0).getAsJsonObject().get("name").getAsString());

        JsonArray plants = root.getAsJsonArray("plants");
        assertEquals(1, plants.size());
        assertEquals("Монстера", plants.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(9, plants.get(0).getAsJsonObject().get("intervalDays").getAsInt());
    }

    @Test
    public void testDaysUntilWateringCalculation() {
        PlantEntity plant = new PlantEntity("test_id");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);

        // Watered today
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        plant.setLastWatered(today);

        // Should be approximately 7 days left
        int daysLeft = plant.getDaysUntilWatering();
        assertTrue("Days left should be positive when watered today", daysLeft >= 6 && daysLeft <= 7);
    }
}
