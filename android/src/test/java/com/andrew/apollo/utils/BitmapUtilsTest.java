/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Album art is cropped to a square for shortcuts and blurred behind the player. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class BitmapUtilsTest {

  private static Bitmap solid(int w, int h, int color) {
    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }

  @Test
  public void aSquareOfTheRightSizeIsReturnedAsIs() {
    Bitmap bitmap = solid(10, 10, Color.RED);

    assertSame(bitmap, BitmapUtils.resizeAndCropCenter(bitmap, 10));
  }

  @Test
  public void aWideImageIsScaledByItsShortSideAndCroppedToASquare() {
    Bitmap result = BitmapUtils.resizeAndCropCenter(solid(40, 20, Color.BLUE), 10);

    assertEquals(10, result.getWidth());
    assertEquals(10, result.getHeight());
  }

  @Test
  public void aTallImageAlsoEndsUpSquare() {
    Bitmap source = solid(20, 40, Color.GREEN);
    Bitmap result = BitmapUtils.resizeAndCropCenter(source, 30);

    assertNotSame(source, result);
    assertEquals(30, result.getWidth());
    assertEquals(30, result.getHeight());
  }

  @Test
  public void blurringASolidColorLeavesItSolid() {
    Bitmap source = solid(8, 6, Color.rgb(200, 100, 50));

    Bitmap blurred = BitmapUtils.stackBlur(source, 2);

    assertNotSame("the input is left alone", source, blurred);
    for (int x = 0; x < 8; x++) {
      for (int y = 0; y < 6; y++) {
        assertEquals(Color.rgb(200, 100, 50), blurred.getPixel(x, y));
      }
    }
  }

  @Test
  public void blurringSoftensTheEdgeBetweenBlackAndWhite() {
    Bitmap source = solid(12, 4, Color.BLACK);
    for (int x = 6; x < 12; x++) {
      for (int y = 0; y < 4; y++) {
        source.setPixel(x, y, Color.WHITE);
      }
    }

    Bitmap blurred = BitmapUtils.stackBlur(source, 3);

    int atEdge = Color.red(blurred.getPixel(6, 1));
    assertTrue("the edge is gray, not black or white: " + atEdge, atEdge > 0 && atEdge < 255);
    assertTrue(
        "darker on the black side",
        Color.red(blurred.getPixel(0, 1)) < Color.red(blurred.getPixel(11, 1)));
    assertEquals("the source keeps its hard edge", Color.BLACK, source.getPixel(5, 1));
  }
}
