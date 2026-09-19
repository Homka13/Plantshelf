package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.stream.JsonReader;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.importer.BrunqBackupImporter;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class BackupRoundtripTest {

    @Test
    public void testFullFieldPreservationOnImport() throws Exception {
        String json = "{"
                + "\"categories\":[{\"id\":\"cat_living\",\"name\":\"Вітальня\",\"color\":\"#4E8D7C\",\"icon\":\"sofa\",\"smart\":false}],"
                + "\"plants\":[{"
                + "\"id\":\"p_calathea\","
                + "\"name\":\"Калатея Медальйон\","
                + "\"nickname\":\"Кала\","
                + "\"variety\":\"Medallion\","
                + "\"latin\":\"Calathea veitchiana\","
                + "\"categoryId\":\"cat_living\","
                + "\"fertFreq\":\"Раз на 2 тижні\","
                + "\"fertIntervalDays\":14,"
                + "\"potSize\":15,"
                + "\"potDepth\":\"Глибокий\","
                + "\"potMaterial\":\"Кераміка\","
                + "\"soil\":\"Спеціальний суглинок\","
                + "\"substrate\":\"Кокос + перліт + мох\","
                + "\"quarantineFrom\":\"2026-09-01\","
                + "\"quarantineUntil\":\"2026-09-21\","
                + "\"quarantineReason\":\"Трипси на листках\","
                + "\"careLog\":[{"
                + "\"id\":\"log_treat_1\","
                + "\"kind\":\"treatment\","
                + "\"date\":\"2026-09-05\","
                + "\"ts\":1725530000000,"
                + "\"treatmentDrug\":\"Актара\","
                + "\"notes\":\"Обробка по листку та пролив ґрунту\""
                + "}]"
                + "}]"
                + "}";

        try (ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
             JsonReader reader = new JsonReader(new InputStreamReader(bais, StandardCharsets.UTF_8))) {

            BrunqBackupImporter.ParsedData data = BrunqBackupImporter.parseStream(reader, null, null);

            assertNotNull(data);
            assertEquals(1, data.plants.size());
            assertEquals(1, data.careLogs.size());

            PlantEntity plant = data.plants.get(0);
            assertEquals("p_calathea", plant.getId());
            assertEquals("Калатея Медальйон", plant.getName());
            assertEquals("Раз на 2 тижні", plant.getFertFreq());
            assertEquals("Глибокий", plant.getPotDepth());
            assertEquals("Кераміка", plant.getPotMaterial());
            assertEquals("Кокос + перліт + мох", plant.getSubstrate());
            assertEquals("2026-09-01", plant.getQuarantineFrom());
            assertEquals("2026-09-21", plant.getQuarantineUntil());
            assertEquals("Трипси на листках", plant.getQuarantineReason());
            assertTrue(plant.isQuarantined());

            CareLogEntity log = data.careLogs.get(0);
            assertEquals("log_treat_1", log.getId());
            assertEquals("treatment", log.getKind());
            assertEquals("2026-09-05", log.getDate());
            assertEquals("Актара", log.getTreatmentDrug());
            assertEquals("Обробка по листку та пролив ґрунту", log.getNotes());
        }
    }
}
