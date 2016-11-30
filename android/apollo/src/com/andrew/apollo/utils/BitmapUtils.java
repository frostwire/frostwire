/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;

/**
 * {@link Bitmap} specific helpers.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class BitmapUtils {

    private static final float BITMAP_SCALE = 0.4f;
    private static final float BLUR_RADIUS = 7.5f;

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
        return mTarget;
    }


    public static Bitmap createBlurredBitmap(Context context, Bitmap image) {
        int width = Math.round(image.getWidth() * BITMAP_SCALE);
        int height = Math.round(image.getHeight() * BITMAP_SCALE);
        Bitmap inputBitmap;

        //renderscript works with ARGB_8888 format of bitmaps - make sure that is what we pass to it.
        if (image.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap copyBitmap = image.copy(Bitmap.Config.ARGB_8888, true);
            inputBitmap = Bitmap.createScaledBitmap(copyBitmap, width, height, false);
        } else {
            inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
        }

        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
        theIntrinsic.setRadius(BLUR_RADIUS);

        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);
        return outputBitmap;
    }

}
