package com.plantshelf.app.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.plantshelf.app.feature.calendar.CareCalendarFragment;

/**
 * Backward-compatibility wrapper for CareCalendar.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Retained for backward-compatibility with deep links and widget actions while delegating
 * all calendar presentation to {@link CareCalendarFragment}.
 */
public class CareCalendarActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new CareCalendarFragment())
                    .commit();
        }
    }

    private boolean isWateringDueOn(com.plantshelf.app.data.entity.PlantEntity plant, String targetDateStr, boolean isTargetToday) {
        if (plant.isQuarantined()) return false;
        try {
            java.time.LocalDate targetDate = java.time.LocalDate.parse(targetDateStr);
            com.plantshelf.app.domain.usecase.CalculateNextWateringUseCase useCase =
                    new com.plantshelf.app.domain.usecase.CalculateNextWateringUseCase();
            return useCase.isDueOn(
                    plant.getLastWatered(),
                    plant.getIntervalDays(),
                    plant.getIntervalDaysWinter(),
                    targetDate,
                    isTargetToday
            );
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFertilizingDueOn(com.plantshelf.app.data.entity.PlantEntity plant, String targetDateStr, boolean isTargetToday) {
        if (plant.isQuarantined()) return false;
        try {
            java.time.LocalDate targetDate = java.time.LocalDate.parse(targetDateStr);
            com.plantshelf.app.domain.usecase.CalculateNextFertilizingUseCase useCase =
                    new com.plantshelf.app.domain.usecase.CalculateNextFertilizingUseCase();
            return useCase.isDueOn(
                    plant.getLastFert(),
                    plant.getEffectiveFertilizeIntervalSummerDays(),
                    plant.getFertilizeIntervalWinterDays(),
                    targetDate,
                    isTargetToday
            );
        } catch (Exception e) {
            return false;
        }
    }
}
