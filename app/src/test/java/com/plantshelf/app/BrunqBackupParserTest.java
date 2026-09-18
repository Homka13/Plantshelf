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

    @Test
    public void testSanitizeId() {
        // Path traversal attempts must be stripped of dangerous characters
        String sanitized = com.plantshelf.app.data.importer.BrunqBackupImporter.sanitizeId("../../secret/file", "ph");
        assertEquals("______secret_file", sanitized);
        assertTrue(!sanitized.contains("/") && !sanitized.contains("."));

        // Null or empty id must generate a UUID-based ID
        String fallback = com.plantshelf.app.data.importer.BrunqBackupImporter.sanitizeId(null, "p");
        assertNotNull(fallback);
        assertTrue(fallback.startsWith("p_"));
        assertTrue(fallback.length() > 10);
    }

    @Test
    public void testStreamingParseStream() throws Exception {
        String json = "{"
                + "\"categories\":[{\"name\":\"Спальня\"}],"
                + "\"plants\":[{\"name\":\"Фікус\",\"careLog\":[{\"kind\":\"water\"}]}]"
                + "}";

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
             com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.InputStreamReader(bais, java.nio.charset.StandardCharsets.UTF_8))) {
            com.plantshelf.app.data.importer.BrunqBackupImporter.ParsedData data =
                    com.plantshelf.app.data.importer.BrunqBackupImporter.parseStream(reader, null, null);

            assertEquals(1, data.categories.size());
            assertEquals("Спальня", data.categories.get(0).getName());
            assertTrue(data.categories.get(0).getId().startsWith("c_"));

            assertEquals(1, data.plants.size());
            assertEquals("Фікус", data.plants.get(0).getName());
            assertTrue(data.plants.get(0).getId().startsWith("p_"));

            assertEquals(1, data.careLogs.size());
            assertEquals("water", data.careLogs.get(0).getKind());
            assertTrue(data.careLogs.get(0).getId().startsWith("log_"));
        }
    }

    @Test
    public void testCategoryForeignKeyAlignmentWithSpecialChars() throws Exception {
        String json = "{"
                + "\"categories\":[{\"id\":\"room.living:123\",\"name\":\"Вітальня\"}],"
                + "\"plants\":[{\"name\":\"Монстера\",\"categoryId\":\"room.living:123\"}]"
                + "}";

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
             com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.InputStreamReader(bais, java.nio.charset.StandardCharsets.UTF_8))) {
            com.plantshelf.app.data.importer.BrunqBackupImporter.ParsedData data =
                    com.plantshelf.app.data.importer.BrunqBackupImporter.parseStream(reader, null, null);

            assertEquals(1, data.categories.size());
            assertEquals(1, data.plants.size());

            String categoryId = data.categories.get(0).getId();
            String plantCategoryId = data.plants.get(0).getCategoryId();

            assertEquals("room_living_123", categoryId);
            assertEquals("room_living_123", plantCategoryId);
            assertEquals(categoryId, plantCategoryId);
        }
    }
}
