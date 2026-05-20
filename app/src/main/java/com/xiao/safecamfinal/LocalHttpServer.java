package com.xiao.safecamfinal;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

final class LocalHttpServer extends NanoHTTPD {
    private final Context appContext;
    private final String serverPin;
    private final SimpleDateFormat displayFmt =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    LocalHttpServer(Context context, int port, String pin) {
        super(port);
        this.appContext = context.getApplicationContext();
        this.serverPin = pin;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        String currentPin = AppSettings.pin(appContext);
        if (!currentPin.equals(params.get("pin")) && !serverPin.equals(params.get("pin"))) {
            return html(Response.Status.UNAUTHORIZED,
                    "<h2>401 PIN required</h2><p>请在地址后加：<code>?pin=你的PIN</code></p>");
        }

        if ("/snapshot.jpg".equals(uri)) return snapshot();
        if ("/recordings".equals(uri)) return recordingsPage();
        if ("/api/recordings".equals(uri)) return recordingsJson();
        if (uri != null && uri.startsWith("/recording/")) return recordingFile(uri);
        if ("/settings".equals(uri)) return settingsPage(params);
        if ("/guide".equals(uri)) return guidePage();

        return livePage();
    }

    private Response snapshot() {
        byte[] jpeg = FrameStore.latestJpeg.get();
        if (jpeg == null) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "Camera is starting...");
        }
        Response res = newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                new ByteArrayInputStream(jpeg),
                jpeg.length
        );
        res.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        return res;
    }

    private Response recordingFile(String uri) {
        try {
            String name = URLDecoder.decode(uri.substring("/recording/".length()), "UTF-8");
            if (name.contains("/") || name.contains("\\") || !name.endsWith(".jpg")) {
                return html(Response.Status.BAD_REQUEST, "<h2>Bad file name</h2>");
            }
            File f = new File(RecordingManager.dir(appContext), name);
            if (!f.exists()) return html(Response.Status.NOT_FOUND, "<h2>Not found</h2>");
            Response res = newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    new FileInputStream(f),
                    f.length()
            );
            res.addHeader("Cache-Control", "no-store");
            return res;
        } catch (Exception e) {
            return html(Response.Status.INTERNAL_ERROR, "<h2>Error reading file</h2>");
        }
    }

    private Response livePage() {
        String p = esc(AppSettings.pin(appContext));
        String recordingText = AppSettings.recordingEnabled(appContext) ? "ON" : "OFF";
        String nightText = AppSettings.nightMode(appContext) ? "夜间增强 ON" : "夜间增强 OFF";
        String torchText = AppSettings.torchEnabled(appContext) ? "手电补光 ON" : "手电补光 OFF";

        String html = pageStart("SafeCam Wide Final", p)
                + "<main class='wrap'>"
                + "<section class='hero'><div><h1>Live Monitor</h1><p>实时查看 · 远程设置 · 循环回放</p></div><a class='pill' href='/settings?pin=" + p + "'>设置</a></section>"
                + "<section class='card'><img id='cam' alt='camera frame' src='/snapshot.jpg?pin=" + p + "&t=0'></section>"
                + "<section class='grid'>"
                + stat("镜头", AppSettings.wideCamera(appContext) ? "最广角优先" : "默认主摄")
                    + stat("循环记录", recordingText)
                + stat("间隔", AppSettings.recordSeconds(appContext) + " 秒")
                + stat("保留", AppSettings.retentionHours(appContext) + " 小时")
                + stat("空间", RecordingManager.humanSize(RecordingManager.totalBytes(appContext)))
                + stat("夜间", nightText)
                + stat("补光", torchText)
                + "</section>"
                + "<section class='actions'><button onclick='toggle()'>暂停 / 继续</button><a class='btn' href='/recordings?pin=" + p + "'>查看回放</a><a class='btn ghost' href='/guide?pin=" + p + "'>外出观看</a></section>"
                + "</main>"
                + "<script>let on=true;function loop(){if(on){document.getElementById('cam').src='/snapshot.jpg?pin=" + p + "&t='+Date.now();}setTimeout(loop,500);}loop();function toggle(){on=!on;}</script>"
                + "</body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private Response settingsPage(Map<String, String> params) {
        String saved = "";
        if ("1".equals(params.get("save"))) {
            saveRemoteSettings(params);
            saved = "<div class='ok'>已保存。除 PIN / 端口外，大部分参数会在运行中直接生效；端口修改后需要在摄像头手机上 STOP 再 START。</div>";
        }

        String p = esc(AppSettings.pin(appContext));
        String html = pageStart("Remote Settings", p)
                + "<main class='wrap'><section class='hero'><div><h1>远程设置</h1><p>摄像头手机固定后，在这里调参数</p></div><a class='pill' href='/?pin=" + p + "'>返回实时</a></section>"
                + "<section class='card'>"
                + saved
                + "<form method='get' action='/settings'>"
                + "<input type='hidden' name='save' value='1'>"
                + field("当前 PIN / 新 PIN", "pin", "text", p)
                + field("端口，默认 8080，改后需重启服务", "port", "number", String.valueOf(AppSettings.port(appContext)))
                + checkbox("wideCamera", "优先使用最广角后置镜头（系统开放才生效，保存后需重启服务）", AppSettings.wideCamera(appContext))
                    + checkbox("recording", "开启循环记录 / 回放", AppSettings.recordingEnabled(appContext))
                + field("记录间隔秒数：稳定版最低 1 秒一张", "recordSeconds", "number", String.valueOf(AppSettings.recordSeconds(appContext)))
                + field("保留小时数，例如 24 / 72 / 168 / 720", "retentionHours", "number", String.valueOf(AppSettings.retentionHours(appContext)))
                + field("最大占用空间 MB，例如 30000≈30GB，80000≈80GB", "maxStorageMb", "number", String.valueOf(AppSettings.maxStorageMb(appContext)))
                + checkbox("night", "开启夜间低光增强", AppSettings.nightMode(appContext))
                + checkbox("gray", "夜间黑白增强模式", AppSettings.nightGray(appContext))
                + checkbox("torch", "打开手机手电筒补光", AppSettings.torchEnabled(appContext))
                + field("夜间提亮强度 0-100", "brightness", "number", String.valueOf(AppSettings.nightBrightness(appContext)))
                + field("夜间对比增强 0-100", "contrast", "number", String.valueOf(AppSettings.nightContrast(appContext)))
                + checkbox("autoStart", "开机后自动启动监控服务", AppSettings.autoStart(appContext))
                + "<button type='submit'>保存远程设置</button>"
                + "</form>"
                + "<div class='note'><b>说明：</b>当前版会优先尝试系统开放的最广角后置镜头；如果视野没变化，说明系统只开放了默认主摄。当前稳定版是图片循环记录，不是 24fps MP4 录像。</div>"
                + "</section></main></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private void saveRemoteSettings(Map<String, String> p) {
        SharedPreferences.Editor e = AppSettings.prefs(appContext).edit();
        String newPin = safeString(p.get("pin"), AppSettings.pin(appContext));
        if (newPin.length() < 4) newPin = AppSettings.pin(appContext);

        e.putString(AppSettings.KEY_PIN, newPin);
        e.putInt(AppSettings.KEY_PORT, AppSettings.clamp(parseInt(p.get("port"), AppSettings.port(appContext)), 1024, 65535));
        e.putBoolean(AppSettings.KEY_WIDE_CAMERA, "on".equals(p.get("wideCamera")));
            e.putBoolean(AppSettings.KEY_RECORDING, "on".equals(p.get("recording")));
        e.putInt(AppSettings.KEY_RECORD_SECONDS, AppSettings.clamp(parseInt(p.get("recordSeconds"), 1), 1, 60));
        e.putInt(AppSettings.KEY_RETENTION_HOURS, AppSettings.clamp(parseInt(p.get("retentionHours"), 72), 1, 24 * 30));
        e.putInt(AppSettings.KEY_MAX_STORAGE_MB, AppSettings.clamp(parseInt(p.get("maxStorageMb"), 30000), 100, 102400));
        e.putBoolean(AppSettings.KEY_NIGHT_MODE, "on".equals(p.get("night")));
        e.putBoolean(AppSettings.KEY_NIGHT_GRAY, "on".equals(p.get("gray")));
        e.putBoolean(AppSettings.KEY_TORCH, "on".equals(p.get("torch")));
        e.putInt(AppSettings.KEY_NIGHT_BRIGHTNESS, AppSettings.clamp(parseInt(p.get("brightness"), 45), 0, 100));
        e.putInt(AppSettings.KEY_NIGHT_CONTRAST, AppSettings.clamp(parseInt(p.get("contrast"), 25), 0, 100));
        e.putBoolean(AppSettings.KEY_AUTO_START, "on".equals(p.get("autoStart")));
        e.apply();
    }

    private Response recordingsPage() {
        String p = esc(AppSettings.pin(appContext));
        String html = pageStart("Replay", p)
                + "<main class='wrap'><section class='hero'><div><h1>回放记录</h1><p>循环抓拍记录，自动清理旧文件</p></div><a class='pill' href='/?pin=" + p + "'>返回实时</a></section>"
                + "<section class='card'><img id='play' alt='replay frame'><div class='actions'><button onclick='playAuto()'>自动播放</button><button onclick='stopAuto()'>停止</button><a class='btn ghost' href='/settings?pin=" + p + "'>设置</a></div><div id='list' class='list'>加载中...</div></section>"
                + "</main>"
                + "<script>"
                + "let items=[],idx=0,timer=null;const pin='" + p + "';"
                + "fetch('/api/recordings?pin='+pin+'&t='+Date.now()).then(r=>r.json()).then(d=>{items=d.items||[];render();if(items.length){show(items[items.length-1].name);}});"
                + "function render(){let el=document.getElementById('list');if(!items.length){el.innerHTML='<p>暂无回放记录。请确认开启了循环记录，并运行几分钟。</p>';return;}el.innerHTML=items.slice().reverse().map((x)=>'<button class=\"row\" onclick=\"show(\\''+x.name+'\\')\">'+x.time+' ｜ '+x.size+'</button>').join('');}"
                + "function show(n){document.getElementById('play').src='/recording/'+encodeURIComponent(n)+'?pin='+pin+'&t='+Date.now();idx=items.findIndex(x=>x.name===n);}"
                + "function playAuto(){if(!items.length)return;stopAuto();timer=setInterval(()=>{show(items[idx].name);idx=(idx+1)%items.length;},700);}"
                + "function stopAuto(){if(timer){clearInterval(timer);timer=null;}}"
                + "</script></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private Response recordingsJson() {
        File[] files = RecordingManager.list(appContext);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"items\":[");
        boolean first = true;
        for (File f : files) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(json(f.getName())).append("\",");
            sb.append("\"time\":\"").append(json(displayFmt.format(new Date(f.lastModified())))).append("\",");
            sb.append("\"size\":\"").append(json(RecordingManager.humanSize(f.length()))).append("\"}");
        }
        sb.append("]}");
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", sb.toString());
    }

    private Response guidePage() {
        String p = esc(AppSettings.pin(appContext));
        String html = pageStart("Remote View", p)
                + "<main class='wrap'><section class='hero'><div><h1>外出实时观看</h1><p>推荐使用虚拟局域网，不要公网裸奔</p></div><a class='pill' href='/?pin=" + p + "'>返回实时</a></section>"
                + "<section class='card'><h2>Tailscale / ZeroTier</h2><ol><li>旧手机和查看手机都安装 Tailscale 或 ZeroTier。</li><li>加入同一个账号或虚拟网络。</li><li>旧手机 App 如果显示 100.x.x.x 地址，外出手机浏览器打开它。</li><li>示例：<code>http://100.x.x.x:8080/?pin=123456</code></li></ol><p>摄像头不够广角时，优先把手机后移或加外接广角夹镜。本广角版会尝试使用系统开放的最短焦距后置镜头；如果 ColorOS 不开放超广角，就会自动回到默认主摄。</p></section>"
                + "</main></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
    }

    private String pageStart(String title, String p) {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + esc(title) + "</title>" + css() + "</head><body>"
                + "<header><div class='brand'>● SafeCam Wide Final</div><nav>"
                + "<a href='/?pin=" + p + "'>实时</a>"
                + "<a href='/recordings?pin=" + p + "'>回放</a>"
                + "<a href='/settings?pin=" + p + "'>设置</a>"
                + "<a href='/guide?pin=" + p + "'>外网</a>"
                + "</nav></header>";
    }

    private Response html(Response.Status status, String body) {
        return newFixedLengthResponse(status, "text/html; charset=utf-8",
                "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" + css() + "</head><body><main class='wrap'><section class='card'>" + body + "</section></main></body></html>");
    }

    private static String stat(String k, String v) {
        return "<div class='stat'><span>" + esc(k) + "</span><b>" + esc(v) + "</b></div>";
    }

    private static String field(String label, String name, String type, String value) {
        return "<label>" + esc(label) + "</label><input name='" + name + "' type='" + type + "' value='" + esc(value) + "'>";
    }

    private static String checkbox(String name, String label, boolean checked) {
        return "<label class='check'><input type='checkbox' name='" + name + "'" + (checked ? " checked" : "") + "> " + esc(label) + "</label>";
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static String safeString(String s, String fallback) {
        if (s == null) return fallback;
        return s.trim();
    }

    private static String css() {
        return "<style>"
                + ":root{--bg:#090f1d;--card:#111827;--card2:#172033;--blue:#2563eb;--cyan:#06b6d4;--txt:#f8fafc;--mut:#94a3b8;}"
                + "*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at top,#19345f 0,#090f1d 42%,#050816 100%);color:var(--txt);font-family:Inter,Arial,'Noto Sans SC',sans-serif;}"
                + "header{position:sticky;top:0;z-index:5;display:flex;justify-content:space-between;align-items:center;padding:14px 18px;background:rgba(9,15,29,.78);backdrop-filter:blur(14px);border-bottom:1px solid rgba(255,255,255,.08)}"
                + ".brand{font-weight:800;letter-spacing:.3px}nav a{color:#dbeafe;text-decoration:none;margin-left:12px;font-size:14px}"
                + ".wrap{width:min(1050px,100%);margin:auto;padding:18px}.hero{display:flex;align-items:center;justify-content:space-between;margin:12px 0 16px;padding:18px;border-radius:24px;background:linear-gradient(135deg,rgba(37,99,235,.35),rgba(6,182,212,.16));border:1px solid rgba(255,255,255,.12)}"
                + "h1{font-size:28px;margin:0 0 6px}h2{margin:8px 0 12px}.hero p,p,li{color:#cbd5e1;line-height:1.6}.pill{padding:10px 14px;border-radius:999px;background:rgba(255,255,255,.14);color:white;text-decoration:none;border:1px solid rgba(255,255,255,.18)}"
                + ".card{background:linear-gradient(180deg,rgba(17,24,39,.96),rgba(15,23,42,.94));border:1px solid rgba(255,255,255,.10);box-shadow:0 18px 50px rgba(0,0,0,.35);border-radius:26px;padding:16px;margin-bottom:16px}"
                + "img{width:100%;max-height:72vh;object-fit:contain;background:#020617;border-radius:18px;border:1px solid rgba(255,255,255,.08)}"
                + ".grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.stat{background:rgba(15,23,42,.82);border:1px solid rgba(255,255,255,.08);border-radius:18px;padding:14px}.stat span{display:block;color:var(--mut);font-size:13px}.stat b{display:block;margin-top:6px;font-size:16px}"
                + ".actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:12px}button,.btn{border:0;border-radius:14px;padding:12px 16px;background:linear-gradient(135deg,var(--blue),var(--cyan));color:white;font-weight:700;text-decoration:none;font-size:15px}.ghost{background:rgba(255,255,255,.10);border:1px solid rgba(255,255,255,.12)}"
                + ".row{display:block;width:100%;text-align:left;margin:8px 0;background:rgba(255,255,255,.07)}.list{margin-top:12px}code{background:#020617;padding:3px 7px;border-radius:7px}"
                + "label{display:block;margin-top:14px;color:#dbeafe;font-weight:600}input{width:100%;border:1px solid rgba(255,255,255,.14);background:#020617;color:white;border-radius:14px;padding:12px;font-size:16px;margin-top:6px}.check input{width:auto;margin-right:8px}.ok,.note{background:rgba(22,163,74,.18);border:1px solid rgba(34,197,94,.35);color:#dcfce7;border-radius:16px;padding:12px;margin:12px 0}.note{background:rgba(37,99,235,.16);border-color:rgba(96,165,250,.35)}"
                + "@media(max-width:700px){.grid{grid-template-columns:repeat(2,1fr)}header{display:block}nav{margin-top:8px}nav a{margin-left:0;margin-right:10px}.hero{display:block}h1{font-size:24px}}"
                + "</style>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
