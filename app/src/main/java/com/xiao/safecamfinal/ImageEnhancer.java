package com.xiao.safecamfinal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;

final class ImageEnhancer {
    private ImageEnhancer() {}

    static byte[] enhanceLowLight(byte[] jpeg, int brightnessPercent, int contrastPercent, boolean gray) {
        if (jpeg == null || jpeg.length == 0) return jpeg;

        Bitmap src = null;
        Bitmap out = null;
        try {
            src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (src == null) return jpeg;

            out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

            float contrast = 1.0f + (contrastPercent / 100.0f);
            float translate = (-0.5f * contrast + 0.5f) * 255.0f + brightnessPercent * 2.0f;

            ColorMatrix cm = new ColorMatrix(new float[]{
                    contrast, 0, 0, 0, translate,
                    0, contrast, 0, 0, translate,
                    0, 0, contrast, 0, translate,
                    0, 0, 0, 1, 0
            });

            if (gray) {
                ColorMatrix sat = new ColorMatrix();
                sat.setSaturation(0);
                cm.postConcat(sat);
            }

            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            paint.setColorFilter(new ColorMatrixColorFilter(cm));

            Canvas canvas = new Canvas(out);
            canvas.drawBitmap(src, 0, 0, paint);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out.compress(Bitmap.CompressFormat.JPEG, 70, bos);
            return bos.toByteArray();
        } catch (Throwable t) {
            return jpeg;
        } finally {
            try {
                if (src != null && !src.isRecycled()) src.recycle();
                if (out != null && !out.isRecycled()) out.recycle();
            } catch (Exception ignored) {}
        }
    }
}
