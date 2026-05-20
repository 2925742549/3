package com.xiao.safecamfinal;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

final class YuvToJpeg {
    private YuvToJpeg() {}

    static byte[] convert(ImageProxy image, int jpegQuality) {
        byte[] nv21 = yuv420ToNv21(image);
        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                image.getWidth(),
                image.getHeight(),
                null
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0, image.getWidth(), image.getHeight()),
                jpegQuality,
                out
        );
        return out.toByteArray();
    }

    private static byte[] yuv420ToNv21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();

        byte[] nv21 = new byte[width * height * 3 / 2];

        ByteBuffer yBuffer = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();

        int pos = 0;
        for (int row = 0; row < height; row++) {
            int rowStart = row * yRowStride;
            for (int col = 0; col < width; col++) {
                nv21[pos++] = yBuffer.get(rowStart + col * yPixelStride);
            }
        }

        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int uvHeight = height / 2;
        int uvWidth = width / 2;
        for (int row = 0; row < uvHeight; row++) {
            int rowStart = row * uvRowStride;
            for (int col = 0; col < uvWidth; col++) {
                int index = rowStart + col * uvPixelStride;
                nv21[pos++] = vBuffer.get(index);
                nv21[pos++] = uBuffer.get(index);
            }
        }
        return nv21;
    }
}
