package com.plantshelf.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.domain.model.CareScheduleResult;
import com.plantshelf.app.domain.model.LightAnalysisResult;
import com.plantshelf.app.domain.usecase.AnalyzeLightLevelUseCase;
import com.plantshelf.app.domain.usecase.CalculateNextFertilizingUseCase;
import com.plantshelf.app.domain.usecase.CalculateNextWateringUseCase;
import com.plantshelf.app.domain.usecase.FilterPlantsUseCase;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit test suite verifying domain use cases and biological care models.
 * Tests edge cases like season transitions, null dates, dormancy periods, and search predicates.
 */
public class DomainUseCasesTest {

    @Test
    public void testWateringSummerVsWinterIntervals() {
        CalculateNextWateringUseCase useCase = new CalculateNextWateringUseCase();

        // Reference summer date (July 15)
        LocalDate summerDate = LocalDate.of(2026, 7, 15);
        // Plant was watered on July 10 (5 days ago)
        CareScheduleResult summerResult = useCase.execute(
                "2026-07-10",
                7,   // summer interval
                14,  // winter interval
                summerDate
        );

        assertFalse("July is not winter", summerResult.isWinterSeason());
        assertEquals("Should use summer interval of 7", 7, summerResult.getEffectiveIntervalDays());
        assertEquals("5 days elapsed, 2 days remaining", 2, summerResult.getDaysRemaining());
        assertEquals(CareScheduleResult.Status.UPCOMING, summerResult.getStatus());

        // Reference winter date (January 15)
        LocalDate winterDate = LocalDate.of(2027, 1, 15);
        // Plant was watered on January 10 (5 days ago)
        CareScheduleResult winterResult = useCase.execute(
                "2027-01-10",
                7,   // summer interval
                14,  // winter interval
                winterDate
        );

        assertTrue("January is winter season", winterResult.isWinterSeason());
        assertEquals("Should use winter interval of 14", 14, winterResult.getEffectiveIntervalDays());
        assertEquals("5 days elapsed of 14, 9 days remaining", 9, winterResult.getDaysRemaining());
    }

    @Test
    public void testWateringOverdueCalculation() {
        CalculateNextWateringUseCase useCase = new CalculateNextWateringUseCase();
        LocalDate today = LocalDate.of(2026, 6, 20);

        // Watered 10 days ago with 7 day interval -> 3 days overdue
        CareScheduleResult result = useCase.execute("2026-06-10", 7, 14, today);

        assertEquals(CareScheduleResult.Status.OVERDUE, result.getStatus());
        assertEquals(-3, result.getDaysRemaining());
        assertTrue(result.isActionRequiredNow());
    }

    @Test
    public void testFertilizingWinterDormancy() {
        CalculateNextFertilizingUseCase useCase = new CalculateNextFertilizingUseCase();
        LocalDate winterDay = LocalDate.of(2026, 12, 10);

        // When winter interval is 0, plant is in winter dormancy
        CareScheduleResult result = useCase.execute("2026-11-20", 14, 0, winterDay);

        assertTrue("Winter dormancy should suppress fertilizing", result.isWinterSeason());
        assertEquals("Dormancy has 0 effective interval", 0, result.getEffectiveIntervalDays());
        assertFalse("Action is not required during dormancy", result.isActionRequiredNow());
    }

    @Test
    public void testAnalyzeLightLevelPhotobiology() {
        AnalyzeLightLevelUseCase useCase = new AnalyzeLightLevelUseCase();

        // 300 lux: deep shade
        LightAnalysisResult lowLight = useCase.execute(300f);
        assertEquals(LightAnalysisResult.IlluminationZone.DEEP_SHADE, lowLight.getZone());
        assertTrue(lowLight.getFootCandles() < 40f);

        // 5000 lux: bright indirect
        LightAnalysisResult brightLight = useCase.execute(5000f);
        assertEquals(LightAnalysisResult.IlluminationZone.BRIGHT_INDIRECT, brightLight.getZone());
        assertTrue(brightLight.getRecommendedPlants().contains("Монстера"));

        // 35000 lux: direct sun
        LightAnalysisResult directSun = useCase.execute(35000f);
        assertEquals(LightAnalysisResult.IlluminationZone.DIRECT_SUN, directSun.getZone());
        assertTrue(directSun.getRecommendedPlants().contains("Кактуси"));
    }

    @Test
    public void testFilterPlantsUseCaseCompositeQuery() {
        FilterPlantsUseCase filterUseCase = new FilterPlantsUseCase();

        List<PlantEntity> plants = new ArrayList<>();
        PlantEntity p1 = new PlantEntity("p1");
        p1.setName("Монстера делікатесна");
        p1.setVariety("Альба");
        p1.setCategoryId("shelf_livingroom");

        PlantEntity p2 = new PlantEntity("p2");
        p2.setName("Фікус еластика");
        p2.setCategoryId("shelf_livingroom");

        PlantEntity p3 = new PlantEntity("p3");
        p3.setName("Філодендрон рожевий");
        p3.setQuarantineUntil("2026-10-01");
        p3.setCategoryId("shelf_bedroom");

        plants.add(p1);
        plants.add(p2);
        plants.add(p3);

        // Filter by shelf
        List<PlantEntity> shelfFiltered = filterUseCase.execute(plants, "shelf_livingroom", null, false);
        assertEquals(2, shelfFiltered.size());

        // Filter by text search
        List<PlantEntity> searchFiltered = filterUseCase.execute(plants, null, "альба", false);
        assertEquals(1, searchFiltered.size());
        assertEquals("p1", searchFiltered.get(0).getId());

        // Filter by quarantine only
        List<PlantEntity> quarantineFiltered = filterUseCase.execute(plants, null, null, true);
        assertEquals(1, quarantineFiltered.size());
        assertEquals("p3", quarantineFiltered.get(0).getId());
    }
}
