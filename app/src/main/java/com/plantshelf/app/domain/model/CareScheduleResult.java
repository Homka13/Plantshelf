package com.plantshelf.app.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

/**
 * Pure domain model encapsulating the computed schedule status of a care action.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Encapsulating care schedule calculations into an immutable domain object divorces
 * raw date math from the UI and database layers. This enables identical scheduling logic
 * to be reused across Android widgets, Notification WorkManagers, calendar sync tasks,
 * and future medication tracking ("PillShelf").
 */
public final class CareScheduleResult {

    public enum Status {
        /** Care action has never been recorded for this item. */
        NEVER_CARED,
        /** Care is due today. */
        DUE_TODAY,
        /** Care is overdue by one or more days. */
        OVERDUE,
        /** Care is scheduled for a future date. */
        UPCOMING
    }

    private final int daysRemaining;
    private final Status status;
    private final LocalDate nextDueDate;
    private final int effectiveIntervalDays;
    private final boolean isWinterSeason;

    public CareScheduleResult(
            int daysRemaining,
            @NonNull Status status,
            @Nullable LocalDate nextDueDate,
            int effectiveIntervalDays,
            boolean isWinterSeason
    ) {
        this.daysRemaining = daysRemaining;
        this.status = status;
        this.nextDueDate = nextDueDate;
        this.effectiveIntervalDays = effectiveIntervalDays;
        this.isWinterSeason = isWinterSeason;
    }

    /**
     * Number of days until due.
     * <ul>
     *     <li>Positive: Days left in the future</li>
     *     <li>0: Due today</li>
     *     <li>Negative: Days overdue</li>
     * </ul>
     */
    public int getDaysRemaining() {
        return daysRemaining;
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @Nullable
    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public int getEffectiveIntervalDays() {
        return effectiveIntervalDays;
    }

    public boolean isWinterSeason() {
        return isWinterSeason;
    }

    public boolean isActionRequiredNow() {
        return status == Status.DUE_TODAY || status == Status.OVERDUE || status == Status.NEVER_CARED;
    }
}
