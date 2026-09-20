package com.plantshelf.app.domain.usecase;

import androidx.annotation.NonNull;

import com.plantshelf.app.domain.model.LightAnalysisResult;

/**
 * Domain Use Case: Evaluates ambient illuminance (lux) readings from hardware sensors.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Raw lux values (e.g. "3400 lx") are unintuitive for typical growers.
 * Converting raw lux into horticultural photobiology classifications and foot-candles (fc)
 * decouples sensor hardware sampling from the UI rendering presentation.
 */
public final class AnalyzeLightLevelUseCase {

    /** 1 Lux is approximately 0.0929 Foot-Candles */
    private static final float LUX_TO_FOOT_CANDLES_RATIO = 0.092903f;

    /** Scale maximum for progress indicator visualization (lux) */
    public static final int MAX_PROGRESS_LUX = 30000;

    @NonNull
    public LightAnalysisResult execute(float rawLux) {
        int roundedLux = Math.max(0, Math.round(rawLux));
        float footCandles = roundedLux * LUX_TO_FOOT_CANDLES_RATIO;
        int progress = Math.min(roundedLux, MAX_PROGRESS_LUX);

        LightAnalysisResult.IlluminationZone zone;
        String category;
        String recommendedPlants;

        if (roundedLux < 500) {
            zone = LightAnalysisResult.IlluminationZone.DEEP_SHADE;
            category = "🌑 Низьке світло (глибока тінь / північні кімнати)";
            recommendedPlants = "Сансевієрія, Заміокулькас, Аспідістра, Сциндапсус";
        } else if (roundedLux < 2500) {
            zone = LightAnalysisResult.IlluminationZone.MODERATE_INDIRECT;
            category = "⛅ Помірне розсіяне світло (півтінь / 2-3 метри від вікна)";
            recommendedPlants = "Епіпремнум, Філодендрон, Аглаонема, Калатея, Спатифілум";
        } else if (roundedLux < 10000) {
            zone = LightAnalysisResult.IlluminationZone.BRIGHT_INDIRECT;
            category = "☀️ Яскраве розсіяне світло (ідеально для більшості рослин)";
            recommendedPlants = "Монстера делікатесна, Фікус Бенджаміна, Антуріум, Пеперомія, Пілея";
        } else if (roundedLux < 25000) {
            zone = LightAnalysisResult.IlluminationZone.VERY_BRIGHT_INDIRECT;
            category = "🌟 Дуже яскраве непряме світло (Монстери, Фікуси, Східні/Західні вікна)";
            recommendedPlants = "Фікус ліроподібний, Стреліція, Кімнатний цитрус, Гібіскус, Банан";
        } else {
            zone = LightAnalysisResult.IlluminationZone.DIRECT_SUN;
            category = "🔥 Пряме сонячне світло (Південні вікна, Сукуленти, Кактуси)";
            recommendedPlants = "Кактуси, Ехеверія, Красула, Олива, Алое вера, Бугенвілія";
        }

        return new LightAnalysisResult(
                roundedLux,
                footCandles,
                zone,
                category,
                recommendedPlants,
                progress
        );
    }
}
