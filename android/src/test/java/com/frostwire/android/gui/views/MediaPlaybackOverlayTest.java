/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import com.frostwire.android.gui.views.MediaPlaybackOverlayPainter.MediaPlaybackState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The play, preview and stop overlays, drawn with Android's real graphics. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaPlaybackOverlayTest {

  private static final int SIZE = 100;
  private static final int DARK = 0xff3b3b3b;

  private Bitmap bitmap;
  private Canvas canvas;

  @Before
  public void setUp() {
    bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
    canvas = new Canvas(bitmap);
  }

  private MediaPlaybackOverlayPainter painter(MediaPlaybackState state) {
    MediaPlaybackOverlayPainter painter = new MediaPlaybackOverlayPainter();
    painter.setCircleStrokeWidth(5);
    painter.setOverlayState(state);
    painter.drawOverlay(canvas, SIZE, SIZE);
    return painter;
  }

  @Test
  public void nothingIsDrawnWithoutAState() {
    painter(MediaPlaybackState.NONE);
    assertEquals(Color.TRANSPARENT, bitmap.getPixel(SIZE / 2, SIZE / 2));
    assertEquals(Color.TRANSPARENT, bitmap.getPixel(SIZE / 2, 15));
  }

  @Test
  public void aNewPainterStartsWithNothingToDraw() {
    new MediaPlaybackOverlayPainter().drawOverlay(canvas, SIZE, SIZE);
    assertEquals(Color.TRANSPARENT, bitmap.getPixel(SIZE / 2, SIZE / 2));
  }

  @Test
  public void playDrawsAWhiteCircleWithADarkTriangleInTheMiddle() {
    painter(MediaPlaybackState.PLAY);
    assertEquals(DARK, bitmap.getPixel(SIZE / 2, SIZE / 2));
    int circle = bitmap.getPixel(SIZE / 2, 15);
    assertNotEquals(Color.TRANSPARENT, circle);
    assertNotEquals(DARK, circle);
    // outside the circle's box corner stays clear
    assertEquals(Color.TRANSPARENT, bitmap.getPixel(2, 2));
  }

  @Test
  public void previewLooksLikePlay() {
    painter(MediaPlaybackState.PREVIEW);
    assertEquals(DARK, bitmap.getPixel(SIZE / 2, SIZE / 2));
  }

  @Test
  public void stopDrawsASquareThatFillsWhereTheTriangleLeavesGaps() {
    painter(MediaPlaybackState.STOP);
    int w = SIZE / 3;
    // the square's corner is dark, the triangle (play) would have left it empty
    assertEquals(DARK, bitmap.getPixel(SIZE / 2 - w / 2 + 2, SIZE / 2 - w / 2 + 2));
    assertEquals(DARK, bitmap.getPixel(SIZE / 2 + w / 2 - 2, SIZE / 2 + w / 2 - 2));
  }

  // ---- the view that hosts the painter

  private MediaPlaybackStatusOverlayView view() {
    Context context = ViewTestSupport.themedContext();
    MediaPlaybackStatusOverlayView view = new MediaPlaybackStatusOverlayView(context, null);
    view.layout(0, 0, SIZE, SIZE);
    return view;
  }

  @Test
  public void theViewShowsItselfOnlyWhenThereIsAPlaybackState() {
    MediaPlaybackStatusOverlayView view = view();
    view.setPlaybackState(MediaPlaybackState.PLAY);
    assertEquals(View.VISIBLE, view.getVisibility());
    view.setPlaybackState(MediaPlaybackState.NONE);
    assertEquals(View.GONE, view.getVisibility());
  }

  @Test
  public void theViewDrawsTheStateItWasGiven() {
    MediaPlaybackStatusOverlayView view = view();
    view.setPlaybackState(MediaPlaybackState.STOP);
    view.draw(canvas);
    assertEquals(DARK, bitmap.getPixel(SIZE / 2, SIZE / 2));
  }

  @Test
  public void changingTheStateAfterTheFirstDrawRepaints() {
    MediaPlaybackStatusOverlayView view = view();
    view.setPlaybackState(MediaPlaybackState.NONE);
    view.draw(canvas);
    assertEquals(Color.TRANSPARENT, bitmap.getPixel(SIZE / 2, SIZE / 2));
    view.setPlaybackState(MediaPlaybackState.PLAY);
    view.draw(canvas);
    assertEquals(DARK, bitmap.getPixel(SIZE / 2, SIZE / 2));
  }
}
