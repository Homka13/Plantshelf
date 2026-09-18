package com.plantshelf.app.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.R;
import com.plantshelf.app.data.ai.GeminiPlantAiService;

public class ApiKeyDialog {

    public interface OnApiKeySavedListener {
        void onKeySaved(String newKey);
    }

    public static void show(Context context, OnApiKeySavedListener listener) {
        EditText input = new EditText(context);
        input.setHint(R.string.dialog_api_key_hint);
        input.setText(GeminiPlantAiService.getSavedApiKey(context));
        input.setPadding(48, 24, 48, 24);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_api_key_title)
                .setMessage(R.string.dialog_api_key_desc)
                .setView(input)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String key = input.getText().toString().trim();
                    GeminiPlantAiService.saveApiKey(context, key);
                    Toast.makeText(context, "Ключ збережено!", Toast.LENGTH_SHORT).show();
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
