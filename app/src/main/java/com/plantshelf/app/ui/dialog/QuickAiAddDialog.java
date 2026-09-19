package com.plantshelf.app.ui.dialog;

import android.app.ProgressDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.R;
import com.plantshelf.app.data.ai.AiPlantAnalysisResult;
import com.plantshelf.app.data.ai.GeminiPlantAiService;
import com.plantshelf.app.data.database.PlantshelfDatabase;
import com.plantshelf.app.data.entity.CategoryEntity;
import com.plantshelf.app.data.entity.PlantEntity;
import com.plantshelf.app.widget.PlantCareWidgetProvider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class QuickAiAddDialog {

    public interface OnPlantAddedCallback {
        void onPlantAdded();
    }

    public static void show(Context context, String initialQuery, OnPlantAddedCallback callback) {
        String apiKey = GeminiPlantAiService.getSavedApiKey(context);
        if (apiKey.isEmpty()) {
            ApiKeyDialog.show(context, key -> show(context, initialQuery, callback));
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_quick_ai_add, null);
        EditText etName = dialogView.findViewById(R.id.etAiPlantName);
        Spinner spinner = dialogView.findViewById(R.id.spinnerAiCategory);

        if (initialQuery != null && !initialQuery.isEmpty()) {
            etName.setText(initialQuery);
        }

        List<CategoryEntity> categories = new ArrayList<>();
        List<String> catNames = new ArrayList<>();
        catNames.add("Без категорії");

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, catNames);
        spinner.setAdapter(spinnerAdapter);

        new Thread(() -> {
            List<CategoryEntity> dbCats = PlantshelfDatabase.getInstance(context).categoryDao().getAllCategoriesSync();
            if (dbCats != null && !dbCats.isEmpty()) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        categories.addAll(dbCats);
                        for (CategoryEntity c : dbCats) {
                            catNames.add(c.getName());
                        }
                        spinnerAdapter.notifyDataSetChanged();
                    });
                }
            }
        }).start();

        new MaterialAlertDialogBuilder(context)
                .setTitle("🤖 Швидке додавання з Gemini")
                .setMessage("Введіть назву або сорт рослини. Gemini сам визначить ботанічні характеристики, складе графік поливу та збереже її.")
                .setView(dialogView)
                .setPositiveButton("Додати", (dialog, which) -> {
                    String query = etName.getText().toString().trim();
                    if (query.isEmpty()) {
                        Toast.makeText(context, "Введіть назву рослини", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int pos = spinner.getSelectedItemPosition();
                    String selectedCatId = (pos > 0 && pos - 1 < categories.size()) ? categories.get(pos - 1).getId() : null;

                    executeAiPlantCreation(context, query, selectedCatId, callback);
                })
                .setNegativeButton("Скасувати", null)
                .show();
    }

    private static void executeAiPlantCreation(
            Context context,
            String plantName,
            String categoryId,
            OnPlantAddedCallback callback
    ) {
        Toast.makeText(context, "Gemini аналізує рослину та складає розклад...", Toast.LENGTH_LONG).show();

        GeminiPlantAiService.generatePlantByName(context, plantName, new GeminiPlantAiService.AiAnalysisCallback() {
            @Override
            public void onSuccess(AiPlantAnalysisResult result) {
                new Thread(() -> {
                    String plantId = UUID.randomUUID().toString().substring(0, 8);
                    PlantEntity plant = new PlantEntity(plantId);

                    plant.setName(result.getName().isEmpty() ? plantName : result.getName());
                    plant.setVariety(result.getVariety());
                    plant.setLatin(result.getLatin());
                    plant.setDifficulty(result.getDifficulty());
                    plant.setLight(result.getLight());
                    plant.setLux(result.getLux());
                    plant.setIntervalDays(result.getIntervalDaysSummer());
                    plant.setIntervalDaysWinter(result.getIntervalDaysWinter());
                    plant.setFertIntervalDays(result.getFertIntervalDays());
                    plant.setHumidity(result.getHumidity());
                    plant.setSoil(result.getSoil());
                    plant.setWarning(result.getWarning());
                    plant.setNote(result.getNotes());
                    plant.setCategoryId(categoryId);

                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    plant.setLastWatered(today);
                    plant.setCreatedAt(today);

                    PlantshelfDatabase.getInstance(context).plantDao().insert(plant);
                    PlantCareWidgetProvider.sendUpdateBroadcast(context);

                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context, "🌱 " + plant.getName() + " успішно додано через AI!", Toast.LENGTH_LONG).show();
                            if (callback != null) callback.onPlantAdded();
                        });
                    }
                }).start();
            }

            @Override
            public void onError(Exception e) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Помилка AI: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }
}
