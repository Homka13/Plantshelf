package com.plantshelf.app.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.plantshelf.app.feature.lightmeter.LightMeterFragment;

/**
 * Backward-compatibility wrapper for LightMeter.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Acts as a thin bridge for legacy intents while delegating UI and sensor lifecycle management
 * to {@link LightMeterFragment}.
 */
public class LightMeterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new LightMeterFragment())
                    .commit();
        }
    }
}
