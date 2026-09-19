package com.plantshelf.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

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

public class AddEditPlantActivity extends AppCompatActivity {

    public static final String EXTRA_PLANT_ID = "extra_plant_id";

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

    /**
     * Bundles all plant form fields into a single object,
     * avoiding methods with more than 7 parameters.
     */
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

    // Gallery Picker Launcher
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processPickedImageUri(uri);
                }
            }
    );

    // Camera Launcher
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddEditPlantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarAdd);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editingPlantId = getIntent().getStringExtra(EXTRA_PLANT_ID);
        boolean isEditMode = editingPlantId != null && !editingPlantId.isEmpty();
        if (isEditMode) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.edit_plant_title);
            }
            binding.btnSavePlant.setText(R.string.btn_save_changes);
        }

        repository = new PlantRepository(getApplication());

        setupCategorySpinner();
        setupPhotoButtons();
        setupAiIdentification();
        setupSaveButton();

        if (isEditMode) {
            loadPlantForEditing();
        }
    }

    private void loadPlantForEditing() {
        repository.getPlantById(editingPlantId).observe(this, plant -> {
            if (plant != null && !isPlantLoaded) {
                isPlantLoaded = true;
                editingPlant = plant;
                populateFields(plant);
            }
        });
    }

    /** Fills all form fields from an existing {@link PlantEntity}. */
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
            File cacheDir = new File(getCacheDir(), "camera");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            cameraTempFile = new File(cacheDir, "camera_" + System.currentTimeMillis() + ".jpg");
            cameraTempUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    cameraTempFile
            );

            cameraLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(this, "Помилка запуску камери: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processPickedImageUri(Uri uri) {
        try {
            File photosDir = new File(getFilesDir(), "photos");
            if (!photosDir.exists()) photosDir.mkdirs();

            File destFile = new File(photosDir, "plant_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg");

            try (InputStream is = getContentResolver().openInputStream(uri);
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
            Toast.makeText(this, "Помилка збереження фото: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void displayPhotoPreview(String path) {
        Glide.with(this)
                .load(new File(path))
                .transform(new CenterCrop(), new RoundedCorners(24))
                .into(binding.ivPlantPreview);
    }

    private void setupAiIdentification() {
        binding.btnAiIdentify.setOnClickListener(v -> {
            if (selectedPhotoPath == null) {
                Toast.makeText(this, "Спочатку оберіть фото рослини (з камери або галереї)", Toast.LENGTH_SHORT).show();
                return;
            }

            String apiKey = GeminiPlantAiService.getSavedApiKey(this);
            if (apiKey.isEmpty()) {
                ApiKeyDialog.show(this, key -> startAiAnalysis(selectedPhotoPath));
                return;
            }

            startAiAnalysis(selectedPhotoPath);
        });
    }

    private void startAiAnalysis(String imagePath) {
        binding.progressAi.setVisibility(View.VISIBLE);
        binding.btnAiIdentify.setEnabled(false);
        Toast.makeText(this, R.string.ai_identifying, Toast.LENGTH_SHORT).show();

        GeminiPlantAiService.identifyPlantByPhoto(this, new File(imagePath),
                new GeminiPlantAiService.AiAnalysisCallback() {
            @Override
            public void onSuccess(com.plantshelf.app.data.ai.AiPlantAnalysisResult result) {
                runOnUiThread(() -> {
                    binding.progressAi.setVisibility(View.GONE);
                    binding.btnAiIdentify.setEnabled(true);
                    if (result != null) applyAiResult(result);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    binding.progressAi.setVisibility(View.GONE);
                    binding.btnAiIdentify.setEnabled(true);
                    Toast.makeText(AddEditPlantActivity.this,
                            getString(R.string.ai_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Fills form fields with the AI-identified plant data. */
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

        Toast.makeText(this, R.string.ai_success, Toast.LENGTH_LONG).show();
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

    /** Reads all text fields and delegates to update or insert path. */
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

    /** Applies edited field values to the existing plant and persists changes. */
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

        Toast.makeText(this, R.string.plant_updated_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Creates and inserts a new plant entity. */
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

        Toast.makeText(this, "Рослину додано!", Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Inserts a PhotoEntity record on a background thread. */
    private void addPhotoRecord(String plantId, String date, String path) {
        String photoId = UUID.randomUUID().toString().substring(0, 8);
        PhotoEntity photo = new PhotoEntity(photoId, plantId, date, path);
        new Thread(() -> PlantshelfDatabase.getInstance(this).photoDao().insert(photo)).start();
    }

    /** Safe text extraction from an EditText — never returns null. */
    private String getText(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    /** Parses an integer from an EditText, returning {@code fallback} if the field is blank or invalid. */
    private int parseIntField(android.widget.EditText et, int fallback) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (NumberFormatException ignored) {
            // Field is empty or non-numeric — use the provided fallback value
            return fallback;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        if (editingPlantId != null && !editingPlantId.isEmpty()) {
            getMenuInflater().inflate(R.menu.add_edit_plant_menu, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_delete_plant) {
            confirmDeleteEditingPlant();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDeleteEditingPlant() {
        if (editingPlant == null) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_plant_title)
                .setMessage(getString(R.string.delete_plant_confirm_message, editingPlant.getName()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    repository.deletePlant(editingPlant);
                    Toast.makeText(this, R.string.plant_deleted_success, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
