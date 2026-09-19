package com.plantshelf.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

    private final androidx.activity.result.ActivityResultLauncher<String> calendarPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                PlantEntity plant = viewModel.getPlant().getValue();
                if (plant == null) return;
                if (Boolean.TRUE.equals(isGranted)) {
                    syncPlantToCalendar(plant);
                } else {
                    com.plantshelf.app.data.calendar.CalendarIntegrationHelper.addPlantCareToSystemCalendar(this, plant, "water");
                }
            });

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

        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                com.plantshelf.app.data.entity.CareLogEntity log = careLogAdapter.getItem(position);
                if (log != null) {
                    viewModel.deleteCareLog(log);
                    Snackbar.make(binding.getRoot(), "Запис видалено з історії", Snackbar.LENGTH_LONG)
                            .setDuration(4000)
                            .setAction("Скасувати", v -> viewModel.restoreCareLog(log))
                            .show();
                }
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvCareHistory);
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

        // Fertilizing Section
        String fertRec = plant.getRecommendedFertilizers();
        if (fertRec != null && !fertRec.trim().isEmpty()) {
            binding.tvFertilizerType.setText("Рекомендовано: " + fertRec);
        } else {
            binding.tvFertilizerType.setText("Рекомендовано: Комплексне добриво з мікроелементами");
        }
        int fertSummer = plant.getFertilizeIntervalSummerDays();
        binding.tvFertilizerSummerSchedule.setText(getString(R.string.days_unit, fertSummer));
        int fertWinter = plant.getFertilizeIntervalWinterDays();
        if (fertWinter > 0) {
            binding.tvFertilizerWinterSchedule.setText(getString(R.string.days_unit, fertWinter));
        } else {
            binding.tvFertilizerWinterSchedule.setText("Період спокою (без добрив)");
        }

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

        // Quarantine
        if (plant.isQuarantined()) {
            binding.cardQuarantineBanner.setVisibility(View.VISIBLE);
            String details = getString(R.string.quarantine_banner_until, plant.getQuarantineUntil());
            if (plant.getQuarantineReason() != null && !plant.getQuarantineReason().isEmpty()) {
                details += " • " + getString(R.string.quarantine_reason, plant.getQuarantineReason());
            }
            binding.tvQuarantineDetails.setText(details);
            binding.btnToggleQuarantine.setText(R.string.btn_remove_quarantine);
        } else {
            binding.cardQuarantineBanner.setVisibility(View.GONE);
            binding.btnToggleQuarantine.setText(R.string.btn_add_quarantine);
        }

        // Photo
        if (plant.getPrimaryPhotoPath() != null && new File(plant.getPrimaryPhotoPath()).exists()) {
            Glide.with(this)
                    .load(new File(plant.getPrimaryPhotoPath()))
                    .centerCrop()
                    .placeholder(R.drawable.ic_placeholder_plant)
                    .into(binding.ivDetailPhoto);
        }

        if (plant.getCalendarEventId() != null && !plant.getCalendarEventId().isEmpty()) {
            binding.btnAddToCalendar.setText("Синхронізовано з календарем");
        } else {
            binding.btnAddToCalendar.setText(R.string.action_add_to_system_calendar);
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

        binding.btnActionTreatment.setOnClickListener(v -> {
            com.plantshelf.app.ui.dialog.TreatmentDialog.show(this, (drug, notes, putOnQuarantine) -> {
                viewModel.recordTreatment(drug, notes, putOnQuarantine, 14, "Обробка: " + drug);
                Snackbar.make(binding.getRoot(), R.string.action_treated_success, Snackbar.LENGTH_SHORT).show();
            });
        });

        binding.btnRemoveQuarantine.setOnClickListener(v -> {
            viewModel.updateQuarantine(null, null);
            Snackbar.make(binding.getRoot(), "Рослину знято з карантину", Snackbar.LENGTH_SHORT).show();
        });

        binding.btnToggleQuarantine.setOnClickListener(v -> {
            PlantEntity plant = viewModel.getPlant().getValue();
            if (plant != null) {
                if (plant.isQuarantined()) {
                    viewModel.updateQuarantine(null, null);
                    Snackbar.make(binding.getRoot(), "Рослину знято з карантину", Snackbar.LENGTH_SHORT).show();
                } else {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    c.add(java.util.Calendar.DAY_OF_YEAR, 14);
                    String until = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(c.getTime());
                    viewModel.updateQuarantine(until, "Ізоляція / Профілактика");
                    Snackbar.make(binding.getRoot(), "Рослину поміщено на карантин на 14 днів", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        binding.btnAddToCalendar.setOnClickListener(v -> handleCalendarClick());
    }

    private void handleCalendarClick() {
        PlantEntity plant = viewModel.getPlant().getValue();
        if (plant == null) return;

        if (plant.getCalendarEventId() != null && !plant.getCalendarEventId().isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Подія в календарі")
                    .setMessage("Ця рослина вже синхронізована з системним календарем.")
                    .setPositiveButton("Оновити зараз", (dialog, which) -> {
                        boolean ok = com.plantshelf.app.data.calendar.CalendarIntegrationHelper.updateCalendarEventSchedule(this, plant);
                        if (ok) {
                            Snackbar.make(binding.getRoot(), "Графік оновлено в календарі", Snackbar.LENGTH_SHORT).show();
                        } else {
                            syncPlantToCalendar(plant);
                        }
                    })
                    .setNegativeButton("Видалити подію", (dialog, which) -> {
                        com.plantshelf.app.data.calendar.CalendarIntegrationHelper.deleteCalendarEvent(this, plant.getCalendarEventId());
                        plant.setCalendarEventId(null);
                        viewModel.updatePlant(plant);
                        binding.btnAddToCalendar.setText(R.string.action_add_to_system_calendar);
                        Snackbar.make(binding.getRoot(), "Подію видалено з календаря", Snackbar.LENGTH_SHORT).show();
                    })
                    .setNeutralButton("Закрити", null)
                    .show();
        } else {
            if (com.plantshelf.app.data.calendar.CalendarIntegrationHelper.hasCalendarPermission(this)) {
                syncPlantToCalendar(plant);
            } else {
                calendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR);
            }
        }
    }

    private void syncPlantToCalendar(PlantEntity plant) {
        String eventId = com.plantshelf.app.data.calendar.CalendarIntegrationHelper.insertCalendarEventDirect(this, plant, "water");
        if (eventId != null) {
            plant.setCalendarEventId(eventId);
            viewModel.updatePlant(plant);
            binding.btnAddToCalendar.setText("Синхронізовано з календарем");
            Snackbar.make(binding.getRoot(), "✅ Додано в системний календар з автооновленням!", Snackbar.LENGTH_SHORT).show();
        } else {
            com.plantshelf.app.data.calendar.CalendarIntegrationHelper.addPlantCareToSystemCalendar(this, plant, "water");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.plant_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_edit_plant) {
            PlantEntity plant = viewModel.getPlant().getValue();
            if (plant != null) {
                Intent intent = new Intent(this, AddEditPlantActivity.class);
                intent.putExtra(AddEditPlantActivity.EXTRA_PLANT_ID, plant.getId());
                startActivity(intent);
            }
            return true;
        } else if (item.getItemId() == R.id.action_delete_plant) {
            confirmDeletePlant();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDeletePlant() {
        PlantEntity plant = viewModel.getPlant().getValue();
        if (plant == null) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_plant_title)
                .setMessage(getString(R.string.delete_plant_confirm_message, plant.getName()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    viewModel.deletePlant(plant);
                    Toast.makeText(this, R.string.plant_deleted_success, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
