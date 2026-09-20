package com.plantshelf.app.feature.lightmeter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.plantshelf.app.R;
import com.plantshelf.app.databinding.ActivityLightMeterBinding;
import com.plantshelf.app.domain.model.LightAnalysisResult;
import com.plantshelf.app.domain.usecase.AnalyzeLightLevelUseCase;

import java.util.Locale;

/**
 * Feature: Light Meter (Lux Sensor) Fragment.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Encapsulating sensor listening in a lifecycle-aware Fragment prevents sensor battery drain
 * by safely registering onResume and unregistering onPause within the fragment lifecycle.
 */
public class LightMeterFragment extends Fragment implements SensorEventListener {

    private ActivityLightMeterBinding binding;
    private SensorManager sensorManager;
    private Sensor lightSensor;

    private final AnalyzeLightLevelUseCase analyzeLightLevelUseCase = new AnalyzeLightLevelUseCase();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ActivityLightMeterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbarLight.setNavigationOnClickListener(v -> {
            try {
                if (!Navigation.findNavController(v).navigateUp()) {
                    requireActivity().onBackPressed();
                }
            } catch (Exception e) {
                requireActivity().onBackPressed();
            }
        });

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        if (lightSensor == null) {
            binding.tvLuxValue.setText("—");
            binding.tvLightCategory.setText(R.string.light_sensor_not_available);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT && isAdded() && binding != null) {
            float lux = event.values[0];
            updateLuxDisplay(lux);
        }
    }

    private void updateLuxDisplay(float lux) {
        LightAnalysisResult analysis = analyzeLightLevelUseCase.execute(lux);
        binding.tvLuxValue.setText(String.format(Locale.getDefault(), "%,d lx", analysis.getLux()));
        binding.progressLux.setProgress(analysis.getProgressPercentage());
        binding.tvLightCategory.setText(analysis.getDisplayCategory());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
