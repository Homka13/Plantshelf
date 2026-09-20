package com.plantshelf.app.feature.catalog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
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

/**
 * Feature: Plant Botanical Catalog Fragment.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Converting screen activities into modular Fragments enables Single-Activity Jetpack
 * Navigation, eliminating window transition stutter and heavy activity stack overhead.
 */
public class PlantCatalogFragment extends Fragment {

    private ActivityPlantCatalogBinding binding;
    private PlantCatalogAdapter adapter;
    private String currentCategory = "Всі";
    private String currentQuery = "";
    private List<CategoryEntity> userCategories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ActivityPlantCatalogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbarCatalog.setNavigationOnClickListener(v -> {
            try {
                if (!Navigation.findNavController(v).navigateUp()) {
                    requireActivity().onBackPressed();
                }
            } catch (Exception e) {
                requireActivity().onBackPressed();
            }
        });

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

        binding.rvCatalog.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCatalog.setAdapter(adapter);
    }

    private void setupCategoryChips() {
        binding.chipGroupCatalog.removeAllViews();
        List<String> categories = PlantCatalogRepository.getCategories();
        for (String cat : categories) {
            Chip chip = new Chip(requireContext());
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
            QuickAiAddDialog.show(requireContext(), currentQuery, () -> {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Рослину додано на полицю через AI!", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadUserCategories() {
        PlantshelfDatabase.getInstance(requireContext())
                .categoryDao()
                .getAllCategories()
                .observe(getViewLifecycleOwner(), categories -> {
                    if (categories != null) {
                        userCategories = categories;
                    }
                });
    }

    private void updateList() {
        List<CatalogPlant> filtered = PlantCatalogRepository.filter(currentQuery, currentCategory);
        adapter.setPlants(filtered);
        binding.tvEmptyCatalog.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void promptAddToShelf(CatalogPlant plant) {
        if (userCategories.isEmpty()) {
            addPlantToShelf(plant, null);
            return;
        }

        String[] categoryNames = new String[userCategories.size() + 1];
        categoryNames[0] = "Без кімнати";
        for (int i = 0; i < userCategories.size(); i++) {
            categoryNames[i + 1] = userCategories.get(i).getName();
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Оберіть кімнату / полицю")
                .setItems(categoryNames, (dialog, which) -> {
                    String categoryId = null;
                    if (which > 0) {
                        categoryId = userCategories.get(which - 1).getId();
                    }
                    addPlantToShelf(plant, categoryId);
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private void addPlantToShelf(CatalogPlant catalogPlant, String categoryId) {
        new Thread(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String plantId = UUID.randomUUID().toString();

            PlantEntity entity = new PlantEntity(plantId);
            entity.setName(catalogPlant.getName());
            entity.setLatin(catalogPlant.getLatin());
            entity.setCategoryId(categoryId);
            entity.setLastWatered(today);
            entity.setIntervalDays(catalogPlant.getIntervalSummer());
            entity.setIntervalDaysWinter(catalogPlant.getIntervalWinter());
            entity.setFertIntervalDays(catalogPlant.getFertInterval());
            entity.setFertilizeIntervalSummerDays(catalogPlant.getFertInterval());
            entity.setLux(catalogPlant.getLux());
            entity.setHumidity(catalogPlant.getHumidity());
            entity.setSoil(catalogPlant.getSoil());
            entity.setWarning(catalogPlant.getWarning());
            entity.setNote(catalogPlant.getDescription());
            entity.setCreatedAt(today);

            PlantshelfDatabase.getInstance(requireContext()).plantDao().insert(entity);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "«" + catalogPlant.getName() + "» додано на полицю!", Toast.LENGTH_SHORT).show();
                        PlantCareWidgetProvider.sendUpdateBroadcast(requireContext());
                    }
                });
            }
        }).start();
    }

    private void showPlantInfoDialog(CatalogPlant plant) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌿 Латинська назва: ").append(plant.getLatin()).append("\n\n");
        sb.append("📂 Категорія: ").append(plant.getCategory()).append("\n");
        sb.append("⭐️ Складність: ").append(plant.getDifficulty()).append("\n\n");
        sb.append("💧 Полив влітку: кожні ").append(plant.getIntervalSummer()).append(" дн.\n");
        sb.append("❄️ Полив взимку: кожні ").append(plant.getIntervalWinter()).append(" дн.\n");
        sb.append("🧪 Добрива: кожні ").append(plant.getFertInterval()).append(" дн.\n\n");
        sb.append("☀️ Освітлення: ").append(plant.getLight()).append(" (~").append(plant.getLux()).append(" lx)\n");
        sb.append("💨 Вологість повітря: ").append(plant.getHumidity()).append("\n");
        sb.append("🪴 Ґрунт: ").append(plant.getSoil()).append("\n\n");

        if (plant.getWarning() != null && !plant.getWarning().isEmpty()) {
            sb.append("⚠️ Застереження: ").append(plant.getWarning()).append("\n\n");
        }
        sb.append("📝 Опис: ").append(plant.getDescription());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(plant.getName())
                .setMessage(sb.toString())
                .setPositiveButton("Додати на полицю", (dialog, which) -> promptAddToShelf(plant))
                .setNegativeButton("Закрити", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
