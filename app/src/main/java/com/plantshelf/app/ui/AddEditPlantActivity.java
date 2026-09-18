package com.plantshelf.app.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;
import com.plantshelf.app.databinding.ActivityAddEditPlantBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AddEditPlantActivity extends AppCompatActivity {

    private ActivityAddEditPlantBinding binding;
    private PlantRepository repository;
    private final List<CategoryEntity> categoryList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEditPlantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarAdd);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        repository = new PlantRepository(getApplication());

        setupCategorySpinner();
        setupSaveButton();
    }

    private void setupCategorySpinner() {
        spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        binding.spinnerCategories.setAdapter(spinnerAdapter);

        repository.getAllCategories().observe(this, categories -> {
            categoryList.clear();
            List<String> names = new ArrayList<>();
            names.add("Без категорії");

            if (categories != null) {
                categoryList.addAll(categories);
                for (CategoryEntity c : categories) {
                    names.add(c.getName());
                }
            }
            spinnerAdapter.clear();
            spinnerAdapter.addAll(names);
            spinnerAdapter.notifyDataSetChanged();
        });
    }

    private void setupSaveButton() {
        binding.btnSavePlant.setOnClickListener(v -> {
            String name = binding.etPlantName.getText() != null ? binding.etPlantName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                binding.etPlantName.setError("Введіть назву рослини");
                return;
            }

            String variety = binding.etPlantVariety.getText() != null ? binding.etPlantVariety.getText().toString().trim() : "";
            String latin = binding.etPlantLatin.getText() != null ? binding.etPlantLatin.getText().toString().trim() : "";
            String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

            int summerInterval = 7;
            try {
                summerInterval = Integer.parseInt(binding.etIntervalSummer.getText().toString().trim());
            } catch (Exception ignored) {}

            int winterInterval = summerInterval;
            try {
                winterInterval = Integer.parseInt(binding.etIntervalWinter.getText().toString().trim());
            } catch (Exception ignored) {}

            int targetLux = 5000;
            try {
                targetLux = Integer.parseInt(binding.etTargetLux.getText().toString().trim());
            } catch (Exception ignored) {}

            String selectedCategoryId = null;
            int spinnerPos = binding.spinnerCategories.getSelectedItemPosition();
            if (spinnerPos > 0 && spinnerPos - 1 < categoryList.size()) {
                selectedCategoryId = categoryList.get(spinnerPos - 1).getId();
            }

            String plantId = UUID.randomUUID().toString().substring(0, 8);
            PlantEntity plant = new PlantEntity(plantId);
            plant.setName(name);
            plant.setVariety(variety);
            plant.setLatin(latin);
            plant.setNote(notes);
            plant.setCategoryId(selectedCategoryId);
            plant.setIntervalDays(summerInterval);
            plant.setIntervalDaysWinter(winterInterval);
            plant.setLux(targetLux);
            plant.setLastWatered(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
            plant.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));

            repository.insertPlant(plant);
            Toast.makeText(this, "Рослину додано!", Toast.LENGTH_SHORT).show();
            finish();
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
