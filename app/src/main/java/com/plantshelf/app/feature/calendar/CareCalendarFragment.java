package com.plantshelf.app.feature.calendar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.plantshelf.app.R;
import com.plantshelf.app.data.calendar.CalendarIntegrationHelper;
import com.plantshelf.app.data.calendar.CalendarSyncManager;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.models.CalendarTask;
import com.plantshelf.app.data.repository.PlantRepository;
import com.plantshelf.app.databinding.ActivityCareCalendarBinding;
import com.plantshelf.app.domain.usecase.CalculateNextFertilizingUseCase;
import com.plantshelf.app.domain.usecase.CalculateNextWateringUseCase;
import com.plantshelf.app.ui.adapter.CalendarTaskAdapter;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Feature: Care Calendar Fragment.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Encapsulating the calendar into a Fragment decouples calendar UI state from the
 * top-level Activity lifecycle, allowing easy reuse across tablet multi-pane or modal sheets.
 */
public class CareCalendarFragment extends Fragment {

    private ActivityCareCalendarBinding binding;
    private PlantRepository repository;
    private CalendarTaskAdapter taskAdapter;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("d MMMM yyyy", new Locale("uk"));

    private final Calendar selectedCalendar = Calendar.getInstance();
    private final List<PlantEntity> allPlants = new ArrayList<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();

    private final CalculateNextWateringUseCase calculateNextWateringUseCase =
            new CalculateNextWateringUseCase();
    private final CalculateNextFertilizingUseCase calculateNextFertilizingUseCase =
            new CalculateNextFertilizingUseCase();

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
        binding = ActivityCareCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new PlantRepository(requireActivity().getApplication());

        binding.toolbarCalendar.setNavigationOnClickListener(v -> {
            try {
                if (!Navigation.findNavController(v).navigateUp()) {
                    requireActivity().onBackPressed();
                }
            } catch (Exception e) {
                requireActivity().onBackPressed();
            }
        });

        setupRecyclerView();
        setupCalendarView();
        loadData();
    }

    private void setupRecyclerView() {
        taskAdapter = new CalendarTaskAdapter(task -> {
            if (task == null) return;
            if ("water".equalsIgnoreCase(task.getTaskType())) {
                repository.recordWatering(task.getPlantId());
                Snackbar.make(binding.getRoot(), "💧 Рослину полито!", Snackbar.LENGTH_SHORT).show();
            } else if ("fert".equalsIgnoreCase(task.getTaskType())) {
                repository.recordFertilizing(task.getPlantId());
                Snackbar.make(binding.getRoot(), "🧪 Добрива внесено!", Snackbar.LENGTH_SHORT).show();
            }
        });

        binding.rvCalendarTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCalendarTasks.setAdapter(taskAdapter);
    }

    private void setupCalendarView() {
        updateDateTitle(selectedCalendar.getTime());
        binding.calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedCalendar.set(year, month, dayOfMonth);
            updateDateTitle(selectedCalendar.getTime());
            computeTasksForSelectedDate();
        });
    }

    private void updateDateTitle(Date date) {
        binding.tvSelectedDateTitle.setText("Завдання на " + displayFormat.format(date) + ":");
    }

    private void loadData() {
        repository.getAllCategories().observe(getViewLifecycleOwner(), categories -> {
            categoryMap.clear();
            if (categories != null) {
                for (CategoryEntity c : categories) {
                    categoryMap.put(c.getId(), c);
                }
            }
            computeTasksForSelectedDate();
        });

        repository.getAllPlants().observe(getViewLifecycleOwner(), plants -> {
            allPlants.clear();
            if (plants != null) {
                allPlants.addAll(plants);
            }
            computeTasksForSelectedDate();
        });
    }

    private void computeTasksForSelectedDate() {
        if (allPlants.isEmpty()) {
            taskAdapter.setTasks(new ArrayList<>());
            binding.tvEmptyCalendar.setVisibility(View.VISIBLE);
            return;
        }

        String selectedDateStr = dateFormat.format(selectedCalendar.getTime());
        String todayStr = dateFormat.format(new Date());
        boolean isSelectedToday = selectedDateStr.equals(todayStr);

        List<CalendarTask> tasks = new ArrayList<>();

        for (PlantEntity plant : allPlants) {
            CategoryEntity cat = categoryMap.get(plant.getCategoryId());
            String catName = cat != null ? cat.getName() : "Без кімнати";
            String catColor = cat != null ? cat.getColor() : "#4E8D7C";

            if (isWateringDueOn(plant, selectedDateStr, isSelectedToday)) {
                tasks.add(new CalendarTask(
                        plant.getId(),
                        plant.getName(),
                        plant.getVariety(),
                        catName,
                        catColor,
                        "water",
                        selectedDateStr
                ));
            }

            if (isFertilizingDueOn(plant, selectedDateStr, isSelectedToday)) {
                tasks.add(new CalendarTask(
                        plant.getId(),
                        plant.getName(),
                        plant.getVariety(),
                        catName,
                        catColor,
                        "fert",
                        selectedDateStr
                ));
            }
        }

        taskAdapter.setTasks(tasks);
        binding.tvEmptyCalendar.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean isWateringDueOn(PlantEntity plant, String targetDateStr, boolean isTargetToday) {
        if (plant.isQuarantined()) return false;
        try {
            LocalDate targetDate = LocalDate.parse(targetDateStr);
            return calculateNextWateringUseCase.isDueOn(
                    plant.getLastWatered(),
                    plant.getIntervalDays(),
                    plant.getIntervalDaysWinter(),
                    targetDate,
                    isTargetToday
            );
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFertilizingDueOn(PlantEntity plant, String targetDateStr, boolean isTargetToday) {
        if (plant.isQuarantined()) return false;
        try {
            LocalDate targetDate = LocalDate.parse(targetDateStr);
            return calculateNextFertilizingUseCase.isDueOn(
                    plant.getLastFert(),
                    plant.getEffectiveFertilizeIntervalSummerDays(),
                    plant.getFertilizeIntervalWinterDays(),
                    targetDate,
                    isTargetToday
            );
        } catch (Exception e) {
            return false;
        }
    }

    public void checkAndStartBatchSync() {
        if (allPlants.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_plants_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        if (CalendarSyncManager.getInstance(requireContext()).hasCalendarPermission()) {
            startBatchCalendarSync();
        } else {
            calendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR);
        }
    }

    private void startBatchCalendarSync() {
        CalendarSyncManager.getInstance(requireContext()).syncAllPlants(allPlants, new CalendarSyncManager.SyncCallback() {
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

    public void exportAllToIcs() {
        if (allPlants.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_plants_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            CalendarIntegrationHelper.exportAndShareIcs(requireContext(), allPlants);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Помилка експорту: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
