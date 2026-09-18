package com.plantshelf.app.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.R;
import com.plantshelf.app.data.ai.GeminiPlantAiService;

public class ApiKeyDialog {

    public interface OnApiKeySavedListener {
        void onKeySaved(String newKey);
    }

    public static void show(Context context, OnApiKeySavedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_api_settings, null);
        EditText etApiKey = view.findViewById(R.id.etApiKey);
        EditText etModel = view.findViewById(R.id.etModel);

        etApiKey.setText(GeminiPlantAiService.getSavedApiKey(context));
        etModel.setText(GeminiPlantAiService.getSavedModel(context));

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_api_key_title)
                .setView(view)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String key = etApiKey.getText().toString().trim();
                    String model = etModel.getText().toString().trim();
                    if (model.isEmpty()) {
                        model = GeminiPlantAiService.DEFAULT_MODEL;
                    }
                    GeminiPlantAiService.saveAiSettings(context, key, model);
                    Toast.makeText(context, "Налаштування AI збережено!", Toast.LENGTH_SHORT).show();
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
}
