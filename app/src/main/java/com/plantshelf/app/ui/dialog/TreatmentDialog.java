package com.plantshelf.app.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.plantshelf.app.R;

public class TreatmentDialog {

    public interface OnTreatmentRecordedListener {
        void onRecorded(String drug, String notes, boolean putOnQuarantine);
    }

    public static void show(Context context, OnTreatmentRecordedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_treatment, null);
        EditText etDrug = view.findViewById(R.id.etDrug);
        EditText etNotes = view.findViewById(R.id.etNotes);
        CheckBox cbQuarantine = view.findViewById(R.id.cbQuarantine);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_treatment_title)
                .setView(view)
                .setPositiveButton(R.string.action_treatment, (dialog, which) -> {
                    String drug = etDrug.getText().toString().trim();
                    String notes = etNotes.getText().toString().trim();
                    boolean quarantine = cbQuarantine.isChecked();
                    if (drug.isEmpty()) {
                        drug = "Лікувальна обробка";
                    }
                    if (listener != null) {
                        listener.onRecorded(drug, notes, quarantine);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
