package com.xiao.safecamfinal;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    private static final String PREF = "safe_cam_final_settings";

    static final String KEY_PIN = "pin";
    static final String KEY_PORT = "port";
    static final String KEY_RECORDING = "recording_enabled";
    static final String KEY_RECORD_SECONDS = "record_seconds";
    static final String KEY_RETENTION_HOURS = "retention_hours";
    static final String KEY_MAX_STORAGE_MB = "max_storage_mb";
    static final String KEY_AUTO_START = "auto_start";
        static final String KEY_WIDE_CAMERA = "wide_camera";

    static final String KEY_NIGHT_MODE = "night_mode";
    static final String KEY_TORCH = "torch_enabled";
    static final String KEY_NIGHT_BRIGHTNESS = "night_brightness";
    static final String KEY_NIGHT_CONTRAST = "night_contrast";
    static final String KEY_NIGHT_GRAY = "night_gray";

    private AppSettings() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static String pin(Context c) {
        String v = prefs(c).getString(KEY_PIN, "123456");
        if (v == null || v.trim().length() < 4) return "123456";
        return v.trim();
    }

    static int port(Context c) {
        return clamp(prefs(c).getInt(KEY_PORT, 8080), 1024, 65535);
    }

    static boolean recordingEnabled(Context c) {
        return prefs(c).getBoolean(KEY_RECORDING, true);
    }

    // 当前稳定版是图片循环记录，最低 1 秒一张。
    static int recordSeconds(Context c) {
        return clamp(prefs(c).getInt(KEY_RECORD_SECONDS, 1), 1, 60);
    }

    static int retentionHours(Context c) {
        return clamp(prefs(c).getInt(KEY_RETENTION_HOURS, 72), 1, 24 * 30);
    }

    // 默认 30GB，最高 100GB。
    static int maxStorageMb(Context c) {
        return clamp(prefs(c).getInt(KEY_MAX_STORAGE_MB, 30000), 100, 102400);
    }

    static boolean autoStart(Context c) {
        return prefs(c).getBoolean(KEY_AUTO_START, false);
    }

    static boolean nightMode(Context c) {
        return prefs(c).getBoolean(KEY_NIGHT_MODE, true);
    }

    static boolean torchEnabled(Context c) {
        return prefs(c).getBoolean(KEY_TORCH, false);
    }

    static int nightBrightness(Context c) {
        return clamp(prefs(c).getInt(KEY_NIGHT_BRIGHTNESS, 45), 0, 100);
    }

    static int nightContrast(Context c) {
        return clamp(prefs(c).getInt(KEY_NIGHT_CONTRAST, 25), 0, 100);
    }

    static boolean nightGray(Context c) {
        return prefs(c).getBoolean(KEY_NIGHT_GRAY, true);
    }

    static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
