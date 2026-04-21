package com.bluestacks.fpsoverlay;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    public static View overlayView;
    public static double min_fps = 60.0d;
    public static double max_fps = 75.0d;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_main);
        initializeLogic();
    }

    private boolean isAccessibilityServiceEnabled() {
        String prefString = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return prefString != null && prefString.contains(getPackageName() + "/" + SupportService.class.getName());
    }

    private void showAccessibilityDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Izin Aksesibilitas Diperlukan")
                .setMessage("Izin aksesibilitas di perlukan untuk menampilkan overlay di project ini, kami memakai type overlay aksesibilitas untuk membuat text di dalam nya bisa stretch sehingga lebih mirip dengan bluestacks, dan memastikan overlay tetap berjalan lancar.")
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void initializeLogic() {
        final SharedPreferences sharedPreferences = getSharedPreferences("status_fps", 0);
        final SwitchCompat swShow = findViewById(R.id.sw_show);
        final EditText etMin = findViewById(R.id.et_min);
        final EditText etMax = findViewById(R.id.et_max);
        final MaterialButton btnApply = findViewById(R.id.btn_apply);

        String string = sharedPreferences.getString("min", "97");
        String string2 = sharedPreferences.getString("max", "114");
        etMin.setText(string);
        etMax.setText(string2);
        min_fps = Double.parseDouble(string);
        max_fps = Double.parseDouble(string2);

        boolean isRunning = sharedPreferences.getBoolean("is_running", false);
        swShow.setChecked(isRunning);
        if (isRunning && isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(this, SupportService.class);
            intent.setAction("ACTION_START_FPS");
            startService(intent);
        }

        swShow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && !isAccessibilityServiceEnabled()) {
                    swShow.setChecked(false);
                    showAccessibilityDialog();
                    return;
                }
                sharedPreferences.edit().putBoolean("is_running", isChecked).apply();
                Intent intent = new Intent(MainActivity.this, SupportService.class);
                if (isChecked) {
                    intent.setAction("ACTION_START_FPS");
                } else {
                    intent.setAction("ACTION_STOP_FPS");
                }
                startService(intent);
            }
        });

        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String sMin = etMin.getText().toString();
                String sMax = etMax.getText().toString();
                if (!sMin.isEmpty() && !sMax.isEmpty()) {
                    sharedPreferences.edit().putString("min", sMin).putString("max", sMax).apply();
                    min_fps = Double.parseDouble(sMin);
                    max_fps = Double.parseDouble(sMax);
                    if (swShow.isChecked() && isAccessibilityServiceEnabled()) {
                        Intent intent = new Intent(MainActivity.this, SupportService.class);
                        intent.setAction("ACTION_START_FPS");
                        startService(intent);
                    }
                }
            }
        });
    }


}
