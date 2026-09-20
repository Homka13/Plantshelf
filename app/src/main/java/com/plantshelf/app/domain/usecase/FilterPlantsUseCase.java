package com.plantshelf.app.domain.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.plantshelf.app.data.entity.PlantEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain Use Case: Filters and queries plant collections.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Decouples list filtering and searching algorithms from Android ViewModel state.
 * Allows independent testing of multi-field search matches (name, variety, latin, nickname)
 * and composite category/quarantine filtering predicates.
 */
public final class FilterPlantsUseCase {

    @NonNull
    public List<PlantEntity> execute(
            @Nullable List<PlantEntity> sourceList,
            @Nullable String selectedCategoryId,
            @Nullable String searchQuery,
            boolean quarantineOnly
    ) {
        if (sourceList == null || sourceList.isEmpty()) {
            return new ArrayList<>();
        }

        String query = searchQuery != null ? searchQuery.trim().toLowerCase() : "";
        List<PlantEntity> filtered = new ArrayList<>();

        for (PlantEntity plant : sourceList) {
            if (plant == null) continue;

            // 1. Quarantine isolation filter
            if (quarantineOnly && !plant.isQuarantined()) {
                continue;
            }

            // 2. Shelf / Room Category filter
            if (!quarantineOnly && selectedCategoryId != null && !selectedCategoryId.isEmpty()) {
                if (!selectedCategoryId.equals(plant.getCategoryId())) {
                    continue;
                }
            }

            // 3. Multi-field text search predicate
            if (!query.isEmpty()) {
                boolean matchesName = containsSubstring(plant.getName(), query);
                boolean matchesVariety = containsSubstring(plant.getVariety(), query);
                boolean matchesLatin = containsSubstring(plant.getLatin(), query);
                boolean matchesNickname = containsSubstring(plant.getNickname(), query);

                if (!matchesName && !matchesVariety && !matchesLatin && !matchesNickname) {
                    continue;
                }
            }

            filtered.add(plant);
        }

        return filtered;
    }

    private static boolean containsSubstring(@Nullable String text, @NonNull String query) {
        return text != null && text.toLowerCase().contains(query);
    }
}
