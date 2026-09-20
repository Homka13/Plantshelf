package com.plantshelf.app.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.plantshelf.app.feature.detail.PlantDetailFragment;

/**
 * Backward-compatibility wrapper for PlantDetail.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Retained to handle notification clicks, widget clicks, and external intents seamlessly
 * while forwarding {@link #EXTRA_PLANT_ID} into {@link PlantDetailFragment}.
 */
public class PlantDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PLANT_ID = "extra_plant_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            String plantId = getIntent().getStringExtra(EXTRA_PLANT_ID);
            if (plantId == null) {
                finish();
                return;
            }
            Bundle args = new Bundle();
            args.putString(PlantDetailFragment.ARG_PLANT_ID, plantId);
            PlantDetailFragment fragment = new PlantDetailFragment();
            fragment.setArguments(args);

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit();
        }
    }
}
