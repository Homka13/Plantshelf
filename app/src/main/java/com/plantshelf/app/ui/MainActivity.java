package com.plantshelf.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.plantshelf.app.R;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.importer.BackupExporter;
import com.plantshelf.app.data.importer.BrunqBackupImporter;
import com.plantshelf.app.databinding.ActivityMainBinding;
import com.plantshelf.app.ui.adapter.PlantAdapter;
import com.plantshelf.app.ui.adapter.ShelfAdapter;
import com.plantshelf.app.viewmodel.MainViewModel;

import java.io.File;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private ShelfAdapter shelfAdapter;
    private PlantAdapter plantAdapter;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importBackupFromUri(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        setupRecyclerViews();
        observeData();
        setupListeners();

        // Check for updates from GitHub in background (only on cold start, at most once per 24 hours)
        if (savedInstanceState == null && com.plantshelf.app.updater.GitHubUpdateManager.shouldPerformPeriodicCheck(this)) {
            com.plantshelf.app.updater.GitHubUpdateManager.checkForUpdates(this, false);
        }
    }

    private void setupRecyclerViews() {
        // Horizontal Shelves
        shelfAdapter = new ShelfAdapter(categoryId -> viewModel.selectCategory(categoryId));
        binding.rvShelves.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvShelves.setAdapter(shelfAdapter);

        // Vertical Plants
        plantAdapter = new PlantAdapter(new PlantAdapter.OnPlantClickListener() {
            @Override
            public void onPlantClick(PlantEntity plant) {
                Intent intent = new Intent(MainActivity.this, PlantDetailActivity.class);
                intent.putExtra(PlantDetailActivity.EXTRA_PLANT_ID, plant.getId());
                startActivity(intent);
            }

            @Override
            public void onQuickWater(PlantEntity plant) {
                viewModel.quickWater(plant.getId());
                Snackbar.make(binding.getRoot(), getString(R.string.action_watered_success), Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onQuickMist(PlantEntity plant) {
                viewModel.quickMist(plant.getId());
                Snackbar.make(binding.getRoot(), getString(R.string.action_misted_success), Snackbar.LENGTH_SHORT).show();
            }
        });

        binding.rvPlants.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlants.setAdapter(plantAdapter);
    }

    private void observeData() {
        viewModel.getCategories().observe(this, categories -> {
            shelfAdapter.setCategories(categories);
            plantAdapter.setCategories(categories);
        });

        viewModel.getPlants().observe(this, plants -> {
            binding.swipeRefresh.setRefreshing(false);
            plantAdapter.setPlants(plants);

            if (plants == null || plants.isEmpty()) {
                binding.layoutEmpty.setVisibility(View.VISIBLE);
                binding.rvPlants.setVisibility(View.GONE);
            } else {
                binding.layoutEmpty.setVisibility(View.GONE);
                binding.rvPlants.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getQuarantinedCount().observe(this, count -> {
            int c = count != null ? count : 0;
            binding.chipQuarantine.setText(getString(R.string.status_quarantine_chip, c));
            // Show quarantine bar if count > 0 or if user is currently filtering by it
            binding.layoutQuarantineBar.setVisibility(c > 0 || viewModel.isQuarantineFilterActive() ? View.VISIBLE : View.GONE);
        });

        viewModel.getQuarantineFilterOnly().observe(this, isActive -> {
            boolean active = Boolean.TRUE.equals(isActive);
            binding.chipQuarantine.setChecked(active);
            binding.tvQuarantineActiveNotice.setVisibility(active ? View.VISIBLE : View.GONE);
        });
    }

    private void setupListeners() {
        binding.chipQuarantine.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setQuarantineFilter(isChecked);
        });

        binding.swipeRefresh.setOnRefreshListener(() -> {
            // Re-trigger category selection to refresh
            viewModel.selectCategory(viewModel.getSelectedCategoryId());
        });

        binding.fabAddPlant.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEditPlantActivity.class);
            startActivity(intent);
        });

        binding.btnImportBackup.setOnClickListener(v -> openFilePickerForImport());
    }

    private void openFilePickerForImport() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_confirm_title)
                .setMessage(R.string.import_confirm_message)
                .setPositiveButton(R.string.btn_import, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    filePickerLauncher.launch(intent);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void importBackupFromUri(Uri uri) {
        Toast.makeText(this, R.string.importing_backup_title, Toast.LENGTH_SHORT).show();

        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "Не вдалося відкрити файл", Toast.LENGTH_SHORT).show();
                return;
            }

            BrunqBackupImporter.importFromStream(this, is, new BrunqBackupImporter.ImportCallback() {
                @Override
                public void onSuccess(int plantCount, int categoryCount) {
                    runOnUiThread(() -> {
                        String msg = getString(R.string.import_success, plantCount, categoryCount);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        String msg = getString(R.string.import_error, e.getMessage());
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Помилка читання: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        viewModel.setSearchQuery(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        viewModel.setSearchQuery(newText);
                        return true;
                    }
                });
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_catalog) {
            Intent intent = new Intent(this, PlantCatalogActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_quick_ai_add) {
            com.plantshelf.app.ui.dialog.QuickAiAddDialog.show(this, null, null);
            return true;
        } else if (id == R.id.action_calendar) {
            Intent intent = new Intent(this, CareCalendarActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_light_meter) {
            Intent intent = new Intent(this, LightMeterActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_api_settings) {
            com.plantshelf.app.ui.dialog.ApiKeyDialog.show(this, null);
            return true;
        } else if (id == R.id.action_import_backup) {
            openFilePickerForImport();
            return true;
        } else if (id == R.id.action_export_backup) {
            exportBackup();
            return true;
        } else if (id == R.id.action_check_updates) {
            com.plantshelf.app.updater.GitHubUpdateManager.checkForUpdates(this, true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportBackup() {
        File backupFile = new File(getExternalFilesDir(null), "plantshelf-backup-" + System.currentTimeMillis() + ".json");
        BackupExporter.exportToFile(this, backupFile, new BackupExporter.ExportCallback() {
            @Override
            public void onSuccess(File exportedFile) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Бекап збережено: " + exportedFile.getName(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Помилка експорту: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
