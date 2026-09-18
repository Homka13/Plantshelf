package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.plantshelf.app.data.ai.AiPlantAnalysisResult;
import com.plantshelf.app.data.ai.GeminiPlantAiService;
import com.plantshelf.app.data.models.CalendarTask;

import org.junit.Test;

public class GeminiAiAndCalendarTest {

    @Test
    public void testCleanJsonFromMarkdownCodeBlocks() {
        String markdownRaw = "```json\n"
                + "{\n"
                + "  \"name\": \"Фікус каучуконосний\",\n"
                + "  \"latin\": \"Ficus elastica\",\n"
                + "  \"variety\": \"Робуста\",\n"
                + "  \"difficulty\": \"легка\",\n"
                + "  \"light\": \"яскраве розсіяне\",\n"
                + "  \"lux\": 12000,\n"
                + "  \"intervalDaysSummer\": 7,\n"
                + "  \"intervalDaysWinter\": 14,\n"
                + "  \"fertIntervalDays\": 14,\n"
                + "  \"humidity\": \"середня\",\n"
                + "  \"soil\": \"пухкий дренований субстрат\",\n"
                + "  \"warning\": \"отруйний для котів\",\n"
                + "  \"notes\": \"протирати листя\"\n"
                + "}\n"
                + "```";

        String clean = GeminiPlantAiService.cleanJsonContent(markdownRaw);
        AiPlantAnalysisResult result = new Gson().fromJson(clean, AiPlantAnalysisResult.class);

        assertNotNull(result);
        assertEquals("Фікус каучуконосний", result.getName());
        assertEquals("Ficus elastica", result.getLatin());
        assertEquals(12000, result.getLux());
        assertEquals(7, result.getIntervalDaysSummer());
        assertEquals(14, result.getIntervalDaysWinter());
    }

    @Test
    public void testCalendarTaskModel() {
        CalendarTask task = new CalendarTask(
                "p_123",
                "Монстера",
                "Альба",
                "Вітальня",
                "#4E8D7C",
                "water",
                "2026-09-18"
        );

        assertEquals("p_123", task.getPlantId());
        assertEquals("Монстера", task.getPlantName());
        assertEquals("water", task.getTaskType());
        assertTrue(!task.isCompleted());

        task.setCompleted(true);
        assertTrue(task.isCompleted());
    }
}
