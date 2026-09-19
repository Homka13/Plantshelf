package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.importer.BackupExporter;
import com.plantshelf.app.data.importer.BrunqBackupImporter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RealBackupStressTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File locateRealBackupFile() {
        // Look in current directory and parent directory (since tests run from either root or app/)
        File file = new File("brunq-backup-2026-09-18.json");
        if (!file.exists()) {
            file = new File("../brunq-backup-2026-09-18.json");
        }
        if (!file.exists()) {
            file = new File("d:/Git projects/Plantshelf/brunq-backup-2026-09-18.json");
        }
        return file;
    }

    /**
     * 1. Rigorously tests BrunqBackupImporter.parseStream(reader, null, null) on the 5.7MB backup.
     * Verifies category count, plant count, care log count, photo count (0 when dirs null),
     * performance timing, and ensures no exceptions are thrown.
     */
    @Test
    public void testRealBackupParsingWithNullDirs() throws Exception {
        File backupFile = locateRealBackupFile();
        org.junit.Assume.assumeTrue("Real backup file not found at " + backupFile.getAbsolutePath(), backupFile.exists());
        assertTrue("Backup file must be > 5MB", backupFile.length() > 5 * 1024 * 1024);

        long startTime = System.currentTimeMillis();

        BrunqBackupImporter.ParsedData data;
        try (FileInputStream fis = new FileInputStream(backupFile);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(isr)) {
            data = BrunqBackupImporter.parseStream(reader, null, null);
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Parsed 5.7MB real backup in " + duration + " ms");

        assertNotNull("Parsed data must not be null", data);
        assertEquals("Should extract exactly 6 categories", 6, data.categories.size());
        assertEquals("Should extract exactly 39 plants", 39, data.plants.size());
        assertEquals("Should extract exactly 187 care logs", 187, data.careLogs.size());
        assertEquals("Should extract 0 photos when tempDirs are null", 0, data.photos.size());

        // Performance benchmark: Streaming JSON parser should process 5.7MB under 5000ms
        assertTrue("Parsing 5.7MB should finish under 5 seconds (took " + duration + " ms)", duration < 5000);

        // Verify specific known entities from real backup
        boolean foundMonstera = false;
        for (PlantEntity p : data.plants) {
            if ("dhykzs5g".equals(p.getId())) {
                foundMonstera = true;
                assertEquals("Monstera deliciosa variegata", p.getLatin());
                assertEquals(9, p.getIntervalDays());
                assertEquals(14, p.getIntervalDaysWinter());
                assertEquals(15000, p.getLux());
                assertEquals("hrw9mhdg", p.getCategoryId());
                assertTrue(p.isFavorite());
            }
        }
        assertTrue("Plant dhykzs5g should be present in real backup", foundMonstera);

        // Verify categories
        boolean foundLivingRoom = false;
        for (CategoryEntity c : data.categories) {
            if ("hrw9mhdg".equals(c.getId())) {
                foundLivingRoom = true;
                assertEquals("#4E8D7C", c.getColor());
                assertEquals("sofa", c.getIcon());
            }
        }
        assertTrue("Category hrw9mhdg should be present in real backup", foundLivingRoom);
    }

    /**
     * 2. Tests parsing the real backup file WITH real temporary directories.
     * Decodes all 55 embedded photos from Base64 into JPEG files on disk.
     */
    @Test
    public void testRealBackupParsingWithTempPhotosDir() throws Exception {
        File backupFile = locateRealBackupFile();
        org.junit.Assume.assumeTrue("Real backup file not found at " + backupFile.getAbsolutePath(), backupFile.exists());

        File tempPhotosDir = tempFolder.newFolder("temp_photos");
        File finalPhotosDir = tempFolder.newFolder("final_photos");

        BrunqBackupImporter.ParsedData data;
        try (FileInputStream fis = new FileInputStream(backupFile);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             JsonReader reader = new JsonReader(isr)) {
            data = BrunqBackupImporter.parseStream(reader, tempPhotosDir, finalPhotosDir);
        }

        assertNotNull(data);
        assertEquals(6, data.categories.size());
        assertEquals(39, data.plants.size());
        assertEquals(187, data.careLogs.size());
        assertEquals("Should extract all 55 photos", 55, data.photos.size());

        // Verify that all 55 photo files actually exist in tempPhotosDir and are non-empty
        File[] savedFiles = tempPhotosDir.listFiles();
        assertNotNull(savedFiles);
        assertEquals(55, savedFiles.length);

        for (File f : savedFiles) {
            assertTrue("Photo file " + f.getName() + " must exist", f.exists());
            assertTrue("Photo file " + f.getName() + " must be non-empty", f.length() > 1000);
        }

        // Verify primaryPhotoPath was assigned to plants that have photos
        int plantsWithPhoto = 0;
        for (PlantEntity p : data.plants) {
            if (p.getPrimaryPhotoPath() != null) {
                plantsWithPhoto++;
                assertTrue(p.getPrimaryPhotoPath().contains("final_photos"));
            }
        }
        assertTrue("At least one plant should have primary photo set", plantsWithPhoto > 0);
    }

    /**
     * 3. Corrupted JSON test:
     * Asserts that syntax errors throw IOException properly.
     */
    @Test
    public void testCorruptedJson() {
        String corruptedJson = "{\"categories\": [ { \"id\": \"c1\", \"name\": \"Living Room\""; // unclosed JSON

        try (JsonReader reader = new JsonReader(new StringReader(corruptedJson))) {
            BrunqBackupImporter.parseStream(reader, null, null);
            fail("Should throw IOException on corrupted JSON");
        } catch (IOException expected) {
            // Expected
        }
    }

    /**
     * 4. Truncated JSON test:
     * Truncated midway through plants stream.
     */
    @Test
    public void testTruncatedJson() {
        String truncatedJson = "{\"categories\": [], \"plants\": [{\"id\": \"p1\", \"name\": \"Ficus\", \"careLog\": [{\"id\": \"l1\"";

        try (JsonReader reader = new JsonReader(new StringReader(truncatedJson))) {
            BrunqBackupImporter.parseStream(reader, null, null);
            fail("Should throw IOException on truncated JSON");
        } catch (IOException expected) {
            // Expected
        }
    }

    /**
     * 5. Missing fields test:
     * Verifies that minimal objects {} use safe defaults and generate valid UUID-based IDs.
     */
    @Test
    public void testMissingFields() throws Exception {
        String json = "{\"categories\": [{}], \"plants\": [{}]}";

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            BrunqBackupImporter.ParsedData data = BrunqBackupImporter.parseStream(reader, null, null);
            assertEquals(1, data.categories.size());
            CategoryEntity cat = data.categories.get(0);
            assertNotNull(cat.getId());
            assertTrue("Category ID should have c_ prefix", cat.getId().startsWith("c_"));
            assertEquals("Кімната", cat.getName());
            assertEquals("#4E8D7C", cat.getColor());
            assertEquals("leaf", cat.getIcon());
            assertFalse(cat.isSmart());

            assertEquals(1, data.plants.size());
            PlantEntity p = data.plants.get(0);
            assertNotNull(p.getId());
            assertTrue("Plant ID should have p_ prefix", p.getId().startsWith("p_"));
            assertEquals("Рослина", p.getName());
            assertEquals(7, p.getIntervalDays());
            assertEquals(5000, p.getLux());
            assertEquals(0, p.getPotSize());
            assertFalse(p.isFavorite());
        }
    }

    /**
     * 6. Null fields test:
     * Verifies that explicit nulls do not trigger NullPointerException and are safely sanitized.
     */
    @Test
    public void testNullFields() throws Exception {
        String json = "{\n"
                + "  \"categories\": [\n"
                + "    {\"id\": null, \"name\": null, \"color\": null, \"icon\": null, \"smart\": null}\n"
                + "  ],\n"
                + "  \"plants\": [\n"
                + "    {\n"
                + "      \"id\": null,\n"
                + "      \"name\": null,\n"
                + "      \"categoryId\": null,\n"
                + "      \"lux\": null,\n"
                + "      \"intervalDays\": null,\n"
                + "      \"quarantineUntil\": null,\n"
                + "      \"quarantineReason\": null,\n"
                + "      \"potDepth\": null,\n"
                + "      \"substrate\": null,\n"
                + "      \"careLog\": null,\n"
                + "      \"photos\": null\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            BrunqBackupImporter.ParsedData data = BrunqBackupImporter.parseStream(reader, null, null);
            assertEquals(1, data.categories.size());
            assertEquals(1, data.plants.size());

            PlantEntity plant = data.plants.get(0);
            assertNotNull(plant.getId());
            assertTrue(plant.getId().startsWith("p_"));
            assertEquals("Рослина", plant.getName());
            assertEquals(5000, plant.getLux());
            assertEquals(7, plant.getIntervalDays());
            assertEquals(0, data.careLogs.size());
            assertEquals(0, data.photos.size());
        }
    }

    /**
     * 7. Extra unknown fields test:
     * Verifies root and object-level unknown properties (like Brunq's haptics, aiMonth, env, etc.)
     * are skipped gracefully without parsing errors.
     */
    @Test
    public void testExtraUnknownFields() throws Exception {
        String json = "{\n"
                + "  \"haptics\": true,\n"
                + "  \"aiMonth\": \"2026-09\",\n"
                + "  \"unknownNested\": {\"deep\": {\"array\": [1, 2, 3]}},\n"
                + "  \"categories\": [\n"
                + "    {\"id\": \"c1\", \"name\": \"Kitchen\", \"extraCategoryField\": 42, \"env\": {\"temp\": 22}}\n"
                + "  ],\n"
                + "  \"plants\": [\n"
                + "    {\n"
                + "      \"id\": \"p1\",\n"
                + "      \"name\": \"Pothos\",\n"
                + "      \"unknownPlantField\": [\"a\", \"b\"],\n"
                + "      \"diagnoses\": [{\"date\": \"2026-09-01\", \"state\": \"ok\"}],\n"
                + "      \"careLog\": [\n"
                + "        {\"id\": \"log1\", \"kind\": \"water\", \"date\": \"2026-09-18\", \"unknownLogField\": true}\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            BrunqBackupImporter.ParsedData data = BrunqBackupImporter.parseStream(reader, null, null);
            assertEquals(1, data.categories.size());
            assertEquals(1, data.plants.size());
            assertEquals(1, data.careLogs.size());

            assertEquals("Kitchen", data.categories.get(0).getName());
            assertEquals("Pothos", data.plants.get(0).getName());
            assertEquals("water", data.careLogs.get(0).getKind());
        }
    }

    /**
     * 8. Type-mismatched fields test:
     * Numbers as strings, booleans as strings, unexpected objects for string fields.
     */
    @Test
    public void testTypeMismatchedFields() throws Exception {
        String json = "{\n"
                + "  \"categories\": [],\n"
                + "  \"plants\": [\n"
                + "    {\n"
                + "      \"id\": \"p_type_test\",\n"
                + "      \"name\": \"Sansevieria\",\n"
                + "      \"lux\": \"12000\",\n"
                + "      \"intervalDays\": \"21\",\n"
                + "      \"favorite\": \"true\",\n"
                + "      \"note\": {\"unexpected\": \"object\"}\n"
                + "    }\n"
                + "  ]\n"
                + "}";

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            BrunqBackupImporter.ParsedData data = BrunqBackupImporter.parseStream(reader, null, null);
            assertEquals(1, data.plants.size());
            PlantEntity plant = data.plants.get(0);
            assertEquals(12000, plant.getLux());
            assertEquals(21, plant.getIntervalDays());
            assertTrue(plant.isFavorite());
            assertEquals("", plant.getNote());
        }
    }

    /**
     * 9. Data integrity test for quarantine, pot, substrate, and fertFreq:
     * Checks if quarantineUntil, quarantineFrom, quarantineReason, potDepth, potMaterial,
     * substrate, fertFreq are preserved across BackupExporter -> BrunqBackupImporter round-trip.
     */
    @Test
    public void testDataIntegrityQuarantinePotSubstrateFertFreq() throws Exception {
        PlantEntity plant = new PlantEntity("plant_integrity_1");
        plant.setName("Calathea Orbifolia");
        plant.setFertFreq("Every 2 weeks in spring/summer");
        plant.setPotDepth("deep");
        plant.setPotMaterial("terracotta");
        plant.setSubstrate("perlite + pine bark + peat 1:1:1");
        plant.setQuarantineFrom("2026-09-01");
        plant.setQuarantineUntil("2026-09-22");
        plant.setQuarantineReason("Spider mites treatment");

        List<PlantEntity> plants = Collections.singletonList(plant);
        List<CategoryEntity> categories = Collections.singletonList(new CategoryEntity("cat_1", "Greenhouse", "#4E8D7C", "leaf", false));

        // Export to JSON
        JsonObject exportedJson = BackupExporter.buildBackupJson(categories, plants, id -> Collections.emptyList(), id -> Collections.emptyList());
        String jsonString = exportedJson.toString();

        // Verify properties are present in exported JSON
        JsonObject exportedPlant = exportedJson.getAsJsonArray("plants").get(0).getAsJsonObject();
        assertEquals("Every 2 weeks in spring/summer", exportedPlant.get("fertFreq").getAsString());
        assertEquals("deep", exportedPlant.get("potDepth").getAsString());
        assertEquals("terracotta", exportedPlant.get("potMaterial").getAsString());
        assertEquals("perlite + pine bark + peat 1:1:1", exportedPlant.get("substrate").getAsString());
        assertEquals("2026-09-01", exportedPlant.get("quarantineFrom").getAsString());
        assertEquals("2026-09-22", exportedPlant.get("quarantineUntil").getAsString());
        assertEquals("Spider mites treatment", exportedPlant.get("quarantineReason").getAsString());

        // Re-import from JSON
        BrunqBackupImporter.ParsedData imported;
        try (JsonReader reader = new JsonReader(new StringReader(jsonString))) {
            imported = BrunqBackupImporter.parseStream(reader, null, null);
        }

        assertEquals(1, imported.plants.size());
        PlantEntity importedPlant = imported.plants.get(0);
        assertEquals("plant_integrity_1", importedPlant.getId());
        assertEquals("Calathea Orbifolia", importedPlant.getName());
        assertEquals("Every 2 weeks in spring/summer", importedPlant.getFertFreq());
        assertEquals("deep", importedPlant.getPotDepth());
        assertEquals("terracotta", importedPlant.getPotMaterial());
        assertEquals("perlite + pine bark + peat 1:1:1", importedPlant.getSubstrate());
        assertEquals("2026-09-01", importedPlant.getQuarantineFrom());
        assertEquals("2026-09-22", importedPlant.getQuarantineUntil());
        assertEquals("Spider mites treatment", importedPlant.getQuarantineReason());
        assertTrue(importedPlant.isQuarantined());
    }

    /**
     * 10. Data integrity test for CareLogEntity's treatmentDrug and notes:
     * Checks if treatmentDrug and notes are preserved across export and import.
     */
    @Test
    public void testCareLogTreatmentDrugAndNotesPreserved() throws Exception {
        PlantEntity plant = new PlantEntity("plant_treatment_test");
        plant.setName("Ficus Elastica");

        CareLogEntity log = new CareLogEntity("log_treatment_1", plant.getId(), "treatment", "2026-09-15", 1789400000000L);
        log.setTreatmentDrug("Aktara 1.4g/L");
        log.setNotes("Foliar spray and soil drench against thrips");

        List<PlantEntity> plants = Collections.singletonList(plant);
        List<CareLogEntity> logs = Collections.singletonList(log);

        JsonObject exportedJson = BackupExporter.buildBackupJson(
                Collections.emptyList(),
                plants,
                id -> logs,
                id -> Collections.emptyList()
        );

        String jsonString = exportedJson.toString();

        // Verify JSON fields
        JsonObject exportedLog = exportedJson.getAsJsonArray("plants").get(0).getAsJsonObject()
                .getAsJsonArray("careLog").get(0).getAsJsonObject();
        assertEquals("treatment", exportedLog.get("kind").getAsString());
        assertEquals("Aktara 1.4g/L", exportedLog.get("treatmentDrug").getAsString());
        assertEquals("Foliar spray and soil drench against thrips", exportedLog.get("notes").getAsString());

        // Re-import
        BrunqBackupImporter.ParsedData imported;
        try (JsonReader reader = new JsonReader(new StringReader(jsonString))) {
            imported = BrunqBackupImporter.parseStream(reader, null, null);
        }

        assertEquals(1, imported.careLogs.size());
        CareLogEntity importedLog = imported.careLogs.get(0);
        assertEquals("log_treatment_1", importedLog.getId());
        assertEquals("treatment", importedLog.getKind());
        assertEquals("Aktara 1.4g/L", importedLog.getTreatmentDrug());
        assertEquals("Foliar spray and soil drench against thrips", importedLog.getNotes());
        assertEquals(1789400000000L, importedLog.getTimestamp());
    }

    /**
     * 11. Test encodeFileToBase64 without partial read bugs:
     * Verifies that large files (> 64KB and multi-megabyte) are read completely and encode accurately.
     */
    @Test
    public void testEncodeFileToBase64NoPartialRead() throws Exception {
        File testFile = tempFolder.newFile("test_large_image.bin");

        // Generate 128KB of pseudo-random binary data covering all byte values 0..255
        byte[] expectedBytes = new byte[128 * 1024];
        new Random(42).nextBytes(expectedBytes);

        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write(expectedBytes);
            fos.flush();
        }

        String encoded = BackupExporter.encodeFileToBase64(testFile.getAbsolutePath());
        assertNotNull(encoded);

        // Verify standard Base64 encoding matches
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(expectedBytes);
        assertEquals(expectedBase64, encoded);

        // Verify decoding the encoded string yields the exact original byte array
        byte[] decoded = java.util.Base64.getMimeDecoder().decode(encoded);
        assertEquals(expectedBytes.length, decoded.length);
        for (int i = 0; i < expectedBytes.length; i++) {
            assertEquals("Byte mismatch at index " + i, expectedBytes[i], decoded[i]);
        }

        // Test non-existent file and null path
        assertEquals(null, BackupExporter.encodeFileToBase64(null));
        assertEquals(null, BackupExporter.encodeFileToBase64("non_existent_file_path_12345.xyz"));
    }
}
