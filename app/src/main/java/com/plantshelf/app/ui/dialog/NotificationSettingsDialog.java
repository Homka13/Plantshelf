package com.plantshelf.app.ui.dialog;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.plantshelf.app.R;
import com.plantshelf.app.notifications.NotificationHelper;
import com.plantshelf.app.notifications.NotificationPrefs;
import com.plantshelf.app.notifications.NotificationScheduler;

import java.util.Locale;

public class NotificationSettingsDialog {

    public interface OnNotificationSettingsSavedListener {
        void onSettingsSaved(boolean enabled, int hour, int minute);
        void onRequestPermission();
    }

    public static void show(Context context, OnNotificationSettingsSavedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_notification_settings, null);

        MaterialSwitch switchNotifications = view.findViewById(R.id.switchNotifications);
        LinearLayout layoutTimeContainer = view.findViewById(R.id.layoutTimeContainer);
        MaterialButton btnPickTime = view.findViewById(R.id.btnPickTime);
        MaterialButton btnTestNotification = view.findViewById(R.id.btnTestNotification);

        boolean initialEnabled = NotificationPrefs.isNotificationsEnabled(context);
        int[] selectedTime = new int[]{
                NotificationPrefs.getReminderHour(context),
                NotificationPrefs.getReminderMinute(context)
        };

        switchNotifications.setChecked(initialEnabled);
        layoutTimeContainer.setVisibility(initialEnabled ? View.VISIBLE : View.GONE);
        btnPickTime.setText(String.format(Locale.getDefault(), "⏰ %02d:%02d", selectedTime[0], selectedTime[1]));

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutTimeContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            // Check Android 13+ permission
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (listener != null) {
                        listener.onRequestPermission();
                    }
                }
            }
        });

        btnPickTime.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(
                    context,
                    (timePicker, hourOfDay, minute) -> {
                        selectedTime[0] = hourOfDay;
                        selectedTime[1] = minute;
                        btnPickTime.setText(String.format(Locale.getDefault(), "⏰ %02d:%02d", hourOfDay, minute));
                    },
                    selectedTime[0],
                    selectedTime[1],
                    true
            );
            timePickerDialog.setTitle("Оберіть час нагадування");
            timePickerDialog.show();
        });

        btnTestNotification.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Потрібен дозвіл для показу сповіщень", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onRequestPermission();
                    }
                    return;
                }
            }

            NotificationHelper.sendCareReminderNotification(
                    context,
                    context.getString(R.string.notif_test_title),
                    context.getString(R.string.notif_test_body)
            );
            Toast.makeText(context, R.string.notif_setting_test_sent, Toast.LENGTH_SHORT).show();
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_notif_settings_title)
                .setIcon(R.drawable.ic_notifications)
                .setView(view)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    boolean enabled = switchNotifications.isChecked();
                    NotificationPrefs.setNotificationsEnabled(context, enabled);
                    NotificationPrefs.setReminderTime(context, selectedTime[0], selectedTime[1]);

                    NotificationScheduler.scheduleCareReminder(context);

                    if (listener != null) {
                        listener.onSettingsSaved(enabled, selectedTime[0], selectedTime[1]);
                    }

                    if (enabled) {
                        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", selectedTime[0], selectedTime[1]);
                        Toast.makeText(context, context.getString(R.string.notif_saved_snackbar, timeStr), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
