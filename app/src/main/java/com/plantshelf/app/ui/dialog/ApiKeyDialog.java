package com.plantshelf.app.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.R;
import com.plantshelf.app.data.ai.GeminiPlantAiService;

import java.util.ArrayList;
import java.util.List;

public class ApiKeyDialog {

    public interface OnApiKeySavedListener {
        void onKeySaved(String newKey);
    }

    public static void show(Context context, OnApiKeySavedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_api_settings, null);
        EditText etApiKey = view.findViewById(R.id.etApiKey);
        AutoCompleteTextView actvModel = view.findViewById(R.id.actvModel);

        etApiKey.setText(GeminiPlantAiService.getSavedApiKey(context));
        String currentModel = GeminiPlantAiService.getSavedModel(context);

        List<GeminiPlantAiService.GeminiModelInfo> models = GeminiPlantAiService.AVAILABLE_MODELS;
        List<String> modelDisplayNames = new ArrayList<>();
        int selectedIndex = -1;

        for (int i = 0; i < models.size(); i++) {
            GeminiPlantAiService.GeminiModelInfo info = models.get(i);
            modelDisplayNames.add(info.toString());
            if (info.getModelId().equalsIgnoreCase(currentModel)) {
                selectedIndex = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, modelDisplayNames);
        actvModel.setAdapter(adapter);

        if (selectedIndex >= 0) {
            actvModel.setText(models.get(selectedIndex).getModelId(), false);
        } else {
            actvModel.setText(currentModel, false);
        }

        actvModel.setOnClickListener(v -> actvModel.showDropDown());
        actvModel.setOnItemClickListener((parent, v, position, id) -> {
            if (position >= 0 && position < models.size()) {
                actvModel.setText(models.get(position).getModelId(), false);
            }
        });

        View btnViewLogs = view.findViewById(R.id.btnViewLogs);
        if (btnViewLogs != null) {
            btnViewLogs.setOnClickListener(v -> com.plantshelf.app.data.ai.AiErrorLogger.showLogsDialog(context));
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_api_key_title)
                .setView(view)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String key = etApiKey.getText().toString().trim();
                    String rawModel = actvModel.getText().toString().trim();
                    String model = extractModelId(rawModel);

                    GeminiPlantAiService.saveAiSettings(context, key, model);
                    Toast.makeText(context, "Налаштування AI збережено! Модель: " + model, Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onKeySaved(key);
                    }
                })
                .setNeutralButton("Отримати ключ", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"));
                    context.startActivity(browserIntent);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    public static String extractModelId(String text) {
        if (text == null || text.trim().isEmpty()) {
            return GeminiPlantAiService.DEFAULT_MODEL;
        }
        String trimmed = text.trim();
        for (GeminiPlantAiService.GeminiModelInfo info : GeminiPlantAiService.AVAILABLE_MODELS) {
            if (info.getModelId().equalsIgnoreCase(trimmed) || info.toString().equalsIgnoreCase(trimmed)) {
                return info.getModelId();
            }
        }
        if (trimmed.contains("(") && trimmed.endsWith(")")) {
            int start = trimmed.lastIndexOf('(') + 1;
            int end = trimmed.lastIndexOf(')');
            if (start < end) {
                return trimmed.substring(start, end).trim();
            }
        }
        return trimmed;
    }
}
