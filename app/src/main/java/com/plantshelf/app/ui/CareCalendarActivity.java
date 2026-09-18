package com.plantshelf.app.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.plantshelf.app.R;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.models.CalendarTask;
import com.plantshelf.app.data.repository.PlantRepository;
import com.plantshelf.app.databinding.ActivityCareCalendarBinding;
import com.plantshelf.app.ui.adapter.CalendarTaskAdapter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CareCalendarActivity extends AppCompatActivity {

    private ActivityCareCalendarBinding binding;
    private PlantRepository repository;
    private CalendarTaskAdapter taskAdapter;

    private final List<PlantEntity> allPlants = new ArrayList<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();
    private final Calendar selectedCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("d MMMM yyyy", new Locale("uk"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCareCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarCalendar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        repository = new PlantRepository(getApplication());

        setupRecyclerView();
        setupCalendarView();
        loadData();
    }

    private void setupRecyclerView() {
        taskAdapter = new CalendarTaskAdapter(task -> {
            if ("water".equalsIgnoreCase(task.getTaskType())) {
                repository.recordWatering(task.getPlantId());
            } else if ("fert".equalsIgnoreCase(task.getTaskType())) {
                repository.recordFertilizing(task.getPlantId());
            } else {
                repository.recordMisting(task.getPlantId());
            }
            Toast.makeText(this, R.string.task_done, Toast.LENGTH_SHORT).show();
        });

        binding.rvCalendarTasks.setLayoutManager(new LinearLayoutManager(this));
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
        repository.getAllCategories().observe(this, categories -> {
            categoryMap.clear();
            if (categories != null) {
                for (CategoryEntity c : categories) {
                    categoryMap.put(c.getId(), c);
                }
            }
            computeTasksForSelectedDate();
        });

        repository.getAllPlants().observe(this, plants -> {
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

            // Check watering
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

            // Check fertilizing
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

        String lastWatered = plant.getLastWatered();
        if (lastWatered == null || lastWatered.isEmpty()) {
            // Never watered -> due today
            return isTargetToday;
        }

        try {
            Date lastDate = dateFormat.parse(lastWatered);
            if (lastDate == null) return false;

            Calendar cal = Calendar.getInstance();
            cal.setTime(lastDate);

            int month = cal.get(Calendar.MONTH);
            boolean isWinter = (month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY);
            int interval = isWinter ? plant.getIntervalDaysWinter() : plant.getIntervalDays();
            cal.add(Calendar.DAY_OF_YEAR, interval);

            String dueDateStr = dateFormat.format(cal.getTime());

            if (isTargetToday) {
                // If checking today, include overdue tasks
                return dueDateStr.compareTo(targetDateStr) <= 0;
            } else {
                // For future or past specific date
                return dueDateStr.equals(targetDateStr);
            }
        } catch (ParseException e) {
            return false;
        }
    }

    private boolean isFertilizingDueOn(PlantEntity plant, String targetDateStr, boolean isTargetToday) {
        if (plant.isQuarantined()) return false;
        int fertInterval = plant.getFertIntervalDays();
        if (fertInterval <= 0) return false;

        String lastFert = plant.getLastFert();
        if (lastFert == null || lastFert.isEmpty()) return false;

        try {
            Date lastDate = dateFormat.parse(lastFert);
            if (lastDate == null) return false;

            Calendar cal = Calendar.getInstance();
            cal.setTime(lastDate);
            cal.add(Calendar.DAY_OF_YEAR, fertInterval);

            String dueDateStr = dateFormat.format(cal.getTime());
            if (isTargetToday) {
                return dueDateStr.compareTo(targetDateStr) <= 0;
            } else {
                return dueDateStr.equals(targetDateStr);
            }
        } catch (ParseException e) {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.calendar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_export_calendar) {
            exportAllToIcs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportAllToIcs() {
        if (allPlants == null || allPlants.isEmpty()) {
            Toast.makeText(this, R.string.no_plants_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            com.plantshelf.app.data.calendar.CalendarIntegrationHelper.exportAndShareIcs(this, allPlants);
        } catch (Exception e) {
            Toast.makeText(this, "Помилка експорту: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
