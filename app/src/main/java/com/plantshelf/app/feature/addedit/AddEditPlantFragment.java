package com.plantshelf.app.feature.addedit;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.plantshelf.app.R;
import com.plantshelf.app.data.ai.GeminiPlantAiService;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PhotoEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.data.repository.PlantRepository;
import com.plantshelf.app.databinding.ActivityAddEditPlantBinding;
import com.plantshelf.app.ui.dialog.ApiKeyDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Feature: Add and Edit Plant Fragment.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Bundling form editing within a Fragment enables fluid navigation without launching new
 * OS-level activities, keeping photo capture and AI identification within the single-activity scope.
 */
public class AddEditPlantFragment extends Fragment {

    public static final String ARG_PLANT_ID = "plantId";

    private ActivityAddEditPlantBinding binding;
    private PlantRepository repository;
    private final List<CategoryEntity> categoryList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    private String editingPlantId = null;
    private PlantEntity editingPlant = null;
    private boolean isPlantLoaded = false;

    private String selectedPhotoPath = null;
    private Uri cameraTempUri = null;
    private File cameraTempFile = null;

    private static final class PlantFormData {
        final String name;
        final String variety;
        final String latin;
        final String soil;
        final String warning;
        final String notes;
        final String categoryId;
        final int summerInterval;
        final int winterInterval;
        final int lux;
        final String recommendedFertilizers;
        final int fertSummerInterval;
        final int fertWinterInterval;
        final String today;

