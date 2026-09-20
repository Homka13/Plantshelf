package com.plantshelf.app.domain.model;

import androidx.annotation.NonNull;

/**
 * Domain model representing an ambient illumination measurement analysis.
 */
public final class LightAnalysisResult {

    public enum IlluminationZone {
        DEEP_SHADE,
        MODERATE_INDIRECT,
        BRIGHT_INDIRECT,
        VERY_BRIGHT_INDIRECT,
        DIRECT_SUN
    }

    private final int lux;
    private final float footCandles;
    private final IlluminationZone zone;
    private final String displayCategory;
    private final String recommendedPlants;
    private final int progressPercentage;

    public LightAnalysisResult(
            int lux,
            float footCandles,
            @NonNull IlluminationZone zone,
            @NonNull String displayCategory,
            @NonNull String recommendedPlants,
            int progressPercentage
    ) {
        this.lux = lux;
        this.footCandles = footCandles;
        this.zone = zone;
        this.displayCategory = displayCategory;
        this.recommendedPlants = recommendedPlants;
        this.progressPercentage = progressPercentage;
    }

    public int getLux() {
        return lux;
    }

    public float getFootCandles() {
        return footCandles;
    }

    @NonNull
    public IlluminationZone getZone() {
        return zone;
    }

    @NonNull
    public String getDisplayCategory() {
        return displayCategory;
    }

    @NonNull
    public String getRecommendedPlants() {
        return recommendedPlants;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }
}
