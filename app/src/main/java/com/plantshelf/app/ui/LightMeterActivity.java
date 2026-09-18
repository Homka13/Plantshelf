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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            updateLuxDisplay(lux);
        }
    }

    private void updateLuxDisplay(float lux) {
        int roundedLux = Math.round(lux);
        binding.tvLuxValue.setText(String.format(Locale.getDefault(), "%,d lx", roundedLux));
        binding.progressLux.setProgress(Math.min(roundedLux, 30000));

        String category;
        if (roundedLux < 500) {
            category = "🌑 Низьке світло (глибока тінь / північні кімнати)";
        } else if (roundedLux < 2500) {
            category = "⛅ Помірне розсіяне світло (півтінь / 2-3 метри від вікна)";
        } else if (roundedLux < 10000) {
            category = "☀️ Яскраве розсіяне світло (ідеально для більшості рослин)";
        } else if (roundedLux < 25000) {
            category = "🌟 Дуже яскраве непряме світло (Монстери, Фікуси, Східні/Західні вікна)";
        } else {
            category = "🔥 Пряме сонячне світло (Південні вікна, Сукуленти, Кактуси)";
        }
        binding.tvLightCategory.setText(category);
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
