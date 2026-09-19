package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.plantshelf.app.data.ai.AiPlantAnalysisResult;
import com.plantshelf.app.data.ai.GeminiPlantAiService;
import com.plantshelf.app.data.catalog.CatalogPlant;
import com.plantshelf.app.data.catalog.PlantCatalogRepository;
import com.plantshelf.app.updater.GitHubUpdateManager;

import org.junit.Test;

import java.util.List;

/**
 * Comprehensive stress and integration tests for:
 * 1. GeminiPlantAiService.cleanJsonContent
 * 2. GitHubUpdateManager.isNewerVersion
 * 3. PlantCatalogRepository search with Ukrainian Cyrillic casing & category combinations
 */
public class AiAndCatalogStressTest {

    // =========================================================================
    // 1. GeminiPlantAiService.cleanJsonContent tests
    // =========================================================================

    @Test
    public void testCleanJsonWithStandardJsonCodeBlock() {
        String input = "```json\n"
                + "{\n"
                + "  \"name\": \"Монстера деліціоза\",\n"
                + "  \"latin\": \"Monstera deliciosa\",\n"
                + "  \"intervalDaysSummer\": 7,\n"
                + "  \"intervalDaysWinter\": 14\n"
                + "}\n"
                + "```";

        String cleaned = GeminiPlantAiService.cleanJsonContent(input);
        assertNotNull(cleaned);
        assertTrue(cleaned.startsWith("{"));
        assertTrue(cleaned.endsWith("}"));

        AiPlantAnalysisResult result = new Gson().fromJson(cleaned, AiPlantAnalysisResult.class);
        assertEquals("Монстера деліціоза", result.getName());
        assertEquals("Monstera deliciosa", result.getLatin());
        assertEquals(7, result.getIntervalDaysSummer());
        assertEquals(14, result.getIntervalDaysWinter());
    }

    @Test
    public void testCleanJsonWithGenericCodeBlock() {
        String input = "```\n"
                + "{\n"
                + "  \"name\": \"Фікус Бенджаміна\",\n"
                + "  \"difficulty\": \"середня\"\n"
                + "}\n"
                + "```";

        String cleaned = GeminiPlantAiService.cleanJsonContent(input);
        assertTrue(cleaned.startsWith("{"));
        assertTrue(cleaned.endsWith("}"));

        AiPlantAnalysisResult result = new Gson().fromJson(cleaned, AiPlantAnalysisResult.class);
        assertEquals("Фікус Бенджаміна", result.getName());
        assertEquals("середня", result.getDifficulty());
    }

    @Test
    public void testCleanJsonWithIntroAndOutroMarkdown() {
        String input = "Звісно! Ось детальний паспорт догляду для вашої рослини:\n\n"
                + "```json\n"
                + "{\n"
                + "  \"name\": \"Спатифілум\",\n"
                + "  \"latin\": \"Spathiphyllum\",\n"
                + "  \"lux\": 4000,\n"
                + "  \"humidity\": \"висока\"\n"
                + "}\n"
                + "```\n\n"
                + "Сподіваюся, цей паспорт буде корисним! Звертайтеся ще.";

        String cleaned = GeminiPlantAiService.cleanJsonContent(input);
        assertTrue(cleaned.startsWith("{"));
        assertTrue(cleaned.endsWith("}"));
        assertFalse(cleaned.contains("Звісно!"));
        assertFalse(cleaned.contains("Сподіваюся"));

        AiPlantAnalysisResult result = new Gson().fromJson(cleaned, AiPlantAnalysisResult.class);
        assertEquals("Спатифілум", result.getName());
        assertEquals("Spathiphyllum", result.getLatin());
        assertEquals(4000, result.getLux());
        assertEquals("висока", result.getHumidity());
    }

    @Test
    public void testCleanJsonWithInternalBracesInFields() {
        String input = "```json\n"
                + "{\n"
                + "  \"name\": \"Заміокулькас\",\n"
                + "  \"soil\": \"суміш {пісок:торф {1:1}}\",\n"
                + "  \"notes\": \"Поливати {тільки} після повного просихання ґрунту\"\n"
                + "}\n"
                + "```";

        String cleaned = GeminiPlantAiService.cleanJsonContent(input);
        assertTrue(cleaned.startsWith("{"));
        assertTrue(cleaned.endsWith("}"));
        assertTrue(cleaned.contains("{пісок:торф {1:1}}"));
        assertTrue(cleaned.contains("{тільки}"));

        AiPlantAnalysisResult result = new Gson().fromJson(cleaned, AiPlantAnalysisResult.class);
        assertEquals("Заміокулькас", result.getName());
        assertEquals("суміш {пісок:торф {1:1}}", result.getSoil());
        assertEquals("Поливати {тільки} після повного просихання ґрунту", result.getNotes());
    }