        PlantFormData(String name, String variety, String latin, String soil, String warning,
                      String notes, String categoryId, int summerInterval, int winterInterval,
                      int lux, String recommendedFertilizers, int fertSummerInterval,
                      int fertWinterInterval, String today) {
            this.name                   = name;
            this.variety                = variety;
            this.latin                  = latin;
            this.soil                   = soil;
            this.warning                = warning;
            this.notes                  = notes;
            this.categoryId             = categoryId;
            this.summerInterval         = summerInterval;
            this.winterInterval         = winterInterval;
            this.lux                    = lux;
            this.recommendedFertilizers = recommendedFertilizers;
            this.fertSummerInterval     = fertSummerInterval;
            this.fertWinterInterval     = fertWinterInterval;
            this.today                  = today;
        }
    }

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processPickedImageUri(uri);
                }
            }
    );

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && cameraTempFile != null && cameraTempFile.exists()) {
                    selectedPhotoPath = cameraTempFile.getAbsolutePath();
                    displayPhotoPreview(selectedPhotoPath);
                }
            }
    );

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            editingPlantId = getArguments().getString(ARG_PLANT_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ActivityAddEditPlantBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new PlantRepository(requireActivity().getApplication());

        binding.toolbarAdd.setNavigationOnClickListener(v -> navigateBack());

        boolean isEditMode = editingPlantId != null && !editingPlantId.isEmpty();
        if (isEditMode) {
            binding.toolbarAdd.setTitle(R.string.edit_plant_title);
            binding.btnSavePlant.setText(R.string.btn_save_changes);
        }

        setupCategorySpinner();
        setupPhotoButtons();
        setupAiIdentification();
        setupSaveButton();

        if (isEditMode) {
            loadPlantForEditing();
        }
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

    private void loadPlantForEditing() {
        repository.getPlantById(editingPlantId).observe(getViewLifecycleOwner(), plant -> {
            if (plant != null && !isPlantLoaded) {
                isPlantLoaded = true;
                editingPlant = plant;
                populateFields(plant);
            }
        });
    }

    private void populateFields(PlantEntity plant) {
        binding.etPlantName.setText(plant.getName());
        binding.etPlantVariety.setText(plant.getVariety() != null ? plant.getVariety() : "");
        binding.etPlantLatin.setText(plant.getLatin() != null ? plant.getLatin() : "");
        binding.etSoil.setText(plant.getSoil() != null ? plant.getSoil() : "");
        binding.etWarning.setText(plant.getWarning() != null ? plant.getWarning() : "");
        binding.etNotes.setText(plant.getNote() != null ? plant.getNote() : "");
        binding.etIntervalSummer.setText(String.valueOf(plant.getIntervalDays()));
        binding.etIntervalWinter.setText(String.valueOf(plant.getIntervalDaysWinter()));
        binding.etTargetLux.setText(String.valueOf(plant.getLux()));
        binding.etRecommendedFertilizers.setText(plant.getRecommendedFertilizers() != null ? plant.getRecommendedFertilizers() : "");
        binding.etFertSummer.setText(String.valueOf(plant.getFertilizeIntervalSummerDays()));
        binding.etFertWinter.setText(String.valueOf(plant.getFertilizeIntervalWinterDays()));

        selectedPhotoPath = plant.getPrimaryPhotoPath();
        if (selectedPhotoPath != null && new File(selectedPhotoPath).exists()) {
            displayPhotoPreview(selectedPhotoPath);
        }

        updateCategorySpinnerSelection(plant.getCategoryId());
    }

    private void updateCategorySpinnerSelection(String categoryId) {
        if (categoryId == null || categoryList.isEmpty()) {
            binding.spinnerCategories.setSelection(0);
            return;
        }
        for (int i = 0; i < categoryList.size(); i++) {
            if (categoryId.equals(categoryList.get(i).getId())) {
                binding.spinnerCategories.setSelection(i + 1);
                break;
            }
        }
    }

    private void setupCategorySpinner() {
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        binding.spinnerCategories.setAdapter(spinnerAdapter);

        repository.getAllCategories().observe(getViewLifecycleOwner(), categories -> {
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

            if (editingPlant != null) {
                updateCategorySpinnerSelection(editingPlant.getCategoryId());
            }
        });
    }

    private void setupPhotoButtons() {
        binding.btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        binding.btnCamera.setOnClickListener(v -> launchCamera());
    }

    private void launchCamera() {
        try {
            File cacheDir = new File(requireContext().getCacheDir(), "camera");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            cameraTempFile = new File(cacheDir, "camera_" + System.currentTimeMillis() + ".jpg");
            cameraTempUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    cameraTempFile
            );

            cameraLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Помилка запуску камери: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processPickedImageUri(Uri uri) {
        try {
            File photosDir = new File(requireContext().getFilesDir(), "photos");
            if (!photosDir.exists()) photosDir.mkdirs();

            File destFile = new File(photosDir, "plant_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg");

            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
            }

            selectedPhotoPath = destFile.getAbsolutePath();
            displayPhotoPreview(selectedPhotoPath);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Помилка збереження фото: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void displayPhotoPreview(String path) {
        if (!isAdded()) return;
        Glide.with(this)
                .load(new File(path))
                .transform(new CenterCrop(), new RoundedCorners(24))
                .into(binding.ivPlantPreview);
    }

    private void setupAiIdentification() {
        binding.btnAiIdentify.setOnClickListener(v -> {
            if (selectedPhotoPath == null) {
                Toast.makeText(requireContext(), "Спочатку оберіть фото рослини (з камери або галереї)", Toast.LENGTH_SHORT).show();
                return;
            }

            String apiKey = GeminiPlantAiService.getSavedApiKey(requireContext());
            if (apiKey.isEmpty()) {
                ApiKeyDialog.show(requireContext(), key -> startAiAnalysis(selectedPhotoPath));
                return;
            }

            startAiAnalysis(selectedPhotoPath);
        });
    }

    private void startAiAnalysis(String imagePath) {
        binding.progressAi.setVisibility(View.VISIBLE);
        binding.btnAiIdentify.setEnabled(false);
        Toast.makeText(requireContext(), R.string.ai_identifying, Toast.LENGTH_SHORT).show();

        GeminiPlantAiService.identifyPlantByPhoto(requireContext(), new File(imagePath),
                new GeminiPlantAiService.AiAnalysisCallback() {
            @Override
            public void onSuccess(com.plantshelf.app.data.ai.AiPlantAnalysisResult result) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.progressAi.setVisibility(View.GONE);
                            binding.btnAiIdentify.setEnabled(true);
                            if (result != null) applyAiResult(result);
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.progressAi.setVisibility(View.GONE);
                            binding.btnAiIdentify.setEnabled(true);
                            com.plantshelf.app.data.ai.AiErrorLogger.showErrorFeedbackDialog(
                                    requireContext(),
                                    "Помилка розпізнавання рослини",
                                    e,
                                    () -> startAiAnalysis(imagePath)
                            );
                        }
                    });
                }
            }
        });
    }

    private void applyAiResult(com.plantshelf.app.data.ai.AiPlantAnalysisResult result) {
        if (!result.getName().isEmpty())    binding.etPlantName.setText(result.getName());
        if (!result.getVariety().isEmpty()) binding.etPlantVariety.setText(result.getVariety());
        if (!result.getLatin().isEmpty())   binding.etPlantLatin.setText(result.getLatin());

        binding.etIntervalSummer.setText(String.valueOf(result.getIntervalDaysSummer()));
        binding.etIntervalWinter.setText(String.valueOf(result.getIntervalDaysWinter()));
        binding.etTargetLux.setText(String.valueOf(result.getLux()));

        if (!result.getSoil().isEmpty())    binding.etSoil.setText(result.getSoil());
        if (!result.getWarning().isEmpty()) binding.etWarning.setText(result.getWarning());
        if (!result.getNotes().isEmpty())   binding.etNotes.setText(result.getNotes());
        if (!result.getRecommendedFertilizers().isEmpty()) {
            binding.etRecommendedFertilizers.setText(result.getRecommendedFertilizers());
        }
        binding.etFertSummer.setText(String.valueOf(result.getFertilizeIntervalSummerDays()));
        binding.etFertWinter.setText(String.valueOf(result.getFertilizeIntervalWinterDays()));

        Toast.makeText(requireContext(), R.string.ai_success, Toast.LENGTH_LONG).show();
    }

    private void setupSaveButton() {
        binding.btnSavePlant.setOnClickListener(v -> {
            String name = getText(binding.etPlantName);
            if (name.isEmpty()) {
                binding.etPlantName.setError("Введіть назву рослини");
                return;
            }
            savePlant(name);
        });
    }

    private void savePlant(String name) {
        String variety = getText(binding.etPlantVariety);
        String latin   = getText(binding.etPlantLatin);
        String soil    = getText(binding.etSoil);
        String warning = getText(binding.etWarning);
        String notes   = getText(binding.etNotes);

        int summerInterval = parseIntField(binding.etIntervalSummer, 7);
        int winterInterval = parseIntField(binding.etIntervalWinter, summerInterval);
        int targetLux      = parseIntField(binding.etTargetLux, 10000);

        String recommendedFertilizers = getText(binding.etRecommendedFertilizers);
        int fertSummerInterval = parseIntField(binding.etFertSummer, 14);
        int fertWinterInterval = parseIntField(binding.etFertWinter, 0);

        String selectedCategoryId = null;
        int spinnerPos = binding.spinnerCategories.getSelectedItemPosition();
        if (spinnerPos > 0 && spinnerPos - 1 < categoryList.size()) {
            selectedCategoryId = categoryList.get(spinnerPos - 1).getId();
        }

        String today = LocalDate.now(ZoneId.systemDefault()).toString();

        PlantFormData data = new PlantFormData(name, variety, latin, soil, warning, notes,
                selectedCategoryId, summerInterval, winterInterval, targetLux,
                recommendedFertilizers, fertSummerInterval, fertWinterInterval, today);

        if (editingPlant != null) {
            updateExistingPlant(data);
        } else {
            saveNewPlant(data);
        }
    }

    private void updateExistingPlant(PlantFormData d) {
        editingPlant.setName(d.name);
        editingPlant.setVariety(d.variety);
        editingPlant.setLatin(d.latin);
        editingPlant.setSoil(d.soil);
        editingPlant.setWarning(d.warning);
        editingPlant.setNote(d.notes);
        editingPlant.setCategoryId(d.categoryId);
        editingPlant.setIntervalDays(d.summerInterval);
        editingPlant.setIntervalDaysWinter(d.winterInterval);
        editingPlant.setLux(d.lux);
        editingPlant.setRecommendedFertilizers(d.recommendedFertilizers);
        editingPlant.setFertilizeIntervalSummerDays(d.fertSummerInterval);
        editingPlant.setFertilizeIntervalWinterDays(d.fertWinterInterval);
        editingPlant.setFertIntervalDays(d.fertSummerInterval);

        boolean photoChanged = selectedPhotoPath != null
                && !selectedPhotoPath.equals(editingPlant.getPrimaryPhotoPath());
        if (selectedPhotoPath != null) {
            editingPlant.setPrimaryPhotoPath(selectedPhotoPath);
        }

        repository.updatePlant(editingPlant);

        if (photoChanged) {
            addPhotoRecord(editingPlant.getId(), d.today, selectedPhotoPath);
        }

        Toast.makeText(requireContext(), R.string.plant_updated_success, Toast.LENGTH_SHORT).show();
        navigateBack();
    }

    private void saveNewPlant(PlantFormData d) {
        String plantId = UUID.randomUUID().toString().substring(0, 8);
        PlantEntity plant = new PlantEntity(plantId);
        plant.setName(d.name);
        plant.setVariety(d.variety);
        plant.setLatin(d.latin);
        plant.setSoil(d.soil);
        plant.setWarning(d.warning);
        plant.setNote(d.notes);
        plant.setCategoryId(d.categoryId);
        plant.setIntervalDays(d.summerInterval);
        plant.setIntervalDaysWinter(d.winterInterval);
        plant.setLux(d.lux);
        plant.setRecommendedFertilizers(d.recommendedFertilizers);
        plant.setFertilizeIntervalSummerDays(d.fertSummerInterval);
        plant.setFertilizeIntervalWinterDays(d.fertWinterInterval);
        plant.setFertIntervalDays(d.fertSummerInterval);
        plant.setPrimaryPhotoPath(selectedPhotoPath);
        plant.setLastWatered(d.today);
        plant.setCreatedAt(d.today);

        repository.insertPlant(plant);

        if (selectedPhotoPath != null) {
            addPhotoRecord(plantId, d.today, selectedPhotoPath);
        }

        Toast.makeText(requireContext(), "Рослину додано!", Toast.LENGTH_SHORT).show();
        navigateBack();
    }

    private void addPhotoRecord(String plantId, String date, String path) {
        String photoId = UUID.randomUUID().toString().substring(0, 8);
        PhotoEntity photo = new PhotoEntity(photoId, plantId, date, path);
        new Thread(() -> PlantshelfDatabase.getInstance(requireContext()).photoDao().insert(photo)).start();
    }

    private String getText(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private int parseIntField(android.widget.EditText et, int fallback) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
