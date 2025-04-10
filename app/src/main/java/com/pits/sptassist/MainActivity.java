package com.pits.sptassist;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_ALL_FILES_ACCESS  = 100;
    private static final int REQ_POST_NOTIF        = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        /* ---------- start foreground service ---------- */
        Intent svc = new Intent(this, ShellService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        /* ---------- runtime permissions ---------- */
        requestAllFilesAccess();
        requestPostNotificationPermission();
        requestBatteryOptimizationExemption();

        /* ---------- edge‑to‑edge padding ---------- */
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });
    }

    /* ──────────────────────────────────────────────── */
    /*   “All files access” (Android 11 / API 30 +)    */
    /* ──────────────────────────────────────────────── */
    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()) {

            Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);  //user must press "Allow" on next screen
        }
    }

    /* ──────────────────────────────────────────────── */
    /*   POST_NOTIFICATIONS (Android 13 / API 33 +)    */
    /* ──────────────────────────────────────────────── */
    private void requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_POST_NOTIF
            );
        }
    }

    /* ──────────────────────────────────────────────── */
    /*   Ignore battery optimisation prompt            */
    /* ──────────────────────────────────────────────── */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        }
    }

    /* ──────────────────────────────────────────────── */
    /*   Permission callback (optional logging)        */
    /* ──────────────────────────────────────────────── */
    @Override
    public void onRequestPermissionsResult(int reqCode,
                                           @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(reqCode, perms, results);
        // You can add UI feedback here if needed
    }
}