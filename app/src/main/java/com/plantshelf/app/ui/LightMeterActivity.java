package com.plantshelf.app.ui;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.plantshelf.app.R;
import com.plantshelf.app.databinding.ActivityLightMeterBinding;

import java.util.Locale;

public class LightMeterActivity extends AppCompatActivity implements SensorEventListener {

    private ActivityLightMeterBinding binding;
    private SensorManager sensorManager;
    private Sensor lightSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLightMeterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarLight);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        if (lightSensor == null) {
            binding.tvLuxValue.setText("—");
            binding.tvLightCategory.setText(R.string.light_sensor_not_available);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private final com.plantshelf.app.domain.usecase.AnalyzeLightLevelUseCase analyzeLightLevelUseCase =
            new com.plantshelf.app.domain.usecase.AnalyzeLightLevelUseCase();

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            updateLuxDisplay(lux);
        }
    }

    private void updateLuxDisplay(float lux) {
        com.plantshelf.app.domain.model.LightAnalysisResult analysis =
                analyzeLightLevelUseCase.execute(lux);

        binding.tvLuxValue.setText(String.format(Locale.getDefault(), "%,d lx", analysis.getLux()));
        binding.progressLux.setProgress(analysis.getProgressPercentage());
        binding.tvLightCategory.setText(analysis.getDisplayCategory());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
