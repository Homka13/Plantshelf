package com.plantshelf.app.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.plantshelf.app.domain.model.CareScheduleResult;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Domain Use Case: Computes fertilization intervals, next due dates, and seasonal feeding rules.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Over-fertilization during winter dormancy burns root systems and creates toxic mineral build-up.
 * When winter interval is 0 or unconfigured, fertilizing is suspended until spring.
 * In the upcoming "PillShelf" context, this mirrors cyclically dosed therapies (e.g. 3-week on, 1-week off).
 */
public final class CalculateNextFertilizingUseCase {

    public static final int DEFAULT_SUMMER_FERT_INTERVAL_DAYS = 14;

    @NonNull
    public CareScheduleResult execute(
            @Nullable String lastFertilizedDateStr,
            int summerIntervalDays,
            int winterIntervalDays
    ) {
        return execute(lastFertilizedDateStr, summerIntervalDays, winterIntervalDays, LocalDate.now());
    }

    @NonNull
    public CareScheduleResult execute(
            @Nullable String lastFertilizedDateStr,
            int summerIntervalDays,
            int winterIntervalDays,
            @NonNull LocalDate referenceDate
    ) {
        boolean isWinter = CalculateNextWateringUseCase.isWinterMonth(referenceDate.getMonthValue());

        // In winter, if winter interval is 0, fertilization is suspended for dormancy
        int effectiveInterval;
        if (isWinter) {
            effectiveInterval = winterIntervalDays > 0 ? winterIntervalDays : 0;
        } else {
            effectiveInterval = summerIntervalDays > 0 ? summerIntervalDays : DEFAULT_SUMMER_FERT_INTERVAL_DAYS;
        }

        if (effectiveInterval == 0 && isWinter) {
            // Dormancy: no fertilizing needed currently
            return new CareScheduleResult(
                    Integer.MAX_VALUE,
                    CareScheduleResult.Status.UPCOMING,
                    null,
                    0,
                    true
            );
        }

        if (lastFertilizedDateStr == null || lastFertilizedDateStr.trim().isEmpty()) {
            return new CareScheduleResult(
                    0,
                    CareScheduleResult.Status.NEVER_CARED,
                    referenceDate,
                    effectiveInterval,
                    isWinter
            );
        }

        try {
            String clean = lastFertilizedDateStr.trim();
            if (clean.length() > 10) clean = clean.substring(0, 10);
            LocalDate lastFert = LocalDate.parse(clean);

            LocalDate nextDue = lastFert.plusDays(effectiveInterval);
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
        } catch (Exception e) {
            return new CareScheduleResult(0, CareScheduleResult.Status.DUE_TODAY, referenceDate, effectiveInterval, isWinter);
        }
    }
}
