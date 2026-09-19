package com.plantshelf.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.plantshelf.app.data.entity.PlantEntity;

import org.junit.Test;

import java.time.LocalDate;

/**
 * Unit tests verifying scheduling calculations in PlantEntity
 * using java.time.LocalDate and ChronoUnit.DAYS.
 */
public class PlantEntityScheduleTest {

    @Test
    public void testWateredTodayReturnsExactIntervalDays() {
        PlantEntity plant = new PlantEntity("p_today");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);

        LocalDate today = LocalDate.now();
        plant.setLastWatered(today.toString());

        int daysLeft = plant.getDaysUntilWatering();
        assertEquals("Watered today with 7-day interval should have exactly 7 days remaining", 7, daysLeft);
    }

    @Test
    public void testOneDayIntervalWateredToday() {
        PlantEntity plant = new PlantEntity("p_one_day");
        plant.setIntervalDays(1);
        plant.setIntervalDaysWinter(1);

        LocalDate today = LocalDate.now();
        plant.setLastWatered(today.toString());

        int daysLeft = plant.getDaysUntilWatering();
        assertEquals("Watered today with 1-day interval should have exactly 1 day remaining", 1, daysLeft);
    }

    @Test
    public void testDueTodayWhenIntervalElapsed() {
        PlantEntity plant = new PlantEntity("p_due_today");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);

        LocalDate today = LocalDate.now();
        LocalDate watered7DaysAgo = today.minusDays(7);
        plant.setLastWatered(watered7DaysAgo.toString());

        int daysLeft = plant.getDaysUntilWatering();
        assertEquals("Watered 7 days ago with 7-day interval should have 0 days remaining (due today)", 0, daysLeft);
    }

    @Test
    public void testOverduePlantCalculation() {
        PlantEntity plant = new PlantEntity("p_overdue");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);

        LocalDate today = LocalDate.now();
        LocalDate watered12DaysAgo = today.minusDays(12);
        plant.setLastWatered(watered12DaysAgo.toString());

        int daysLeft = plant.getDaysUntilWatering();
        assertEquals("Watered 12 days ago with 7-day interval should be -5 days overdue", -5, daysLeft);
    }

    @Test
    public void testSummerVsWinterIntervalSeasonalAdjustment() {
        PlantEntity plant = new PlantEntity("p_seasons");
        plant.setIntervalDays(5);       // Summer
        plant.setIntervalDaysWinter(12); // Winter

        LocalDate today = LocalDate.now();
        plant.setLastWatered(today.toString());

        int month = today.getMonthValue();
        boolean isWinter = (month == 12 || month == 1 || month == 2);
        int expectedInterval = isWinter ? 12 : 5;

        assertEquals(expectedInterval, plant.getDaysUntilWatering());
    }

    @Test
    public void testQuarantineStatus() {
        PlantEntity plant = new PlantEntity("p_quar");
        assertFalse(plant.isQuarantined());

        plant.setQuarantineUntil("2026-10-01");
        plant.setQuarantineReason("Павутинний кліщ");
        assertTrue(plant.isQuarantined());
        assertEquals("Павутинний кліщ", plant.getQuarantineReason());

        plant.setQuarantineUntil("");
        assertFalse(plant.isQuarantined());

        plant.setQuarantineUntil(null);
        assertFalse(plant.isQuarantined());
    }

    @Test
    public void testNullEmptyAndMalformedDatesGracefulFallback() {
        PlantEntity plant = new PlantEntity("p_null");

        plant.setLastWatered(null);
        assertEquals(0, plant.getDaysUntilWatering());

        plant.setLastWatered("   ");
        assertEquals(0, plant.getDaysUntilWatering());

        plant.setLastWatered("not-a-valid-date");
        assertEquals(0, plant.getDaysUntilWatering());
    }

    @Test
    public void testIsoDateTimeStringParsing() {
        PlantEntity plant = new PlantEntity("p_iso");
        plant.setIntervalDays(7);
        plant.setIntervalDaysWinter(7);

        LocalDate today = LocalDate.now();
        String isoDateTime = today.toString() + "T14:30:00.000Z";
        plant.setLastWatered(isoDateTime);

        assertEquals("Should parse date from ISO-8601 timestamp prefix", 7, plant.getDaysUntilWatering());
    }
}
