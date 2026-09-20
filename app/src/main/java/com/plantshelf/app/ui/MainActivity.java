package com.plantshelf.app.ui;

import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.plantshelf.app.R;
import com.plantshelf.app.databinding.ActivityMainBinding;
import com.plantshelf.app.notifications.NotificationPrefs;
import com.plantshelf.app.notifications.NotificationScheduler;

/**
 * Root Host Activity for Single-Activity Architecture.
 *
 * <p>Rationale (MIT Comm Lab Style - "Why over What"):
 * Following modern Android Architecture recommendations, MainActivity serves as a lightweight
 * navigation host container (via {@link NavHostFragment}). Individual screens are encapsulated
 * in feature Fragments, ensuring fast rendering, predictable lifecycle management, and
 * effortless deep-linking.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;

    private final androidx.activity.result.ActivityResultLauncher<String> postNotificationsLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (Boolean.TRUE.equals(isGranted)) {
                    NotificationPrefs.setNotificationsEnabled(this, true);
                    NotificationScheduler.scheduleCareReminder(this);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        if (savedInstanceState == null) {
            checkNotificationPermissionOnLaunch();
        }
    }

    private void checkNotificationPermissionOnLaunch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationPrefs.isNotificationsEnabled(this)) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    postNotificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null && navController.navigateUp()) {
            return true;
        }
        return super.onSupportNavigateUp();
    }
}
