/*
 * Copyright (C) 2012-2017 Andrew Neal, Angel Leon
 * Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.nio.IntBuffer;

/**
 * {@link Bitmap} specific helpers.
 *
 * @author Angel Leon (@gubatron)
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class BitmapUtils {

    private static final float[] BOX_BLUR_KERNEL = new float[] {
            0.0f, 0.2f, 0.0f,
            0.2f, 0.2f, 0.2f,
            0.0f, 0.2f, 0.0f,
    };

    /** This class is never instantiated */
    private BitmapUtils() {
    }

    /**
     * This is only used when the launcher shortcut is created.
     *
     * @param bitmap The artist, album, genre, or playlist image that's going to
     *            be cropped.
     * @param size The new size.
     * @return A {@link Bitmap} that has been resized and cropped for a launcher
     *         shortcut.
     */
    public static final Bitmap resizeAndCropCenter(final Bitmap bitmap, final int size) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        if (w == size && h == size) {
            return bitmap;
        }

        final float mScale = (float)size / Math.min(w, h);

        final Bitmap mTarget = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        final int mWidth = Math.round(mScale * bitmap.getWidth());
        final int mHeight = Math.round(mScale * bitmap.getHeight());
        final Canvas mCanvas = new Canvas(mTarget);
        mCanvas.translate((size - mWidth) / 2f, (size - mHeight) / 2f);
        mCanvas.scale(mScale, mScale);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        mCanvas.drawBitmap(bitmap, 0, 0, paint);
        // TODO: perhaps recycle the input bitmap, this method is only used to put a Bitmap
        // inside an INTENT.
        return mTarget;
    }

    /** Input image will be recycled, make sure to keep a copy elsewhere if you will need it */
    public static Bitmap blurBitmap(Bitmap image, boolean recycle) {
        if (image == null) {
            return null;
        }
        // 3 passes approximates gaussian blur, let's do 2
        image = convoluteBitmap(image, BOX_BLUR_KERNEL, recycle);
        return convoluteBitmap(image, BOX_BLUR_KERNEL, recycle);
    }

    /** Given an X,Y coordinate and the dimensions of our Matrix, it returns the corresponding offset on a linear array */
    private static int xy2i(int x, int y, int w, int h) {
        int offset = x*w + y;
        if (offset >= w*h) { offset = (w*h)-1; }
        return offset;
    }

    /**
     * Note: This convolution algorithm crops out the edges. Other options are wrapping and extending.
     * See https://www.wikiwand.com/en/Kernel_(image_processing)#/Edge_Handling
     * @param inputBitmap
     * @param kernel
     * @param recycle
     * @return
     */
    public static Bitmap convoluteBitmap(Bitmap inputBitmap, float[] kernel, boolean recycle) {
        int w = inputBitmap.getWidth();
        int h = inputBitmap.getHeight();
        int[] pixels = new int[w * h];
        inputBitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int x = 1; x < w-1; x++) {
            for (int y = 1; y < h-1; y++) {
                convolutePixel(pixels, x, y, w, h, kernel);
            }
        }
        if (recycle) {
            inputBitmap.recycle();
        }
        IntBuffer buffer = IntBuffer.wrap(pixels);
        buffer.rewind();
        Bitmap convolutedSmallBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        convolutedSmallBitmap.copyPixelsFromBuffer(buffer);
        return convolutedSmallBitmap;
    }

    /** It assumes the given kernel is a 3x3 matrix
     *
     * Given a pixel array, it takes a 3x3 matrix 'A' around the given
     * (x,y) coordinate and it applies the following convolution
     * operation against a 3x3 kernel to obtain the transformed value at x,y
     *   kernel                 A
     * [ a b c ]           [ 1 2 3 ]
     * [ d e f ] CONVOLUTE [ 4 5 6 ] => [x,y]' = (i*1)+(h*2)+(g*3)+(f*4)+(e*5)+(d*6)+(c*7)+(b*8)+(a*9)
     * [ g h i ]           [ 7 8 9 ]
     *
     *
     * @param pixels - the entire bitmap in a pixel array
     * @param x - x coordinate being convoluted
     * @param y - y coordinate being convoluted
     * @param w - entire bitmap's width
     * @param h - entire bitmap's height
     * @param kernel - 3x3 convolution kernel as a one dimensional float array
     */
    private static void convolutePixel(int[] pixels, int x, int y, int w, int h, float[] kernel) {
        if (x == 0 || y == 0 || x == w-1 || y == h-1) {
            return;
        }
        // Populate convolution matrix around given X,Y coordinate, a 3x3 matrix of values around us.
        int[] A = new int[9]; //3x3 matrix around the given x,y coordinate
        A[xy2i(0, 0, 3, 3)] = pixels[xy2i(x - 1, y - 1, w, h)];
        A[xy2i(1, 0, 3, 3)] = pixels[xy2i(x, y - 1, w, h)];
        A[xy2i(2, 0, 3, 3)] = pixels[xy2i(x + 1, y - 1, w, h)];
        A[xy2i(0, 1, 3, 3)] = pixels[xy2i(x - 1, y, w, h)];
        A[xy2i(1, 1, 3, 3)] = pixels[xy2i(x, y, w, h)];
        A[xy2i(2, 1, 3, 3)] = pixels[xy2i(x + 1, y, w, h)];
        A[xy2i(0, 2, 3, 3)] = pixels[xy2i(x - 1, y + 1, w, h)];
        A[xy2i(1, 2, 3, 3)] = pixels[xy2i(x, y + 1, w, h)];
        A[xy2i(2, 2, 3, 3)] = pixels[xy2i(x + 1, y + 1, w, h)];
        // convolution operation (not matrix multiplication)

        pixels[xy2i(x, y, w, h)] =
                PixelARGB_8888.multiplyByFloat(kernel[xy2i(2, 2, 3, 3)], A[xy2i(0, 0, 3, 3)], true) +
                PixelARGB_8888.multiplyByFloat(kernel[xy2i(1, 2, 3, 3)], A[xy2i(1, 0, 3, 3)], true) +
                PixelARGB_8888.multiplyByFloat(kernel[xy2i(0, 2, 3, 3)], A[xy2i(2, 0, 3, 3)], true) +

                PixelARGB_8888.multiplyByFloat(kernel[xy2i(2, 1, 3, 3)], A[xy2i(0, 1, 3, 3)], true) +

                PixelARGB_8888.multiplyByFloat(kernel[xy2i(1, 1, 3, 3)], A[xy2i(1, 1, 3, 3)], true) +

                PixelARGB_8888.multiplyByFloat(kernel[xy2i(0, 1, 3, 3)], A[xy2i(2, 1, 3, 3)], true) +

                PixelARGB_8888.multiplyByFloat(kernel[xy2i(2, 0, 3, 3)], A[xy2i(0, 2, 3, 3)], true) +
                PixelARGB_8888.multiplyByFloat(kernel[xy2i(1, 0, 3, 3)], A[xy2i(1, 2, 3, 3)], true) +
                PixelARGB_8888.multiplyByFloat(kernel[xy2i(0, 0, 3, 3)], A[xy2i(2, 2, 3, 3)], true);
    }

    /** ARGB_8888 Pixel abstraction */
    public static class PixelARGB_8888 {
        public final byte a;
        public final byte r;
        public final byte g;
        public final byte b;
        public final int intVal;

        public PixelARGB_8888(final int argb32bitInt) {
            a = (byte) ((argb32bitInt >> 24) & 0xff);
            r = (byte) ((argb32bitInt >> 16) & 0xff);
            g = (byte) ((argb32bitInt >> 8) & 0xff);
            b = (byte) (argb32bitInt & 0xff);
            intVal = argb32bitInt;
        }

        public PixelARGB_8888(byte a, byte r, byte g, byte b) {
            this.a = a;
            this.r = r;
            this.g = g;
            this.b = b;
            intVal = (a << 24) + (r << 16) + (g << 8) + b;
        }

        public static int multiplyByFloat(float factor, int arg32bitInt) {
            return multiplyByFloat(factor, arg32bitInt, false);
        }

        public static int multiplyByFloat(float factor, int argb32bitInt, boolean multiplyAlphaChannel) {
            PixelARGB_8888 original = new PixelARGB_8888(argb32bitInt);
            byte alpha = original.a;
            if (multiplyAlphaChannel) {
                alpha = (byte) (original.a * factor);
            }
            PixelARGB_8888 multiplied = new PixelARGB_8888(
                    alpha,
                    (byte) (factor * original.r),
                    (byte) (factor * original.g),
                    (byte) (factor * original.b));
            return multiplied.intVal;
        }
    }
}
