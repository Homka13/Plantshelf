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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AddEditPlantActivity extends AppCompatActivity {

    private ActivityAddEditPlantBinding binding;
    private PlantRepository repository;
    private final List<CategoryEntity> categoryList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    private String selectedPhotoPath = null;
    private Uri cameraTempUri = null;
    private File cameraTempFile = null;

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

        repository = new PlantRepository(getApplication());

        setupCategorySpinner();
        setupPhotoButtons();
        setupAiIdentification();
        setupSaveButton();
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

        GeminiPlantAiService.identifyPlantByPhoto(this, new File(imagePath), new GeminiPlantAiService.AiAnalysisCallback() {
            @Override
            public void onSuccess(com.plantshelf.app.data.ai.AiPlantAnalysisResult result) {
                runOnUiThread(() -> {
                    binding.progressAi.setVisibility(View.GONE);
                    binding.btnAiIdentify.setEnabled(true);

                    if (result != null) {
                        if (!result.getName().isEmpty()) binding.etPlantName.setText(result.getName());
                        if (!result.getVariety().isEmpty()) binding.etPlantVariety.setText(result.getVariety());
                        if (!result.getLatin().isEmpty()) binding.etPlantLatin.setText(result.getLatin());

                        binding.etIntervalSummer.setText(String.valueOf(result.getIntervalDaysSummer()));
                        binding.etIntervalWinter.setText(String.valueOf(result.getIntervalDaysWinter()));
                        binding.etTargetLux.setText(String.valueOf(result.getLux()));

                        if (!result.getSoil().isEmpty()) binding.etSoil.setText(result.getSoil());
                        if (!result.getWarning().isEmpty()) binding.etWarning.setText(result.getWarning());
                        if (!result.getNotes().isEmpty()) binding.etNotes.setText(result.getNotes());

                        Toast.makeText(AddEditPlantActivity.this, R.string.ai_success, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    binding.progressAi.setVisibility(View.GONE);
                    binding.btnAiIdentify.setEnabled(true);
                    String msg = getString(R.string.ai_error, e.getMessage());
                    Toast.makeText(AddEditPlantActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setupSaveButton() {
        binding.btnSavePlant.setOnClickListener(v -> {
            String name = binding.etPlantName.getText() != null ? binding.etPlantName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                binding.etPlantName.setError("Введіть назву рослини");
                return;
            }

            String variety = binding.etPlantVariety.getText() != null ? binding.etPlantVariety.getText().toString().trim() : "";
            String latin = binding.etPlantLatin.getText() != null ? binding.etPlantLatin.getText().toString().trim() : "";
            String soil = binding.etSoil.getText() != null ? binding.etSoil.getText().toString().trim() : "";
            String warning = binding.etWarning.getText() != null ? binding.etWarning.getText().toString().trim() : "";
            String notes = binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : "";

            int summerInterval = 7;
            try {
                summerInterval = Integer.parseInt(binding.etIntervalSummer.getText().toString().trim());
            } catch (Exception ignored) {}

            int winterInterval = summerInterval;
            try {
                winterInterval = Integer.parseInt(binding.etIntervalWinter.getText().toString().trim());
            } catch (Exception ignored) {}

            int targetLux = 10000;
            try {
                targetLux = Integer.parseInt(binding.etTargetLux.getText().toString().trim());
            } catch (Exception ignored) {}

            String selectedCategoryId = null;
            int spinnerPos = binding.spinnerCategories.getSelectedItemPosition();
            if (spinnerPos > 0 && spinnerPos - 1 < categoryList.size()) {
                selectedCategoryId = categoryList.get(spinnerPos - 1).getId();
            }

            String plantId = UUID.randomUUID().toString().substring(0, 8);
            PlantEntity plant = new PlantEntity(plantId);
            plant.setName(name);
            plant.setVariety(variety);
            plant.setLatin(latin);
            plant.setSoil(soil);
            plant.setWarning(warning);
            plant.setNote(notes);
            plant.setCategoryId(selectedCategoryId);
            plant.setIntervalDays(summerInterval);
            plant.setIntervalDaysWinter(winterInterval);
            plant.setLux(targetLux);
            plant.setPrimaryPhotoPath(selectedPhotoPath);

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            plant.setLastWatered(today);
            plant.setCreatedAt(today);

            repository.insertPlant(plant);

            if (selectedPhotoPath != null) {
                String photoId = UUID.randomUUID().toString().substring(0, 8);
                PhotoEntity photo = new PhotoEntity(photoId, plantId, today, selectedPhotoPath);
                new Thread(() -> PlantshelfDatabase.getInstance(this).photoDao().insert(photo)).start();
            }

            Toast.makeText(this, "Рослину додано!", Toast.LENGTH_SHORT).show();
            finish();
        });
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
