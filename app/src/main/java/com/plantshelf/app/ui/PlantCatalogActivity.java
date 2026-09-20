package com.plantshelf.app.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.plantshelf.app.feature.catalog.PlantCatalogFragment;

/**
 * Backward-compatibility wrapper for PlantCatalog.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Following the Single-Activity transition, this activity serves as a thin compatibility shim
 * so external app shortcuts or intents continue to resolve cleanly while delegating all rendering
 * and logic to {@link PlantCatalogFragment}.
 */
public class PlantCatalogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new PlantCatalogFragment())
                    .commit();
        }
    }
}
