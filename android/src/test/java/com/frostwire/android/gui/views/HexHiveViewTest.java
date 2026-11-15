/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Looper;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The Pieces tab's hive, drawn with Android's real graphics. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HexHiveViewTest {
  static final int WIDTH = 480;
  static final int HEIGHT = 640;
  private static final int FULL = 0xff33b5e5;

  @Test
  public void everyPieceIsDrawn() throws Exception {
    // more pieces than the 1200 the hive drew before
    int pieces = 1500;
    HexHiveView<Void> view = newView();
    Bitmap bitmap = render(view, new Adapter(pieces, piece -> true));

    Point center = new Point();
    for (int piece : new int[] {0, 1199, 1200, pieces - 1}) {
      assertTrue(view.pieceCenter(piece, center));
      assertTrue("piece " + piece + " is outside the view", inside(center));
      assertEquals(
          "piece " + piece + " isn't drawn",
          FULL,
          bitmap.getPixel(center.x, center.y) | 0xff000000);
    }
  }

  static HexHiveView<Void> newView() {
    HexHiveView<Void> view = new HexHiveView<>(ApplicationProvider.getApplicationContext(), null);
    view.measure(
        View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, WIDTH, HEIGHT);
    return view;
  }

  /** Renders the hive on its background thread and draws the view the way the screen would. */
  static Bitmap render(HexHiveView<Void> view, HexHiveView.HexDataAdapter<Void> adapter)
      throws InterruptedException {
    // updateData drops a render less than 100 ms after the previous one
    Thread.sleep(110);
    view.updateData(adapter);
    long deadline = System.currentTimeMillis() + 10_000;
    while (!view.ready()) {
      assertTrue("the hive was never rendered", System.currentTimeMillis() < deadline);
      Thread.sleep(10);
    }
    shadowOf(Looper.getMainLooper()).idle();
    Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
    return bitmap;
  }

  private static boolean inside(Point point) {
    return point.x >= 0 && point.x < WIDTH && point.y >= 0 && point.y < HEIGHT;
  }

  interface PieceTest {
    boolean test(int piece);
  }

  static final class Adapter implements HexHiveView.HexDataAdapter<Void> {
    private final int pieces;
    private final PieceTest full;

    Adapter(int pieces, PieceTest full) {
      this.pieces = pieces;
      this.full = full;
    }

    @Override
    public void updateData(Void data) {}

    @Override
    public int getTotalHexagonsCount() {
      return pieces;
    }

    @Override
    public int getFullHexagonsCount() {
      int count = 0;
      for (int i = 0; i < pieces; i++) {
        count += full.test(i) ? 1 : 0;
      }
      return count;
    }

    @Override
    public boolean isFull(int hexOffset) {
      return full.test(hexOffset);
    }
  }
}
