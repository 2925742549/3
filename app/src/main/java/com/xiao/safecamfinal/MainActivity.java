package com.xiao.safecamfinal;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ = 44;

    private TextView status;
    private EditText pinInput;
    private EditText portInput;
    private CheckBox recordingCheck;
        private CheckBox wideCameraCheck;
    private EditText recordSecondsInput;
    private EditText retentionHoursInput;
    private EditText maxStorageInput;
    private CheckBox autoStartCheck;
    private CheckBox nightModeCheck;
    private CheckBox torchCheck;
    private CheckBox nightGrayCheck;
    private EditText nightBrightnessInput;
    private EditText nightContrastInput;

    private final int bg = Color.rgb(7, 13, 28);
    private final int card = Color.rgb(17, 24, 39);
    private final int text = Color.rgb(248, 250, 252);
    private final int muted = Color.rgb(148, 163, 184);
    private final int blue = Color.rgb(37, 99, 235);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        scroll.addView(root);

        root.addView(heroCard());

        pinInput = input("访问 PIN，至少 4 位", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        portInput = input("端口，例如 8080", InputType.TYPE_CLASS_NUMBER);

        wideCameraCheck = checkbox("优先使用最广角后置镜头（系统开放才生效）");
            recordingCheck = checkbox("开启循环记录 / 回放");
        recordSecondsInput = input("记录间隔秒数，稳定版最低 1 秒", InputType.TYPE_CLASS_NUMBER);
        retentionHoursInput = input("保留小时数，例如 72 / 168 / 720", InputType.TYPE_CLASS_NUMBER);
        maxStorageInput = input("最大占用空间 MB，例如 30000 / 80000", InputType.TYPE_CLASS_NUMBER);
        autoStartCheck = checkbox("开机后自动启动监控服务");

        nightModeCheck = checkbox("开启夜间低光增强");
        torchCheck = checkbox("远程打开手电筒补光");
        nightGrayCheck = checkbox("夜间黑白增强模式");
        nightBrightnessInput = input("夜间提亮强度 0-100", InputType.TYPE_CLASS_NUMBER);
        nightContrastInput = input("夜间对比增强 0-100", InputType.TYPE_CLASS_NUMBER);

        LinearLayout settingsCard = cardLayout();
        settingsCard.addView(sectionTitle("基础设置"));
        settingsCard.addView(label("PIN"));
        settingsCard.addView(pinInput);
        settingsCard.addView(label("端口"));
        settingsCard.addView(portInput);
        settingsCard.addView(wideCameraCheck);
            settingsCard.addView(recordingCheck);
        settingsCard.addView(label("循环记录间隔"));
        settingsCard.addView(recordSecondsInput);
        settingsCard.addView(label("保留时间"));
        settingsCard.addView(retentionHoursInput);
        settingsCard.addView(label("最大存储空间"));
        settingsCard.addView(maxStorageInput);
        settingsCard.addView(autoStartCheck);
        root.addView(settingsCard);

        LinearLayout nightCard = cardLayout();
        nightCard.addView(sectionTitle("夜间增强"));
        nightCard.addView(nightModeCheck);
        nightCard.addView(nightGrayCheck);
        nightCard.addView(torchCheck);
        nightCard.addView(label("提亮强度"));
        nightCard.addView(nightBrightnessInput);
        nightCard.addView(label("对比增强"));
        nightCard.addView(nightContrastInput);
        root.addView(nightCard);

        LinearLayout buttons = cardLayout();
        Button save = primaryButton("保存设置");
        Button start = primaryButton("START CAMERA SERVER");
        Button stop = ghostButton("STOP");
        Button battery = ghostButton("打开电池设置，关闭省电限制");

        buttons.addView(save);
        buttons.addView(start);
        buttons.addView(stop);
        buttons.addView(battery);
        root.addView(buttons);

        LinearLayout statusCard = cardLayout();
        statusCard.addView(sectionTitle("访问地址"));
        status = new TextView(this);
        status.setTextColor(muted);
        status.setTextSize(15);
        status.setLineSpacing(0, 1.15f);
        statusCard.addView(status);
        root.addView(statusCard);

        setContentView(scroll);
        loadSettingsToUi();
        updateStatus();

        save.setOnClickListener(v -> {
            saveSettings();
            updateStatus();
        });

        start.setOnClickListener(v -> {
            saveSettings();
            if (!hasPermissions()) {
                requestNeededPermissions();
                return;
            }
            startServiceNow();
        });

        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, CameraStreamService.class);
            i.setAction(CameraStreamService.ACTION_STOP);
            startService(i);
            status.setText("已发送停止指令。");
        });

        battery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    private LinearLayout heroCard() {
        LinearLayout h = cardLayout();
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(30, 64, 175), Color.rgb(8, 145, 178)}
        );
        g.setCornerRadius(dp(26));
        h.setBackground(g);

        TextView title = new TextView(this);
        title.setText("SafeCam Final");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(title);

        TextView sub = new TextView(this);
        sub.setText("实时监控 · 最广角优先 · 远程设置 · 循环回放");
        sub.setTextColor(Color.rgb(219, 234, 254));
        sub.setTextSize(15);
        sub.setPadding(0, dp(6), 0, 0);
        h.addView(sub);
        return h;
    }

    private LinearLayout cardLayout() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(card);
        gd.setCornerRadius(dp(22));
        gd.setStroke(dp(1), Color.rgb(31, 41, 55));
        l.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        l.setLayoutParams(lp);
        return l;
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(text);
        t.setTextSize(19);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(muted);
        t.setTextSize(14);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(100, 116, 139));
        e.setTextColor(text);
        e.setTextSize(16);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(2, 6, 23));
        gd.setCornerRadius(dp(14));
        gd.setStroke(dp(1), Color.rgb(51, 65, 85));
        e.setBackground(gd);
        return e;
    }

    private CheckBox checkbox(String s) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(text);
        c.setTextSize(15);
        c.setPadding(0, dp(8), 0, dp(4));
        return c;
    }

    private Button primaryButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{blue, Color.rgb(6, 182, 212)});
        gd.setCornerRadius(dp(16));
        b.setBackground(gd);
        b.setPadding(0, dp(10), 0, dp(10));
        return withButtonMargin(b);
    }

    private Button ghostButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(text);
        b.setTextSize(15);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(30, 41, 59));
        gd.setCornerRadius(dp(16));
        gd.setStroke(dp(1), Color.rgb(51, 65, 85));
        b.setBackground(gd);
        b.setPadding(0, dp(10), 0, dp(10));
        return withButtonMargin(b);
    }

    private Button withButtonMargin(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadSettingsToUi() {
        pinInput.setText(AppSettings.pin(this));
        portInput.setText(String.valueOf(AppSettings.port(this)));
        wideCameraCheck.setChecked(AppSettings.wideCamera(this));
            recordingCheck.setChecked(AppSettings.recordingEnabled(this));
        recordSecondsInput.setText(String.valueOf(AppSettings.recordSeconds(this)));
        retentionHoursInput.setText(String.valueOf(AppSettings.retentionHours(this)));
        maxStorageInput.setText(String.valueOf(AppSettings.maxStorageMb(this)));
        autoStartCheck.setChecked(AppSettings.autoStart(this));
        nightModeCheck.setChecked(AppSettings.nightMode(this));
        torchCheck.setChecked(AppSettings.torchEnabled(this));
        nightGrayCheck.setChecked(AppSettings.nightGray(this));
        nightBrightnessInput.setText(String.valueOf(AppSettings.nightBrightness(this)));
        nightContrastInput.setText(String.valueOf(AppSettings.nightContrast(this)));
    }

    private void saveSettings() {
        String pin = pinInput.getText().toString().trim();
        if (pin.length() < 4) pin = "123456";

        int port = parseInt(portInput.getText().toString(), 8080);
        int sec = parseInt(recordSecondsInput.getText().toString(), 1);
        int hours = parseInt(retentionHoursInput.getText().toString(), 72);
        int mb = parseInt(maxStorageInput.getText().toString(), 30000);
        int nightBrightness = parseInt(nightBrightnessInput.getText().toString(), 45);
        int nightContrast = parseInt(nightContrastInput.getText().toString(), 25);

        SharedPreferences.Editor e = AppSettings.prefs(this).edit();
        e.putString(AppSettings.KEY_PIN, pin);
        e.putInt(AppSettings.KEY_PORT, AppSettings.clamp(port, 1024, 65535));
        e.putBoolean(AppSettings.KEY_WIDE_CAMERA, wideCameraCheck.isChecked());
            e.putBoolean(AppSettings.KEY_RECORDING, recordingCheck.isChecked());
        e.putInt(AppSettings.KEY_RECORD_SECONDS, AppSettings.clamp(sec, 1, 60));
        e.putInt(AppSettings.KEY_RETENTION_HOURS, AppSettings.clamp(hours, 1, 24 * 30));
        e.putInt(AppSettings.KEY_MAX_STORAGE_MB, AppSettings.clamp(mb, 100, 102400));
        e.putBoolean(AppSettings.KEY_AUTO_START, autoStartCheck.isChecked());
        e.putBoolean(AppSettings.KEY_NIGHT_MODE, nightModeCheck.isChecked());
        e.putBoolean(AppSettings.KEY_TORCH, torchCheck.isChecked());
        e.putBoolean(AppSettings.KEY_NIGHT_GRAY, nightGrayCheck.isChecked());
        e.putInt(AppSettings.KEY_NIGHT_BRIGHTNESS, AppSettings.clamp(nightBrightness, 0, 100));
        e.putInt(AppSettings.KEY_NIGHT_CONTRAST, AppSettings.clamp(nightContrast, 0, 100));
        e.apply();
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private boolean hasPermissions() {
        boolean cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean notifyOk = true;
        if (Build.VERSION.SDK_INT >= 33) {
            notifyOk = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return cam && notifyOk;
    }

    private void requestNeededPermissions() {
        List<String> ps = new ArrayList<>();
        ps.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) ps.add(Manifest.permission.POST_NOTIFICATIONS);
        ActivityCompat.requestPermissions(this, ps.toArray(new String[0]), REQ);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ && hasPermissions()) {
            startServiceNow();
        } else {
            status.setText("需要允许摄像头权限和通知权限，否则无法长期运行。");
        }
    }

    private void startServiceNow() {
        Intent i = new Intent(this, CameraStreamService.class);
        i.setAction(CameraStreamService.ACTION_START);
        ContextCompat.startForegroundService(this, i);
        status.setText(buildStatusText());
    }

    private void updateStatus() {
        status.setText(buildStatusText());
    }

    private String buildStatusText() {
        int port = AppSettings.port(this);
        String pin = AppSettings.pin(this);
        List<String> ips = getLocalIpv4Addresses();

        StringBuilder sb = new StringBuilder();
        sb.append("实时查看：\n");
        if (ips.isEmpty()) {
            sb.append("请先连接 Wi-Fi / Tailscale / ZeroTier，再点击 START。\n");
        } else {
            for (String ip : ips) {
                sb.append("http://").append(ip).append(":").append(port).append("/?pin=").append(pin).append("\n");
            }
        }

        sb.append("\n远程设置：\n");
        if (!ips.isEmpty()) {
            for (String ip : ips) {
                sb.append("http://").append(ip).append(":").append(port).append("/settings?pin=").append(pin).append("\n");
            }
        }

        sb.append("\n回放地址：\n");
        if (!ips.isEmpty()) {
            for (String ip : ips) {
                sb.append("http://").append(ip).append(":").append(port).append("/recordings?pin=").append(pin).append("\n");
            }
        }

        sb.append("\n镜头模式：").append(AppSettings.wideCamera(this) ? "最广角优先" : "默认主摄");
            sb.append("\n循环记录：").append(AppSettings.recordingEnabled(this) ? "开启" : "关闭");
        sb.append(" ｜ 间隔：").append(AppSettings.recordSeconds(this)).append(" 秒");
        sb.append(" ｜ 保留：").append(AppSettings.retentionHours(this)).append(" 小时");
        sb.append(" ｜ 上限：").append(AppSettings.maxStorageMb(this)).append(" MB");
        sb.append("\n已用空间：").append(RecordingManager.humanSize(RecordingManager.totalBytes(this)));
        sb.append("\n夜间增强：").append(AppSettings.nightMode(this) ? "开启" : "关闭");
        sb.append(" ｜ 手电补光：").append(AppSettings.torchEnabled(this) ? "开启" : "关闭");
        sb.append(" ｜ 提亮：").append(AppSettings.nightBrightness(this));
        sb.append(" ｜ 对比：").append(AppSettings.nightContrast(this));
        sb.append("\n\n长期运行：插电、关闭省电限制、允许后台运行。不要直接公网映射端口。");
        return sb.toString();
    }

    private List<String> getLocalIpv4Addresses() {
        List<String> result = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return result;
            for (Network network : cm.getAllNetworks()) {
                LinkProperties lp = cm.getLinkProperties(network);
                if (lp == null) continue;
                for (LinkAddress addr : lp.getLinkAddresses()) {
                    if (addr.getAddress() instanceof Inet4Address && !addr.getAddress().isLoopbackAddress()) {
                        result.add(addr.getAddress().getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }
}
