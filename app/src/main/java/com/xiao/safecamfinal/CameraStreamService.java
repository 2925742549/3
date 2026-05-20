package com.xiao.safecamfinal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;
import android.os.PowerManager;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraFilter;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.google.common.util.concurrent.ListenableFuture;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraStreamService extends LifecycleService {
    public static final String ACTION_START = "com.xiao.safecamfinal.START";
    public static final String ACTION_STOP = "com.xiao.safecamfinal.STOP";

    private static final String CHANNEL_ID = "safe_cam_final_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ExecutorService cameraExecutor;
    private LocalHttpServer server;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private PowerManager.WakeLock wakeLock;
    private Boolean lastTorchState = null;

    private long lastLiveFrameMs = 0L;
    private long lastRecordMs = 0L;
    private long lastCleanupMs = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        startAsForeground();
        acquireWakeLock();
        startServer(AppSettings.port(this), AppSettings.pin(this));
        startCamera();

        return Service.START_STICKY;
    }

    private void startAsForeground() {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("SafeCam Wide Final is running")
                .setContentText("Visible camera stream and loop recording are active.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "SafeCam Wide Final running status",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeCamWideFinal:StreamLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(12 * 60 * 60 * 1000L);
            }
        } catch (Exception ignored) {}
    }

    private void startServer(int port, String pin) {
        if (server != null) return;
        server = new LocalHttpServer(this, port, pin);
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            stopSelf();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this,
                        buildCameraSelector(cameraProvider),
                        analysis
                );

                applyTorchSetting();
            } catch (Exception e) {
                stopSelf();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private CameraSelector buildCameraSelector(ProcessCameraProvider provider) {
        if (!AppSettings.wideCamera(this)) {
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }

        try {
            List<CameraInfo> infos = provider.getAvailableCameraInfos();
            String selectedCameraId = null;
            float bestFocal = Float.MAX_VALUE;

            for (CameraInfo info : infos) {
                Camera2CameraInfo c2 = Camera2CameraInfo.from(info);

                Integer facing = c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                float[] focalLengths = c2.getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                );
                if (focalLengths == null || focalLengths.length == 0) {
                    continue;
                }

                float minFocal = Float.MAX_VALUE;
                for (float f : focalLengths) {
                    if (f > 0 && f < minFocal) {
                        minFocal = f;
                    }
                }

                if (minFocal < bestFocal) {
                    bestFocal = minFocal;
                    selectedCameraId = c2.getCameraId();
                }
            }

            if (selectedCameraId == null) {
                return CameraSelector.DEFAULT_BACK_CAMERA;
            }

            final String targetId = selectedCameraId;
            return new CameraSelector.Builder()
                    .addCameraFilter(new CameraFilter() {
                        @Override
                        public List<CameraInfo> filter(List<CameraInfo> cameraInfos) {
                            ArrayList<CameraInfo> result = new ArrayList<>();
                            for (CameraInfo info : cameraInfos) {
                                try {
                                    String id = Camera2CameraInfo.from(info).getCameraId();
                                    if (targetId.equals(id)) {
                                        result.add(info);
                                    }
                                } catch (Exception ignored) {}
                            }
                            return result.isEmpty() ? cameraInfos : result;
                        }
                    })
                    .build();
        } catch (Exception e) {
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }
    }

    private void analyzeFrame(ImageProxy image) {
        try {
            applyTorchSetting();

            long now = System.currentTimeMillis();
            boolean needLive = now - lastLiveFrameMs >= 500;
            boolean needRecord = AppSettings.recordingEnabled(this)
                    && now - lastRecordMs >= AppSettings.recordSeconds(this) * 1000L;

            if (needLive || needRecord) {
                byte[] jpeg = YuvToJpeg.convert(image, 68);

                if (AppSettings.nightMode(this)) {
                    jpeg = ImageEnhancer.enhanceLowLight(
                            jpeg,
                            AppSettings.nightBrightness(this),
                            AppSettings.nightContrast(this),
                            AppSettings.nightGray(this)
                    );
                }

                if (needLive) {
                    lastLiveFrameMs = now;
                    FrameStore.latestJpeg.set(jpeg);
                }
                if (needRecord) {
                    lastRecordMs = now;
                    RecordingManager.saveFrame(this, jpeg);
                }
            }

            if (now - lastCleanupMs >= 5 * 60 * 1000L) {
                lastCleanupMs = now;
                RecordingManager.cleanup(this);
            }
        } catch (Exception ignored) {
        } finally {
            image.close();
        }
    }

    private void applyTorchSetting() {
        try {
            if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
            boolean want = AppSettings.torchEnabled(this);
            if (lastTorchState == null || lastTorchState != want) {
                lastTorchState = want;
                camera.getCameraControl().enableTorch(want);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        try {
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                camera.getCameraControl().enableTorch(false);
            }
        } catch (Exception ignored) {}

        if (cameraProvider != null) cameraProvider.unbindAll();
        if (server != null) {
            server.stop();
            server = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
        FrameStore.latestJpeg.set(null);
        super.onDestroy();
    }
}
