/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.andrew.apollo.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * {@link Bitmap} specific helpers.
 *
 * @author Angel Leon (@gubatron)
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class BitmapUtils {
    /**
     * Thread-local array pools for stackBlur to reduce allocations.
     * These are reused across multiple blur operations on the same thread.
     */
    private static final ThreadLocal<int[]> pixelBufferPool = new ThreadLocal<>();
    private static final ThreadLocal<int[]> rBufferPool = new ThreadLocal<>();
    private static final ThreadLocal<int[]> gBufferPool = new ThreadLocal<>();
    private static final ThreadLocal<int[]> bBufferPool = new ThreadLocal<>();
    private static final ThreadLocal<int[]> vminBufferPool = new ThreadLocal<>();
    private static final ThreadLocal<int[]> dvBufferPool = new ThreadLocal<>();

    /**
     * This class is never instantiated
     */
    private BitmapUtils() {
    }

    /**
     * Get a reusable int array from the thread-local pool, or allocate if needed.
     * Reduces GC pressure by reusing arrays across multiple blur operations.
     */
    private static int[] getOrAllocateBuffer(ThreadLocal<int[]> pool, int requiredSize) {
        int[] buffer = pool.get();
        if (buffer == null || buffer.length < requiredSize) {
            buffer = new int[requiredSize];
            pool.set(buffer);
        }
        return buffer;
    }

    /**
     * This is only used when the launcher shortcut is created.
     *
     * @param bitmap The artist, album, genre, or playlist image that's going to
     *               be cropped.
     * @param size   The new size.
     * @return A {@link Bitmap} that has been resized and cropped for a launcher
     * shortcut.
     */
    public static Bitmap resizeAndCropCenter(final Bitmap bitmap, final int size) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        if (w == size && h == size) {
            return bitmap;
        }

        final float mScale = (float) size / Math.min(w, h);

        final Bitmap mTarget = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        final int mWidth = Math.round(mScale * bitmap.getWidth());
        final int mHeight = Math.round(mScale * bitmap.getHeight());
        final Canvas mCanvas = new Canvas(mTarget);
        mCanvas.translate((size - mWidth) / 2f, (size - mHeight) / 2f);
        mCanvas.scale(mScale, mScale);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        mCanvas.drawBitmap(bitmap, 0, 0, paint);
        return mTarget;
    }


    public static Bitmap stackBlur(final Bitmap inputBitmap, int blurRadius) {
        // Stack Blur v1.0 from
        // http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
        //
        // Java Author: Mario Klingemann <mario at quasimondo.com>
        // http://incubator.quasimondo.com
        // created Feburary 29, 2004
        // Android port : Yahel Bouaziz <yahel at kayenko.com>
        // http://www.kayenko.com
        // ported april 5th, 2012

        // This is a compromise between Gaussian Blur and Box blur
        // It creates much better looking blurs than Box Blur, but is
        // 7x faster than my Gaussian Blur implementation.
        //
        // I called it Stack Blur because this describes best how this
        // filter works internally: it creates a kind of moving stack
        // of colors whilst scanning through the image. Thereby it
        // just has to add one new block of color to the right side
        // of the stack and remove the leftmost color. The remaining
        // colors on the topmost layer of the stack are either added on
        // or reduced by one, depending on if they are on the right or
        // on the left side of the stack.
        //
        // If you are using this algorithm in your code please add
        // the following line:
        //
        // Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>

        final Bitmap mBitmap = inputBitmap.copy(inputBitmap.getConfig(), true);

        final int w = mBitmap.getWidth();
        final int h = mBitmap.getHeight();

        final int wm = w - 1;
        final int hm = h - 1;
        final int wh = w * h;
        final int div = blurRadius + blurRadius + 1;

        // Use pooled arrays to reduce allocations
        final int[] pix = getOrAllocateBuffer(pixelBufferPool, wh);
        final int[] r = getOrAllocateBuffer(rBufferPool, wh);
        final int[] g = getOrAllocateBuffer(gBufferPool, wh);
        final int[] b = getOrAllocateBuffer(bBufferPool, wh);
        final int[] vmin = getOrAllocateBuffer(vminBufferPool, Math.max(w, h));

        mBitmap.getPixels(pix, 0, w, 0, 0, w, h);
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;

        int divsum = div + 1 >> 1;
        divsum *= divsum;
        final int dvSize = 256 * divsum;
        final int[] dv = getOrAllocateBuffer(dvBufferPool, dvSize);
        for (i = 0; i < dvSize; i++) {
            dv[i] = i / divsum;
        }

        yw = yi = 0;

        final int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        final int r1 = blurRadius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -blurRadius; i <= blurRadius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + blurRadius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = blurRadius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - blurRadius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + blurRadius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }

        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -blurRadius * w;
            for (i = -blurRadius; i <= blurRadius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + blurRadius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = blurRadius;
            for (y = 0; y < h; y++) {
                pix[yi] = 0xff000000 | dv[rsum] << 16 | dv[gsum] << 8 | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - blurRadius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        mBitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return mBitmap;
    }
}
