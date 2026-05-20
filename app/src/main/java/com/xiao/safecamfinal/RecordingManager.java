package com.xiao.safecamfinal;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

final class RecordingManager {
    private static final SimpleDateFormat NAME_FMT =
            new SimpleDateFormat("yyyyMMdd_HHmmss_SSS'.jpg'", Locale.US);

    private RecordingManager() {}

    static File dir(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (base == null) base = context.getFilesDir();
        File d = new File(base, "recordings");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static void saveFrame(Context context, byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) return;
        File d = dir(context);
        File f = new File(d, NAME_FMT.format(new Date()));
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(jpeg);
        } catch (Exception ignored) {}
    }

    static File[] list(Context context) {
        File[] files = dir(context).listFiles((file, name) -> name.endsWith(".jpg"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        return files;
    }

    static void cleanup(Context context) {
        int retentionHours = AppSettings.retentionHours(context);
        long maxBytes = AppSettings.maxStorageMb(context) * 1024L * 1024L;
        long cutoff = System.currentTimeMillis() - retentionHours * 60L * 60L * 1000L;

        File[] files = list(context);
        for (File f : files) {
            if (f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }

        files = list(context);
        long total = 0;
        for (File f : files) total += f.length();

        int i = 0;
        while (total > maxBytes && i < files.length) {
            long len = files[i].length();
            if (files[i].delete()) total -= len;
            i++;
        }
    }

    static long totalBytes(Context context) {
        long total = 0;
        for (File f : list(context)) total += f.length();
        return total;
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
