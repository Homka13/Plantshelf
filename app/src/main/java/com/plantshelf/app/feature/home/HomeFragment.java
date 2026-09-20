package com.plantshelf.app.feature.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.plantshelf.app.R;
import com.plantshelf.app.data.calendar.CalendarSyncManager;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.importer.BackupExporter;
import com.plantshelf.app.data.importer.BrunqBackupImporter;
import com.plantshelf.app.databinding.FragmentHomeBinding;
import com.plantshelf.app.ui.adapter.PlantAdapter;
import com.plantshelf.app.ui.adapter.ShelfAdapter;
import com.plantshelf.app.ui.dialog.ApiKeyDialog;
import com.plantshelf.app.ui.dialog.NotificationSettingsDialog;
import com.plantshelf.app.ui.dialog.QuickAiAddDialog;
import com.plantshelf.app.updater.GitHubUpdateManager;
import com.plantshelf.app.viewmodel.MainViewModel;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Feature: Home Dashboard & Shelves Fragment.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Migrating the home screen from an Activity to a Fragment anchors the Single-Activity
 * Architecture, enabling Jetpack Navigation graph orchestration and seamless destination routing.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private MainViewModel viewModel;
    private ShelfAdapter shelfAdapter;
    private PlantAdapter plantAdapter;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importBackupFromUri(uri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> exportPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        writeExportToUri(uri);
                    }
                }
            });

    private final ActivityResultLauncher<String> calendarPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (Boolean.TRUE.equals(isGranted)) {
                    startBatchCalendarSync();
                } else {
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), "Потрібен дозвіл для запису в системний календар", Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupToolbar();
        setupRecyclerViews();
        observeData();
        setupListeners();

        if (savedInstanceState == null) {
            GitHubUpdateManager.checkForUpdatesOnLaunch(requireActivity(), binding.getRoot());
        }
    }

    private void setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.main_menu);

        MenuItem searchItem = binding.toolbar.getMenu().findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

        binding.toolbar.setOnMenuItemClickListener(this::handleMenuClick);
    }

    private boolean handleMenuClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_catalog) {
            Navigation.findNavController(requireView()).navigate(R.id.action_homeFragment_to_catalogFragment);
            return true;
        } else if (id == R.id.action_quick_ai_add) {
            QuickAiAddDialog.show(requireContext(), null, null);
            return true;
        } else if (id == R.id.action_calendar) {
            Navigation.findNavController(requireView()).navigate(R.id.action_homeFragment_to_calendarFragment);
            return true;
        } else if (id == R.id.action_sync_calendar) {
            checkAndStartBatchSync();
            return true;
        } else if (id == R.id.action_light_meter) {
            Navigation.findNavController(requireView()).navigate(R.id.action_homeFragment_to_lightMeterFragment);
            return true;
        } else if (id == R.id.action_notifications_settings) {
            NotificationSettingsDialog.show(requireContext(), new NotificationSettingsDialog.OnNotificationSettingsSavedListener() {
                @Override
                public void onSettingsSaved(boolean enabled, int hour, int minute) {
                    checkAndStartBatchSync();
                }

                @Override
                public void onRequestPermission() {
                    checkAndStartBatchSync();
                }
            });
            return true;
        } else if (id == R.id.action_api_settings) {
            ApiKeyDialog.show(requireContext(), null);
            return true;
        } else if (id == R.id.action_import_backup) {
            openFilePickerForImport();
            return true;
        } else if (id == R.id.action_export_backup) {
            exportBackup();
            return true;
        } else if (id == R.id.action_check_updates) {
            GitHubUpdateManager.checkForUpdates(requireActivity(), true);
            return true;
        }
        return false;
    }

    private void setupRecyclerViews() {
        shelfAdapter = new ShelfAdapter(categoryId -> viewModel.selectCategory(categoryId));
        binding.rvShelves.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvShelves.setAdapter(shelfAdapter);

        plantAdapter = new PlantAdapter(new PlantAdapter.OnPlantClickListener() {
            @Override
            public void onPlantClick(PlantEntity plant) {
                Bundle args = new Bundle();
                args.putString("plantId", plant.getId());
                Navigation.findNavController(requireView()).navigate(R.id.action_homeFragment_to_plantDetailFragment, args);
            }

            @Override
            public void onPlantLongClick(PlantEntity plant) {
                showPlantOptionsDialog(plant);
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

        binding.rvPlants.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlants.setAdapter(plantAdapter);
    }

    private void observeData() {
        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            shelfAdapter.setCategories(categories);
            plantAdapter.setCategories(categories);
        });

        viewModel.getPlants().observe(getViewLifecycleOwner(), plants -> {
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

        viewModel.getQuarantinedCount().observe(getViewLifecycleOwner(), count -> {
            int c = count != null ? count : 0;
            binding.chipQuarantine.setText(getString(R.string.status_quarantine_chip, c));
            binding.layoutQuarantineBar.setVisibility(c > 0 || viewModel.isQuarantineFilterActive() ? View.VISIBLE : View.GONE);
        });

        viewModel.getQuarantineFilterOnly().observe(getViewLifecycleOwner(), isActive -> {
            boolean active = Boolean.TRUE.equals(isActive);
            binding.chipQuarantine.setChecked(active);
            binding.tvQuarantineActiveNotice.setVisibility(active ? View.VISIBLE : View.GONE);
        });
    }

    private void setupListeners() {
        binding.chipQuarantine.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setQuarantineFilter(isChecked));

        binding.swipeRefresh.setOnRefreshListener(() ->
                viewModel.selectCategory(viewModel.getSelectedCategoryId()));

        binding.fabAddPlant.setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigate(R.id.action_homeFragment_to_addEditPlantFragment));

        binding.btnImportBackup.setOnClickListener(v -> openFilePickerForImport());
    }

    private void showPlantOptionsDialog(PlantEntity plant) {
        String[] options = {
                "Швидкий полив",
                "Внести добрива",
                "Обприскати",
                "Історія догляду",
                "Редагувати рослину",
                "Видалити рослину"
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(plant.getName())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            viewModel.quickWater(plant.getId());
                            Snackbar.make(binding.getRoot(), getString(R.string.action_watered_success), Snackbar.LENGTH_SHORT).show();
                            break;
                        case 1:
                            viewModel.quickFertilize(plant.getId());
                            Snackbar.make(binding.getRoot(), "Добрива внесено!", Snackbar.LENGTH_SHORT).show();
                            break;
                        case 2:
                            viewModel.quickMist(plant.getId());
                            Snackbar.make(binding.getRoot(), getString(R.string.action_misted_success), Snackbar.LENGTH_SHORT).show();
                            break;
                        case 3:
                        case 4: {
                            Bundle args = new Bundle();
                            args.putString("plantId", plant.getId());
                            Navigation.findNavController(requireView()).navigate(R.id.action_homeFragment_to_plantDetailFragment, args);
                            break;
                        }
                        case 5:
                            confirmDeletePlant(plant);
                            break;
                    }
                })
                .show();
    }

    private void confirmDeletePlant(PlantEntity plant) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_plant_title)
                .setMessage(getString(R.string.delete_plant_confirm_message, plant.getName()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    viewModel.deletePlant(plant);
                    Snackbar.make(binding.getRoot(), R.string.plant_deleted_success, Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void checkAndStartBatchSync() {
        if (CalendarSyncManager.getInstance(requireContext()).hasCalendarPermission()) {
            startBatchCalendarSync();
        } else {
            calendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR);
        }
    }

    private void startBatchCalendarSync() {
        java.util.List<PlantEntity> plants = viewModel.getPlants().getValue();
        if (plants == null || plants.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_plants_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        CalendarSyncManager.getInstance(requireContext()).syncAllPlants(plants, new CalendarSyncManager.SyncCallback() {
            @Override
            public void onProgress(int current, int total) {}

            @Override
            public void onSuccess(int syncedCount) {
                if (isAdded()) {
                    Snackbar.make(binding.getRoot(),
                            "✅ Успішно синхронізовано " + syncedCount + " рослин із системним календарем!",
                            Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String message) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void openFilePickerForImport() {
        new MaterialAlertDialogBuilder(requireContext())
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
        Toast.makeText(requireContext(), R.string.importing_backup_title, Toast.LENGTH_SHORT).show();

        try {
            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(requireContext(), "Не вдалося відкрити файл", Toast.LENGTH_SHORT).show();
                return;
            }

            BrunqBackupImporter.importFromStream(requireContext(), is, new BrunqBackupImporter.ImportCallback() {
                @Override
                public void onSuccess(int plantCount, int categoryCount) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            String msg = getString(R.string.import_success, plantCount, categoryCount);
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        });
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            String msg = getString(R.string.import_error, e.getMessage());
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Помилка читання: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "plantshelf_backup_" + timestamp + ".zip");
        exportPickerLauncher.launch(intent);
    }

    private void writeExportToUri(Uri uri) {
        Toast.makeText(requireContext(), "Експорт резервної копії...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                OutputStream os = requireContext().getContentResolver().openOutputStream(uri);
                if (os == null) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Не вдалося створити файл експорту", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                PlantshelfDatabase db = PlantshelfDatabase.getInstance(requireContext());
                java.util.List<CategoryEntity> categories = db.categoryDao().getAllCategoriesSync();
                java.util.List<PlantEntity> plants = db.plantDao().getAllPlantsSync();

                com.google.gson.JsonObject root = BackupExporter.buildBackupJson(
                        categories,
                        plants,
                        plantId -> db.careLogDao().getLogsForPlantSync(plantId),
                        plantId -> db.photoDao().getPhotosForPlantSync(plantId)
                );

                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8)) {
                    gson.toJson(root, writer);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String msg = "Експортовано " + (plants != null ? plants.size() : 0) + " рослин та " + (categories != null ? categories.size() : 0) + " полиць";
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Помилка запису: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