    @Test
    public void testCleanJsonEdgeCases() {
        // Null should return "{}"
        assertEquals("{}", GeminiPlantAiService.cleanJsonContent(null));

        // Empty should return ""
        assertEquals("", GeminiPlantAiService.cleanJsonContent(""));
        assertEquals("", GeminiPlantAiService.cleanJsonContent("   "));

        // Pure JSON without fences
        String pure = "{\"name\": \"Сансевієрія\"}";
        assertEquals(pure, GeminiPlantAiService.cleanJsonContent(pure));
    }

    // =========================================================================
    // 2. GitHubUpdateManager.isNewerVersion tests
    // =========================================================================

    @Test
    public void testIsNewerVersionTags() {
        // "v1.0.1" vs "1.0.0" -> newer
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.0.1", "1.0.0"));

        // "v2.0" vs "1.9.9" -> newer
        assertTrue(GitHubUpdateManager.isNewerVersion("v2.0", "1.9.9"));

        // "1.0.10" vs "1.0.9" -> newer (numeric comparison, not lexicographic)
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.10", "1.0.9"));

        // "v1.0.0-rc1" vs "1.0.0" -> NOT newer than stable 1.0.0
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0-rc1", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0-beta", "1.0.0"));

        // Null and empty values
        assertFalse(GitHubUpdateManager.isNewerVersion(null, "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.1", null));
        assertFalse(GitHubUpdateManager.isNewerVersion(null, null));
        assertFalse(GitHubUpdateManager.isNewerVersion("", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", ""));
        assertFalse(GitHubUpdateManager.isNewerVersion("", ""));

        // Identical versions
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.0", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "v1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v2.5.3", "v2.5.3"));

        // Older versions
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.1"));
        assertFalse(GitHubUpdateManager.isNewerVersion("v0.9.9", "1.0.0"));
        assertFalse(GitHubUpdateManager.isNewerVersion("1.9.9", "2.0.0"));
    }

    // =========================================================================
    // 3. PlantCatalogRepository Cyrillic Casing & Ukrainian Letters tests
    // =========================================================================

    @Test
    public void testCyrillicCasingSearch() {
        // Lowercase
        List<CatalogPlant> lower = PlantCatalogRepository.filter("монстера", "Всі");
        assertFalse(lower.isEmpty());

        // Capitalized
        List<CatalogPlant> capitalized = PlantCatalogRepository.filter("Монстера", "Всі");
        assertFalse(capitalized.isEmpty());

        // UPPERCASE
        List<CatalogPlant> upper = PlantCatalogRepository.filter("МОНСТЕРА", "Всі");
        assertFalse(upper.isEmpty());

        // Mixed case
        List<CatalogPlant> mixed = PlantCatalogRepository.filter("мОнСтЕрА", "Всі");
        assertFalse(mixed.isEmpty());

        // All should return the exact same count
        assertEquals(lower.size(), capitalized.size());
        assertEquals(lower.size(), upper.size());
        assertEquals(lower.size(), mixed.size());
    }

    @Test
    public void testUkrainianLetterI() {
        // "деліціоза" / "ДЕЛІЦІОЗА" containing "і"
        List<CatalogPlant> resLower = PlantCatalogRepository.filter("деліціоза", "Всі");
        List<CatalogPlant> resUpper = PlantCatalogRepository.filter("ДЕЛІЦІОЗА", "Всі");
        assertFalse(resLower.isEmpty());
        assertEquals(resLower.size(), resUpper.size());
        assertTrue(resLower.get(0).getName().contains("деліціоза"));

        // "спатифілум" containing "і"
        List<CatalogPlant> spath = PlantCatalogRepository.filter("спатифілум", "Всі");
        assertFalse(spath.isEmpty());
        assertTrue(spath.get(0).getName().contains("Спатифілум"));
    }

    @Test
    public void testUkrainianLetterYI() {
        // "Ароїдні" containing "ї"
        List<CatalogPlant> resLower = PlantCatalogRepository.filter("ароїдні", "Всі");
        List<CatalogPlant> resUpper = PlantCatalogRepository.filter("АРОЇДНІ", "Всі");
        assertFalse(resLower.isEmpty());
        assertEquals(resLower.size(), resUpper.size());

        // "фракції" (in Phalaenopsis soil description)
        List<CatalogPlant> phalaenopsis = PlantCatalogRepository.filter("фракції", "Всі");
        assertFalse(phalaenopsis.isEmpty());
        assertTrue(phalaenopsis.get(0).getName().contains("Фаленопсис"));

        // "м'якої" (in Calathea description)
        List<CatalogPlant> calathea = PlantCatalogRepository.filter("м'якої", "Всі");
        assertFalse(calathea.isEmpty());
        assertTrue(calathea.get(0).getName().contains("Калатея"));
    }

    @Test
    public void testUkrainianLetterYE() {
        // "замієлистий" containing "є"
        List<CatalogPlant> resLower = PlantCatalogRepository.filter("замієлистий", "Всі");
        List<CatalogPlant> resUpper = PlantCatalogRepository.filter("ЗАМІЄЛИСТИЙ", "Всі");
        assertFalse(resLower.isEmpty());
        assertEquals(resLower.size(), resUpper.size());
        assertTrue(resLower.get(0).getName().contains("Заміокулькас"));

        // "сансевієрія" containing "і" and "є"
        List<CatalogPlant> sansev = PlantCatalogRepository.filter("сансевієрія", "Всі");
        assertFalse(sansev.isEmpty());
        assertTrue(sansev.get(0).getName().contains("Сансевієрія"));
    }

    @Test
    public void testUkrainianLetterGHE() {
        // "ґрунт" / "Ґрунт" / "ҐРУНТ" containing "ґ"
        List<CatalogPlant> resLower = PlantCatalogRepository.filter("ґрунт", "Всі");
        List<CatalogPlant> resCapital = PlantCatalogRepository.filter("Ґрунт", "Всі");
        List<CatalogPlant> resUpper = PlantCatalogRepository.filter("ҐРУНТ", "Всі");

        assertFalse("Search with 'ґрунт' should find plants with soil recipes", resLower.isEmpty());
        assertEquals(resLower.size(), resCapital.size());
        assertEquals(resLower.size(), resUpper.size());

        // Just the letter "ґ"
        List<CatalogPlant> resSingleLetter = PlantCatalogRepository.filter("ґ", "Всі");
        assertFalse(resSingleLetter.isEmpty());
        assertEquals(resLower.size(), resSingleLetter.size());
    }

    // =========================================================================
    // 4. Simultaneous Category + Search filtering tests
    // =========================================================================

    @Test
    public void testCategoryAndSearchSimultaneously() {
        // Matching category + matching search
        List<CatalogPlant> aroidsMonstera = PlantCatalogRepository.filter("монстера", "Ароїдні");
        assertFalse(aroidsMonstera.isEmpty());
        for (CatalogPlant p : aroidsMonstera) {
            assertEquals("Ароїдні", p.getCategory());
            assertTrue(p.getName().toLowerCase().contains("монстера")
                    || p.getLatin().toLowerCase().contains("monstera"));
        }

        // Mismatched category + search -> empty result
        List<CatalogPlant> succulentMonstera = PlantCatalogRepository.filter("монстера", "Сукуленти");
        assertTrue("Monstera is not a succulent", succulentMonstera.isEmpty());

        // Succulents + Crassula
        List<CatalogPlant> succulentCrassula = PlantCatalogRepository.filter("красула", "Сукуленти");
        assertFalse(succulentCrassula.isEmpty());
        assertEquals("Сукуленти", succulentCrassula.get(0).getCategory());

        // Ficus category + query
        List<CatalogPlant> ficusElastica = PlantCatalogRepository.filter("каучуконосний", "Фікуси");
        assertFalse(ficusElastica.isEmpty());
        assertEquals("cat_ficus_elastica", ficusElastica.get(0).getId());

        // Ficus category + query that does not belong to Ficus
        List<CatalogPlant> ficusAloe = PlantCatalogRepository.filter("алое", "Фікуси");
        assertTrue(ficusAloe.isEmpty());

        // "Всі" should include all matching plants regardless of category
        List<CatalogPlant> allFicuses = PlantCatalogRepository.filter("фікус", "Всі");
        assertTrue(allFicuses.size() >= 2);

        // Empty query + specific category -> returns all plants in that category
        List<CatalogPlant> allSucculents = PlantCatalogRepository.filter("", "Сукуленти");
        assertFalse(allSucculents.isEmpty());
        for (CatalogPlant p : allSucculents) {
            assertEquals("Сукуленти", p.getCategory());
        }

        // Empty query + "Всі" -> returns all plants in catalog
        List<CatalogPlant> everything = PlantCatalogRepository.filter("", "Всі");
        assertEquals(PlantCatalogRepository.getAllPlants().size(), everything.size());

        // Null query + null category -> behaves as all plants
        List<CatalogPlant> nullQueryNullCat = PlantCatalogRepository.filter(null, null);
        assertEquals(PlantCatalogRepository.getAllPlants().size(), nullQueryNullCat.size());

        // Non-existent category
        List<CatalogPlant> nonExistentCat = PlantCatalogRepository.filter("", "НевідомаКатегорія");
        assertTrue(nonExistentCat.isEmpty());
    }
}
