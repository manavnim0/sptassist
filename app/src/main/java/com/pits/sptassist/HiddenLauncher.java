package com.pits.sptassist;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class HiddenLauncher extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Method 1: Trigger secret code programmatically
        sendBroadcast(new Intent("android.provider.Telephony.SECRET_CODE")
                .setData(Uri.parse("android_secret_code://5683")));

        // Method 2: Directly launch MainActivity as fallback
        try {
            startActivity(new Intent(this, MainActivity.class));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Optional: Self-disable after first launch
        disableLauncherIcon();

        finish(); // Close immediately
    }

    private void disableLauncherIcon() {
        PackageManager pm = getPackageManager();
        ComponentName component = new ComponentName(this, HiddenLauncher.class);
        pm.setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}