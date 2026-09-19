package com.plantshelf.app.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.data.catalog.CatalogPlant;
import com.plantshelf.app.data.catalog.PlantCatalogRepository;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.databinding.ActivityPlantCatalogBinding;
import com.plantshelf.app.ui.adapter.PlantCatalogAdapter;
import com.plantshelf.app.ui.dialog.QuickAiAddDialog;
import com.plantshelf.app.widget.PlantCareWidgetProvider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlantCatalogActivity extends AppCompatActivity {

    private ActivityPlantCatalogBinding binding;
    private PlantCatalogAdapter adapter;
    private String currentCategory = "Всі";
    private String currentQuery = "";
    private List<CategoryEntity> userCategories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlantCatalogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarCatalog);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupRecyclerView();
        setupCategoryChips();
        setupSearch();
        setupAiBanner();
        loadUserCategories();
        updateList();
    }

    private void setupRecyclerView() {
        adapter = new PlantCatalogAdapter(new PlantCatalogAdapter.OnCatalogActionListener() {
            @Override
            public void onAddToShelf(CatalogPlant plant) {
                promptAddToShelf(plant);
            }

            @Override
            public void onPlantDetails(CatalogPlant plant) {
                showPlantInfoDialog(plant);
            }
        });

        binding.rvCatalog.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCatalog.setAdapter(adapter);
    }

    private void setupCategoryChips() {
        List<String> categories = PlantCatalogRepository.getCategories();
        for (String cat : categories) {
            Chip chip = new Chip(this);
            chip.setText(cat);
            chip.setCheckable(true);
            if ("Всі".equals(cat)) {
                chip.setChecked(true);
            }

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentCategory = cat;
                    updateList();
                }
            });

            binding.chipGroupCatalog.addView(chip);
        }
    }

    private void setupSearch() {
        binding.searchCatalog.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentQuery = query;
                updateList();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText;
                updateList();
                return true;
            }
        });
    }

    private void setupAiBanner() {
        binding.cardAskAi.setOnClickListener(v -> {
            QuickAiAddDialog.show(this, currentQuery, () -> {
                Toast.makeText(this, "Рослину додано через AI!", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private void loadUserCategories() {
        new Thread(() -> {
            userCategories = PlantshelfDatabase.getInstance(this).categoryDao().getAllCategoriesSync();
        }).start();
    }

    private void updateList() {
        List<CatalogPlant> plants = PlantCatalogRepository.filter(currentQuery, currentCategory);
        adapter.setPlants(plants);

        if (plants.isEmpty()) {
            binding.tvEmptyCatalog.setVisibility(View.VISIBLE);
            binding.tvAskAiSubtitle.setText("Швидко створити '" + currentQuery + "' через Gemini AI");
        } else {
            binding.tvEmptyCatalog.setVisibility(View.GONE);
            binding.tvAskAiSubtitle.setText("Запитайте Gemini AI для створення паспорту");
        }
    }

    private void promptAddToShelf(CatalogPlant plant) {
        if (userCategories == null || userCategories.isEmpty()) {
            saveCatalogPlantToDb(plant, null, "Моя оранжерея");
            return;
        }

        String[] names = new String[userCategories.size()];
        for (int i = 0; i < userCategories.size(); i++) {
            names[i] = userCategories.get(i).getName();
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Оберіть кімнату / полицю:")
                .setItems(names, (dialog, which) -> {
                    CategoryEntity selectedCat = userCategories.get(which);
                    saveCatalogPlantToDb(plant, selectedCat.getId(), selectedCat.getName());
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private void saveCatalogPlantToDb(CatalogPlant plant, String categoryId, String categoryName) {
        new Thread(() -> {
            String plantId = UUID.randomUUID().toString().substring(0, 8);
            PlantEntity entity = new PlantEntity(plantId);
            entity.setName(plant.getName());
            entity.setLatin(plant.getLatin());
            entity.setCategoryId(categoryId);
            entity.setType(plant.getCategory());
            entity.setDifficulty(plant.getDifficulty());
            entity.setLight(plant.getLight());
            entity.setLux(plant.getLux());
            entity.setIntervalDays(plant.getIntervalSummer());
            entity.setIntervalDaysWinter(plant.getIntervalWinter());
            entity.setFertIntervalDays(plant.getFertInterval());
            entity.setHumidity(plant.getHumidity());
            entity.setSoil(plant.getSoil());
            entity.setWarning(plant.getWarning());
            entity.setComments(plant.getDescription());

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            entity.setLastWatered(today);
            entity.setCreatedAt(today);

            PlantshelfDatabase.getInstance(this).plantDao().insert(entity);
            PlantCareWidgetProvider.sendUpdateBroadcast(this);

            runOnUiThread(() -> {
                Toast.makeText(this, plant.getName() + " додано на полицю '" + categoryName + "'!", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void showPlantInfoDialog(CatalogPlant plant) {
        String message = "🌿 Ботанічна назва: " + plant.getLatin() + "\n\n"
                + "☀️ Освітлення: " + plant.getLight() + " (" + plant.getLux() + " люкс)\n\n"
                + "💧 Полив: кожні " + plant.getIntervalSummer() + " дн. влітку, " + plant.getIntervalWinter() + " дн. взимку\n\n"
                + "🧪 Добрива: раз на " + plant.getFertInterval() + " дн.\n\n"
                + "🪴 Ґрунт: " + plant.getSoil() + "\n\n"
                + "⚠️ Безпека: " + plant.getWarning() + "\n\n"
                + "📝 Опис: " + plant.getDescription();

        new MaterialAlertDialogBuilder(this)
                .setTitle(plant.getName())
                .setMessage(message)
                .setPositiveButton("➕ Додати на полицю", (dialog, which) -> promptAddToShelf(plant))
                .setNegativeButton("Закрити", null)
                .show();
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
