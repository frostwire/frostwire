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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.bittorrent.PiecePriorities;
import java.util.ArrayList;
import java.util.List;
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

  @Test
  public void aPointMapsToThePieceWhoseCubeItIsIn() {
    for (int pieces : new int[] {1, 7, 40, 600, 1500, 5000}) {
      HexHiveView<Void> view = newView();
      view.updateData(new Adapter(pieces, piece -> false));
      Point center = new Point();
      Point next = new Point();
      assertTrue(view.pieceCenter(0, center));
      // half the distance between two neighbors is the cube's inner radius
      float inner = pieces == 1 ? 10 : 0;
      if (pieces > 1) {
        assertTrue(view.pieceCenter(1, next));
        inner = (next.x - center.x) / 2f;
      }
      for (int piece = 0; piece < pieces; piece++) {
        assertTrue(view.pieceCenter(piece, center));
        String where = pieces + " pieces, piece " + piece;
        assertEquals(where, piece, view.pieceAt(center.x, center.y));
        // off center, still inside the cube
        assertEquals(where, piece, view.pieceAt(center.x + inner * 0.8f, center.y));
        assertEquals(where, piece, view.pieceAt(center.x - inner * 0.8f, center.y));
        assertEquals(where, piece, view.pieceAt(center.x, center.y - inner * 0.8f));
        assertEquals(where, piece, view.pieceAt(center.x, center.y + inner * 0.8f));
      }
      assertTrue(view.pieceCenter(pieces - 1, center));
      // a single piece is one big cube in the middle of the view
      float belowLastRow = pieces == 1 ? HEIGHT / 2f : inner * 6;
      assertEquals("below the last row", -1, view.pieceAt(center.x, center.y + belowLastRow));
      assertEquals("the top left corner", -1, view.pieceAt(0, 0));
    }
  }

  @Test
  public void aPointIsNoPieceBeforeTheHiveIsLaidOut() {
    HexHiveView<Void> view = new HexHiveView<>(ApplicationProvider.getApplicationContext(), null);
    assertEquals(-1, view.pieceAt(10, 10));
  }

  @Test
  public void aTapSelectsThePieceUnderItAndADragDoesNot() {
    HexHiveView<Void> view = newView();
    view.updateData(new Adapter(100, piece -> false));
    List<Integer> tapped = new ArrayList<>();
    view.setOnPieceTapListener(tapped::add);
    Point center = new Point();
    assertTrue(view.pieceCenter(42, center));

    touch(view, MotionEvent.ACTION_DOWN, center.x, center.y);
    touch(view, MotionEvent.ACTION_UP, center.x + 1, center.y);
    assertEquals(List.of(42), tapped);
    assertEquals(42, view.getSelectedPiece());

    // a drag across the hive is a scroll or a swipe, not a tap
    assertTrue(view.pieceCenter(7, center));
    touch(view, MotionEvent.ACTION_DOWN, center.x, center.y);
    touch(view, MotionEvent.ACTION_MOVE, center.x + 100, center.y);
    touch(view, MotionEvent.ACTION_UP, center.x + 100, center.y);
    assertEquals(List.of(42), tapped);
    assertEquals(42, view.getSelectedPiece());

    // a tap off the cubes clears the selection
    touch(view, MotionEvent.ACTION_DOWN, 0, 0);
    touch(view, MotionEvent.ACTION_UP, 0, 0);
    assertEquals(List.of(42, -1), tapped);
    assertEquals(-1, view.getSelectedPiece());
  }

  @Test
  public void theSelectedPieceIsHighlighted() throws Exception {
    HexHiveView<Void> view = newView();
    Adapter adapter = new Adapter(900, piece -> piece == 2);
    Bitmap before = render(view, adapter);
    assertNotEquals(HexHiveView.SELECTED_COLOR, pixelAt(view, before, 2));

    view.setSelectedPiece(2);
    Bitmap after = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(after));
    assertEquals(HexHiveView.SELECTED_COLOR, pixelAt(view, after, 2));
    assertEquals(pixelAt(view, before, 400), pixelAt(view, after, 400));
  }

  private static void touch(View view, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
    view.dispatchTouchEvent(event);
    event.recycle();
  }

  @Test
  public void piecesThatWontDownloadAreRedAndHighPriorityOnesOrange() throws Exception {
    int[] marks = {
      PiecePriorities.MARK_NONE, PiecePriorities.MARK_SKIP, PiecePriorities.MARK_HIGH,
    };
    // past 600 pieces the cubes are flat, the whole cube has its base color
    int pieces = 900;
    HexHiveView<Void> view = newView();
    Adapter adapter = new Adapter(pieces, piece -> piece >= 3 && piece < 6);
    adapter.marks = piece -> marks[piece % 3];
    Bitmap bitmap = render(view, adapter);

    int empty = pixelAt(view, bitmap, 0);
    assertNotEquals(HexHiveView.SKIPPED_COLOR, empty);
    assertNotEquals(HexHiveView.HIGH_PRIORITY_COLOR, empty);
    assertEquals(HexHiveView.SKIPPED_COLOR, pixelAt(view, bitmap, 1));
    assertEquals(HexHiveView.HIGH_PRIORITY_COLOR, pixelAt(view, bitmap, 2));
    // a downloaded piece shows as downloaded whatever its priority
    for (int piece = 3; piece < 6; piece++) {
      assertEquals("piece " + piece, FULL, pixelAt(view, bitmap, piece));
    }
  }

  private static int pixelAt(HexHiveView<Void> view, Bitmap bitmap, int piece) {
    Point center = new Point();
    assertTrue(view.pieceCenter(piece, center));
    return bitmap.getPixel(center.x, center.y) | 0xff000000;
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

  interface PieceMark {
    int mark(int piece);
  }

  static final class Adapter implements HexHiveView.HexDataAdapter<Void> {
    private final int pieces;
    private final PieceTest full;
    PieceMark marks = piece -> PiecePriorities.MARK_NONE;

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

    @Override
    public int priorityMark(int hexOffset) {
      return marks.mark(hexOffset);
    }
  }
}
