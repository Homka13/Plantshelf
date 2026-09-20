package com.plantshelf.app.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.plantshelf.app.feature.addedit.AddEditPlantFragment;

/**
 * Backward-compatibility wrapper for AddEditPlant.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Retained to handle external edit intents and camera return hooks safely while delegating
 * form state and validation logic to {@link AddEditPlantFragment}.
 */
public class AddEditPlantActivity extends AppCompatActivity {

    public static final String EXTRA_PLANT_ID = "extra_plant_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            String plantId = getIntent().getStringExtra(EXTRA_PLANT_ID);
            Bundle args = new Bundle();
            if (plantId != null) {
                args.putString(AddEditPlantFragment.ARG_PLANT_ID, plantId);
            }
            AddEditPlantFragment fragment = new AddEditPlantFragment();
            fragment.setArguments(args);

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit();
        }
    }
}
