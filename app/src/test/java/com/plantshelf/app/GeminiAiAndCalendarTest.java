package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.plantshelf.app.data.ai.AiPlantAnalysisResult;
import com.plantshelf.app.data.ai.GeminiPlantAiService;
import com.plantshelf.app.data.models.CalendarTask;

import java.util.List;

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

    @Test
    public void testGeminiDefaultAndAvailableModels() {
        assertEquals("gemini-3.8-flash", GeminiPlantAiService.DEFAULT_MODEL);

        List<String> modelIds = new java.util.ArrayList<>();
        for (GeminiPlantAiService.GeminiModelInfo info : GeminiPlantAiService.AVAILABLE_MODELS) {
            modelIds.add(info.getModelId());
        }

        assertTrue(modelIds.contains("gemini-3.8-flash"));
        assertTrue(modelIds.contains("gemini-3.7-flash"));
        assertTrue(modelIds.contains("gemini-3.6-flash"));
        assertTrue(modelIds.contains("gemini-3.5-flash"));
        assertTrue(modelIds.contains("gemini-3.5-flash-lite"));
        assertTrue(modelIds.contains("gemini-3.1-flash-lite"));
        assertTrue(modelIds.contains("gemini-3.1-pro-preview"));
        assertTrue(modelIds.contains("gemini-3-flash-preview"));
        assertTrue(modelIds.contains("gemini-2.5-flash"));
        assertTrue(modelIds.contains("gemini-2.5-flash-lite"));
        assertTrue(modelIds.contains("gemini-2.5-pro"));
        assertTrue(modelIds.contains("gemini-flash-latest"));
    }

    @Test
    public void testApiKeyDialogModelExtraction() {
        // null or empty returns default
        assertEquals("gemini-3.8-flash", com.plantshelf.app.ui.dialog.ApiKeyDialog.extractModelId(null));
        assertEquals("gemini-3.8-flash", com.plantshelf.app.ui.dialog.ApiKeyDialog.extractModelId("   "));

        // Exact model ID
        assertEquals("gemini-3.8-flash", com.plantshelf.app.ui.dialog.ApiKeyDialog.extractModelId("gemini-3.8-flash"));
        assertEquals("gemini-3.7-flash", com.plantshelf.app.ui.dialog.ApiKeyDialog.extractModelId("gemini-3.7-flash"));

        // Formatted display string from dropdown
        assertEquals("gemini-3.8-flash", com.plantshelf.app.ui.dialog.ApiKeyDialog.extractModelId("Gemini 3.8 Flash (Нова стабільна) (gemini-3.8-flash)"));
        assertEquals("gemini-2.5-flash", com.plantshelf.app.ui.dialog.ApiKeyDialog.extractModelId("Gemini 2.5 Flash (gemini-2.5-flash)"));

        // Custom model string entered manually
        assertEquals("custom-experimental-model", com.plantshelf.app.ui.dialog.ApiKeyDialog.extractModelId("custom-experimental-model"));
    }
}

