package com.plantshelf.app.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.plantshelf.app.domain.model.CareScheduleResult;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Domain Use Case: Computes watering intervals and next due dates based on seasonal dormancy rules.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Biological care cycles depend fundamentally on ambient season: during temperate winter months
 * (December, January, February in the Northern Hemisphere), indoor plant transpiration drops
 * dramatically, requiring lengthened intervals to prevent root rot.
 *
 * <p>Extracting this mathematical logic from the persistence entity (PlantEntity) into a pure
 * domain use case ensures:
 * 1. Deterministic unit testing without database or Android framework mocks.
 * 2. Seamless adaptability to recurring health regimes in "PillShelf" (e.g. interval-based medication).
 */
public final class CalculateNextWateringUseCase {

    public static final int DEFAULT_WATER_INTERVAL_DAYS = 7;

    /**
     * Calculates the care schedule result relative to today's date.
     */
    @NonNull
    public CareScheduleResult execute(
            @Nullable String lastWateredDateStr,
            int summerIntervalDays,
            int winterIntervalDays
    ) {
        return execute(lastWateredDateStr, summerIntervalDays, winterIntervalDays, LocalDate.now());
    }

    /**
     * Pure calculation method accepting a target reference date for testability and projection.
     */
    @NonNull
    public CareScheduleResult execute(
            @Nullable String lastWateredDateStr,
            int summerIntervalDays,
            int winterIntervalDays,
            @NonNull LocalDate referenceDate
    ) {
        boolean isWinter = isWinterMonth(referenceDate.getMonthValue());
        int effectiveInterval = resolveInterval(isWinter, summerIntervalDays, winterIntervalDays);

        if (lastWateredDateStr == null || lastWateredDateStr.trim().isEmpty()) {
            return new CareScheduleResult(
                    0,
                    CareScheduleResult.Status.NEVER_CARED,
                    referenceDate,
                    effectiveInterval,
                    isWinter
            );
        }

        try {
            LocalDate lastWatered = parseIsoDate(lastWateredDateStr);
            LocalDate nextDue = lastWatered.plusDays(effectiveInterval);
            int daysRemaining = (int) ChronoUnit.DAYS.between(referenceDate, nextDue);

            CareScheduleResult.Status status;
            if (daysRemaining < 0) {
                status = CareScheduleResult.Status.OVERDUE;
            } else if (daysRemaining == 0) {
                status = CareScheduleResult.Status.DUE_TODAY;
            } else {
                status = CareScheduleResult.Status.UPCOMING;
            }

            return new CareScheduleResult(daysRemaining, status, nextDue, effectiveInterval, isWinter);
        } catch (Exception parseException) {
            // Fallback for corrupted date formats: treat as requiring immediate watering today
            return new CareScheduleResult(
                    0,
                    CareScheduleResult.Status.DUE_TODAY,
                    referenceDate,
                    effectiveInterval,
                    isWinter
            );
        }
    }

    /**
     * Determines whether the given month index (1..12) corresponds to meteorological winter.
     */
    public static boolean isWinterMonth(int monthValue) {
        return monthValue == 12 || monthValue == 1 || monthValue == 2;
    }

    private static int resolveInterval(boolean isWinter, int summerInterval, int winterInterval) {
        int interval = isWinter ? winterInterval : summerInterval;
        if (interval <= 0) {
            interval = summerInterval > 0 ? summerInterval : DEFAULT_WATER_INTERVAL_DAYS;
        }
        return interval > 0 ? interval : DEFAULT_WATER_INTERVAL_DAYS;
    }

    /**
     * Determines whether watering is scheduled or overdue on the given target date.
     *
     * <p>Rationale (MIT Comm Lab Style - "Why over What"):
     * Calendar grids require projecting forward recurring care dates without creating
     * unbounded database records. When viewing the current date, any overdue items must
     * appear immediately. For future dates, events recur periodically according to the
     * seasonal interval.
     */
    public boolean isDueOn(
            @Nullable String lastWateredDateStr,
            int summerIntervalDays,
            int winterIntervalDays,
            @NonNull LocalDate targetDate,
            boolean isTargetToday
    ) {
        boolean isWinter = isWinterMonth(targetDate.getMonthValue());
        int effectiveInterval = resolveInterval(isWinter, summerIntervalDays, winterIntervalDays);

        if (lastWateredDateStr == null || lastWateredDateStr.trim().isEmpty()) {
            return isTargetToday;
        }

        try {
            LocalDate lastDate = parseIsoDate(lastWateredDateStr);
            LocalDate firstDueDate = lastDate.plusDays(effectiveInterval);

            if (isTargetToday) {
                // If checking today, include overdue tasks
                return !targetDate.isBefore(firstDueDate);
            } else if (targetDate.isBefore(firstDueDate)) {
                return false;
            } else {
                long daysDiff = ChronoUnit.DAYS.between(firstDueDate, targetDate);
                return (daysDiff % effectiveInterval) == 0;
            }
        } catch (Exception e) {
            return isTargetToday;
        }
    }

    private static LocalDate parseIsoDate(String rawDate) {
        String clean = rawDate.trim();
        if (clean.length() > 10) {
            clean = clean.substring(0, 10);
        }
        return LocalDate.parse(clean);
    }
}
