package com.plantshelf.app.feature.detail;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.plantshelf.app.R;
import com.plantshelf.app.data.calendar.CalendarIntegrationHelper;
import com.plantshelf.app.data.entity.CareLogEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.databinding.ActivityPlantDetailBinding;
import com.plantshelf.app.ui.AddEditPlantActivity;
import com.plantshelf.app.ui.adapter.CareLogAdapter;
import com.plantshelf.app.viewmodel.PlantDetailViewModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Feature: Plant Detail Profile Fragment.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Encapsulating detailed plant profile views inside a Fragment decouples screen layout from
 * the root Activity window, unlocking fluid shared-element transitions and single-activity navigation.
 */
public class PlantDetailFragment extends Fragment {

    public static final String ARG_PLANT_ID = "plantId";

    private ActivityPlantDetailBinding binding;
    private PlantDetailViewModel viewModel;
    private CareLogAdapter careLogAdapter;
    private String plantId;

    private final androidx.activity.result.ActivityResultLauncher<String> calendarPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                PlantEntity plant = viewModel.getPlant().getValue();
                if (plant == null) return;
                if (Boolean.TRUE.equals(isGranted)) {
                    syncPlantToCalendar(plant);
                } else {
                    CalendarIntegrationHelper.addPlantCareToSystemCalendar(requireContext(), plant, "water");
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            plantId = getArguments().getString(ARG_PLANT_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ActivityPlantDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbarDetail.setNavigationOnClickListener(v -> navigateBack());
        binding.toolbarDetail.inflateMenu(R.menu.plant_detail_menu);
        binding.toolbarDetail.setOnMenuItemClickListener(this::handleMenuItemClick);

        if (plantId == null || plantId.isEmpty()) {
            navigateBack();
            return;
        }

        viewModel = new ViewModelProvider(this).get(PlantDetailViewModel.class);
        viewModel.setPlantId(plantId);

        setupHistoryRecyclerView();
        observePlant();
        setupActions();
    }

    private void navigateBack() {
        try {
            if (!Navigation.findNavController(requireView()).navigateUp()) {
                requireActivity().onBackPressed();
            }
        } catch (Exception e) {
            requireActivity().onBackPressed();
        }
    }

    private boolean handleMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_edit_plant) {
            PlantEntity plant = viewModel.getPlant().getValue();
            if (plant != null) {
                Bundle args = new Bundle();
                args.putString("plantId", plant.getId());
                try {
                    Navigation.findNavController(requireView()).navigate(R.id.action_plantDetailFragment_to_addEditPlantFragment, args);
                } catch (Exception e) {
                    Intent intent = new Intent(requireContext(), AddEditPlantActivity.class);
                    intent.putExtra(AddEditPlantActivity.EXTRA_PLANT_ID, plant.getId());
                    startActivity(intent);
                }
            }
            return true;
        } else if (item.getItemId() == R.id.action_delete_plant) {
            confirmDeletePlant();
            return true;
        }
        return false;
    }

    private void setupHistoryRecyclerView() {
        careLogAdapter = new CareLogAdapter();
        binding.rvCareHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCareHistory.setAdapter(careLogAdapter);

        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            private final ColorDrawable deleteBackground = new ColorDrawable(Color.parseColor("#D32F2F"));

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    position = viewHolder.getAdapterPosition();
                }

                CareLogEntity log = careLogAdapter.getItem(position);
                if (log == null) {
                    careLogAdapter.notifyDataSetChanged();
                    return;
                }

                showDeleteCareLogConfirmation(log, position);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    Drawable deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete);
                    if (deleteIcon != null) {
                        deleteIcon = deleteIcon.mutate();
                        DrawableCompat.setTint(deleteIcon, Color.WHITE);
                    }

                    int itemHeight = itemView.getHeight();
                    int iconMargin = deleteIcon != null ? (itemHeight - deleteIcon.getIntrinsicHeight()) / 2 : 0;

                    if (dX > 0) {
                        deleteBackground.setBounds(itemView.getLeft(), itemView.getTop(), (int) (itemView.getLeft() + dX), itemView.getBottom());
                        deleteBackground.draw(c);

                        if (deleteIcon != null) {
                            int iconTop = itemView.getTop() + (itemHeight - deleteIcon.getIntrinsicHeight()) / 2;
                            int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                            int iconLeft = itemView.getLeft() + iconMargin;
                            int iconRight = iconLeft + deleteIcon.getIntrinsicWidth();
                            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            deleteIcon.draw(c);
                        }
                    } else if (dX < 0) {
                        deleteBackground.setBounds((int) (itemView.getRight() + dX), itemView.getTop(), itemView.getRight(), itemView.getBottom());
                        deleteBackground.draw(c);

                        if (deleteIcon != null) {
                            int iconTop = itemView.getTop() + (itemHeight - deleteIcon.getIntrinsicHeight()) / 2;
                            int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                            int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                            int iconRight = itemView.getRight() - iconMargin;
                            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            deleteIcon.draw(c);
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvCareHistory);
    }

    private void showDeleteCareLogConfirmation(CareLogEntity log, int position) {
        String title = CareLogAdapter.getLocalizedKindTitle(log);
        String formattedDate = CareLogAdapter.getFormattedDate(log);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Видалити запис")
                .setMessage("Ви дійсно бажаєте видалити цей запис догляду (" + title + " - " + formattedDate + ")?")
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    viewModel.deleteCareLog(log);
                    Snackbar.make(binding.getRoot(), "Запис видалено", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, (dialog, which) -> {
                    careLogAdapter.notifyItemChanged(position);
                })
                .setOnCancelListener(dialog -> {
                    careLogAdapter.notifyItemChanged(position);
                })
                .show();
    }

    private void observePlant() {
        viewModel.getPlant().observe(getViewLifecycleOwner(), plant -> {
            if (plant == null) return;
            bindPlantDetails(plant);
        });

        viewModel.getCareLogs().observe(getViewLifecycleOwner(), logs -> {
            careLogAdapter.setLogs(logs);
            binding.rvCareHistory.setVisibility(logs != null && !logs.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void bindPlantDetails(PlantEntity plant) {
        binding.collapsingToolbar.setTitle(plant.getName());
        binding.tvDetailTitle.setText(plant.getName());

        String subtitle = !plant.getVariety().isEmpty() ? plant.getVariety() : plant.getLatin();
        binding.tvDetailLatin.setText(subtitle != null ? subtitle : "");

        binding.tvSummerInterval.setText(getString(R.string.days_unit, plant.getIntervalDays()));
        binding.tvWinterInterval.setText(getString(R.string.days_unit, plant.getIntervalDaysWinter()));
        binding.tvFertInterval.setText(getString(R.string.days_unit, plant.getFertIntervalDays()));

        String fertRec = plant.getEffectiveRecommendedFertilizers();
        if (!fertRec.isEmpty()) {
            binding.tvFertilizerType.setText(getString(R.string.fertilizer_recommended_prefix, fertRec));
        } else {
            binding.tvFertilizerType.setText(getString(R.string.fertilizer_recommended_prefix, getString(R.string.fertilizer_default_recommended)));
        }

        if (plant.getLastFert() != null && !plant.getLastFert().isEmpty()) {
            binding.tvLastFertilizedDate.setText(getString(R.string.fertilizer_last_date, plant.getLastFert()));
        } else {
            binding.tvLastFertilizedDate.setText(getString(R.string.fertilizer_last_date, getString(R.string.fertilizer_never_fertilized)));
        }

        int fertSummer = plant.getEffectiveFertilizeIntervalSummerDays();
        binding.tvFertilizerSummerSchedule.setText(getString(R.string.fertilizer_every_n_days, fertSummer));

        int fertWinter = plant.getFertilizeIntervalWinterDays();
        if (fertWinter > 0) {
            binding.tvFertilizerWinterSchedule.setText(getString(R.string.fertilizer_every_n_days, fertWinter));
        } else {
            binding.tvFertilizerWinterSchedule.setText(getString(R.string.fertilizer_dormant_period));
        }

        int waterSummer = plant.getIntervalDays() > 0 ? plant.getIntervalDays() : 7;
        int waterWinter = plant.getIntervalDaysWinter() > 0 ? plant.getIntervalDaysWinter() : (waterSummer + 4);
        binding.tvWateringSummerSchedule.setText(getString(R.string.fertilizer_every_n_days, waterSummer));
        binding.tvWateringWinterSchedule.setText(getString(R.string.fertilizer_every_n_days, waterWinter));

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

        binding.btnCardFertilizeAction.setOnClickListener(v -> {
            viewModel.recordFertilizing();
            Snackbar.make(binding.getRoot(), R.string.action_fertilized_success, Snackbar.LENGTH_SHORT).show();
        });

        binding.btnActionMist.setOnClickListener(v -> {
            viewModel.recordMisting();
            Snackbar.make(binding.getRoot(), R.string.action_misted_success, Snackbar.LENGTH_SHORT).show();
        });

        binding.btnActionTreatment.setOnClickListener(v -> {
            com.plantshelf.app.ui.dialog.TreatmentDialog.show(requireContext(), (drug, notes, putOnQuarantine) -> {
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
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.DAY_OF_YEAR, 14);
                    String until = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
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
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Подія в календарі")
                    .setMessage("Ця рослина вже синхронізована з системним календарем.")
                    .setPositiveButton("Оновити зараз", (dialog, which) -> {
                        boolean ok = CalendarIntegrationHelper.updateCalendarEventSchedule(requireContext(), plant);
                        if (ok) {
                            Snackbar.make(binding.getRoot(), "Графік оновлено в календарі", Snackbar.LENGTH_SHORT).show();
                        } else {
                            syncPlantToCalendar(plant);
                        }
                    })
                    .setNegativeButton("Видалити подію", (dialog, which) -> {
                        CalendarIntegrationHelper.deleteCalendarEvent(requireContext(), plant.getCalendarEventId());
                        plant.setCalendarEventId(null);
                        viewModel.updatePlant(plant);
                        binding.btnAddToCalendar.setText(R.string.action_add_to_system_calendar);
                        Snackbar.make(binding.getRoot(), "Подію видалено з календаря", Snackbar.LENGTH_SHORT).show();
                    })
                    .setNeutralButton("Закрити", null)
                    .show();
        } else {
            if (CalendarIntegrationHelper.hasCalendarPermission(requireContext())) {
                syncPlantToCalendar(plant);
            } else {
                calendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR);
            }
        }
    }

    private void syncPlantToCalendar(PlantEntity plant) {
        String eventId = CalendarIntegrationHelper.insertCalendarEventDirect(requireContext(), plant, "water");
        if (eventId != null) {
            plant.setCalendarEventId(eventId);
            viewModel.updatePlant(plant);
            binding.btnAddToCalendar.setText("Синхронізовано з календарем");
            Snackbar.make(binding.getRoot(), "✅ Додано в системний календар з автооновленням!", Snackbar.LENGTH_SHORT).show();
        } else {
            CalendarIntegrationHelper.addPlantCareToSystemCalendar(requireContext(), plant, "water");
        }
    }

    private void confirmDeletePlant() {
        PlantEntity plant = viewModel.getPlant().getValue();
        if (plant == null) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_plant_title)
                .setMessage(getString(R.string.delete_plant_confirm_message, plant.getName()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    viewModel.deletePlant(plant);
                    Toast.makeText(requireContext(), R.string.plant_deleted_success, Toast.LENGTH_SHORT).show();
                    navigateBack();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
