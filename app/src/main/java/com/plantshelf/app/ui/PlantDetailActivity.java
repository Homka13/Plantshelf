package com.plantshelf.app.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.plantshelf.app.R;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.databinding.ActivityPlantDetailBinding;
import com.plantshelf.app.ui.adapter.CareLogAdapter;
import com.plantshelf.app.viewmodel.PlantDetailViewModel;

import java.io.File;

public class PlantDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PLANT_ID = "extra_plant_id";

    private ActivityPlantDetailBinding binding;
    private PlantDetailViewModel viewModel;
    private CareLogAdapter careLogAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlantDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarDetail);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        String plantId = getIntent().getStringExtra(EXTRA_PLANT_ID);
        if (plantId == null || plantId.isEmpty()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(PlantDetailViewModel.class);
        viewModel.setPlantId(plantId);

        setupHistoryRecyclerView();
        observePlant();
        setupActions();
    }

    private void setupHistoryRecyclerView() {
        careLogAdapter = new CareLogAdapter();
        binding.rvCareHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCareHistory.setAdapter(careLogAdapter);
    }

    private void observePlant() {
        viewModel.getPlant().observe(this, plant -> {
            if (plant != null) {
                bindPlantDetails(plant);
            }
        });

        viewModel.getCareLogs().observe(this, logs -> {
            careLogAdapter.setLogs(logs);
        });
    }

    private void bindPlantDetails(PlantEntity plant) {
        binding.collapsingToolbar.setTitle(plant.getName());
        binding.tvDetailTitle.setText(plant.getName());

        String subtitle = !plant.getVariety().isEmpty() ? plant.getVariety() : plant.getLatin();
        binding.tvDetailLatin.setText(subtitle != null ? subtitle : "");

        // Intervals
        binding.tvSummerInterval.setText(getString(R.string.days_unit, plant.getIntervalDays()));
        binding.tvWinterInterval.setText(getString(R.string.days_unit, plant.getIntervalDaysWinter()));
        binding.tvFertInterval.setText(getString(R.string.days_unit, plant.getFertIntervalDays()));

        // Requirements
        String lightText = "☀️ " + getString(R.string.label_light) + ": " + (plant.getLight() != null ? plant.getLight() : "");
        if (plant.getLux() > 0) {
            lightText += " (" + getString(R.string.lux_unit, plant.getLux()) + ")";
        }
        binding.tvReqLight.setText(lightText);

        String humidityText = "💧 " + getString(R.string.label_humidity) + ": " + (plant.getHumidity() != null ? plant.getHumidity() : "середня");
        binding.tvReqHumidity.setText(humidityText);

        StringBuilder potSoil = new StringBuilder();
        if (plant.getPotSize() > 0) {
            potSoil.append("🪴 ").append(getString(R.string.label_pot_size)).append(": ").append(getString(R.string.cm_unit, plant.getPotSize()));
        }
        if (plant.getSoil() != null && !plant.getSoil().isEmpty()) {
            if (potSoil.length() > 0) potSoil.append(" | ");
            potSoil.append(plant.getSoil());
        }
        binding.tvReqPotSoil.setText(potSoil.toString());

        if (plant.getWarning() != null && !plant.getWarning().isEmpty()) {
            binding.tvReqNotes.setText("⚠️ " + plant.getWarning());
        } else if (plant.getComments() != null && !plant.getComments().isEmpty()) {
            binding.tvReqNotes.setText("📝 " + plant.getComments());
        } else {
            binding.tvReqNotes.setText("📝 Нотаток немає");
        }

        // Photo
        if (plant.getPrimaryPhotoPath() != null && new File(plant.getPrimaryPhotoPath()).exists()) {
            Glide.with(this)
                    .load(new File(plant.getPrimaryPhotoPath()))
                    .centerCrop()
                    .placeholder(R.drawable.ic_placeholder_plant)
                    .into(binding.ivDetailPhoto);
        }
    }

    private void setupActions() {
        binding.btnActionWater.setOnClickListener(v -> {
            viewModel.recordWatering();
            Snackbar.make(binding.getRoot(), R.string.action_watered_success, Snackbar.LENGTH_SHORT).show();
        });

        binding.btnActionFert.setOnClickListener(v -> {
            viewModel.recordFertilizing();
            Snackbar.make(binding.getRoot(), R.string.action_fertilized_success, Snackbar.LENGTH_SHORT).show();
        });

        binding.btnActionMist.setOnClickListener(v -> {
            viewModel.recordMisting();
            Snackbar.make(binding.getRoot(), R.string.action_misted_success, Snackbar.LENGTH_SHORT).show();
        });

        binding.btnAddToCalendar.setOnClickListener(v -> {
            PlantEntity plant = viewModel.getPlant().getValue();
            if (plant != null) {
                com.plantshelf.app.data.calendar.CalendarIntegrationHelper.addPlantCareToSystemCalendar(this, plant, "water");
            }
        });
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
