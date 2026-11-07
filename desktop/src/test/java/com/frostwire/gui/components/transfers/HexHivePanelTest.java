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

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.CountingRepaintManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Drives the pieces hive off-screen and inspects its backing bitmap and hover state. */
class HexHivePanelTest {

  private static final int SIDE = 16;
  private static final int BACKGROUND = 0xffffff;

  @Test
  void secondToLastRowBottomTipsAreRenderedPastAShortLastRow() throws Exception {
    HexHivePanel probe = newPanel();
    probe.updateData(adapter(1));
    Object props = get(probe, "drawingProperties");
    int evenCount = (int) get(props, "evenRowHexCount");
    int oddCount = (int) get(props, "oddRowHexCount");
    assertTrue(oddCount > 2, "panel must be wide enough for a short last row");

    // rows 0 and 1 full, row 2 (the last one) holds a single hexagon on the left
    HexHivePanel panel = newPanel();
    panel.updateData(adapter(evenCount + oddCount + 1));
    BufferedImage bitmap = awaitRender(panel);

    props = get(panel, "drawingProperties");
    Point oddOrigin = (Point) get(props, "oddRowOrigin");
    float hexWidth = (float) get(props, "hexWidth");
    int centerX = Math.round(oddOrigin.x + (oddCount - 1) * hexWidth);
    // just above the bottom tip of row 1's rightmost cube, below the row 1 / row 2 midpoint
    int tipY = oddOrigin.y + SIDE - 2;
    assertNotEquals(
        BACKGROUND,
        bitmap.getRGB(centerX, tipY) & 0xffffff,
        "bottom tip of the second to last row was clipped");
  }

  @Test
  void everyRowsRightmostCubeIsRenderedWhole() throws Exception {
    // Widths where even and odd rows hold as many cubes, and others, at 1x and 2x: odd rows
    // start half a cube to the right, so their last cube used to be cut off by the hive's width
    for (double scale : new double[] {1.0, 2.0}) {
      for (int width = 280; width <= 340; width++) {
        HexHivePanel panel = newPanel();
        panel.setSize(width, 400);
        set(panel, "renderScale", scale);
        panel.updateData(adapter(60));
        BufferedImage bitmap = awaitRender(panel);
        BufferedImage reference = renderInOnePass(panel, bitmap, scale);

        Object props = get(panel, "drawingProperties");
        Point evenOrigin = (Point) get(props, "evenRowOrigin");
        float hexHeight = (float) get(props, "hexHeight");
        int lastRow = rowIndexForPiece(props, 59);
        for (int row = 0; row <= lastRow; row++) {
          int centerY = (int) Math.round((evenOrigin.y + row * hexHeight * 0.75f) * scale);
          assertEquals(
              rightmostDrawnX(reference, centerY),
              rightmostDrawnX(bitmap, centerY),
              "row " + row + " cut off at width " + width + ", scale " + scale);
        }
      }
    }
  }

  @Test
  void backingBitmapIsRenderedAtDeviceScale() throws Exception {
    HexHivePanel panel = newPanel();
    set(panel, "renderScale", 2.0);
    panel.updateData(adapter(40));
    BufferedImage bitmap = awaitRender(panel);
    Object props = get(panel, "drawingProperties");
    assertEquals((int) get(props, "width") * 2, bitmap.getWidth());
    assertEquals((int) get(props, "height") * 2, bitmap.getHeight());
  }

  @Test
  void hoveringAHexagonSelectsItAndAsksTheAdapterToDescribeIt() throws Exception {
    BlockingQueue<Integer> described = new ArrayBlockingQueue<>(4);
    HexHivePanel.HexDataAdapter<Object> adapter =
        new HexHivePanel.HexDataAdapter<>() {
          @Override
          public void updateData(Object data) {}

          @Override
          public int getTotalHexagonsCount() {
            return 40;
          }

          @Override
          public int getFullHexagonsCount() {
            return 0;
          }

          @Override
          public boolean isFull(int hexOffset) {
            return false;
          }

          @Override
          public Map<String, String> describeHex(int hexOffset) {
            described.add(hexOffset);
            return Map.of("Piece", String.valueOf(hexOffset));
          }
        };
    HexHivePanel panel = newPanel();
    panel.updateData(adapter);
    Object props = get(panel, "drawingProperties");
    Point evenOrigin = (Point) get(props, "evenRowOrigin");
    float hexWidth = (float) get(props, "hexWidth");
    // third hexagon of the first row, nudged off-center but still inside it
    int x = Math.round(evenOrigin.x + 2 * hexWidth) + SIDE / 3;
    int y = evenOrigin.y - SIDE / 3;

    panel.dispatchEvent(mouseEvent(panel, MouseEvent.MOUSE_MOVED, x, y));
    assertEquals(2, (int) get(panel, "hoveredHex"));
    assertEquals(2, described.poll(10, TimeUnit.SECONDS));

    panel.dispatchEvent(mouseEvent(panel, MouseEvent.MOUSE_EXITED, x, y));
    assertEquals(-1, (int) get(panel, "hoveredHex"));
  }

  @Test
  void pointerOutsideTheHiveHoversNothing() throws Exception {
    HexHivePanel panel = newPanel();
    panel.updateData(adapter(3));
    panel.dispatchEvent(mouseEvent(panel, MouseEvent.MOUSE_MOVED, 290, 390));
    assertEquals(-1, (int) get(panel, "hoveredHex"));
  }

  @Test
  void rowsWrapToTheViewportWidth() throws Exception {
    HexHivePanel panel = newPanel();
    JViewport viewport = new JViewport();
    viewport.setView(panel);
    viewport.setSize(600, 200);
    viewport.doLayout();
    panel.updateData(adapter(300));
    awaitRender(panel);
    int wideRowCount = (int) get(get(panel, "drawingProperties"), "evenRowHexCount");

    viewport.setSize(250, 200);
    viewport.doLayout();
    assertEquals(
        250, panel.getWidth(), "panel must shrink with the viewport, no horizontal scroll");
    Object wide = get(panel, "drawingProperties");
    panel.dispatchEvent(new ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED));
    // the re-wrap may wait for the resize to stop
    long deadline = System.currentTimeMillis() + 5_000;
    while (get(panel, "drawingProperties") == wide && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    awaitRender(panel);
    int narrowRowCount = (int) get(get(panel, "drawingProperties"), "evenRowHexCount");
    assertTrue(narrowRowCount < wideRowCount, "rows must re-wrap to the narrower width");
    assertTrue(panel.getPreferredSize().height > 200, "extra rows must grow the panel vertically");
  }

  @Test
  void relayoutWhileRenderingLeavesNoStalePixels() throws Exception {
    HexHivePanel panel = newPanel();
    panel.setSize(600, 400);
    panel.updateData(adapter(400));
    // re-wrap while the wide layout may still be rendering
    panel.setSize(300, 400);
    panel.updateData(adapter(400));
    BufferedImage relaidOut = awaitRender(panel);

    HexHivePanel fresh = newPanel();
    fresh.setSize(300, 400);
    fresh.updateData(adapter(400));
    BufferedImage expected = awaitRender(fresh);

    assertEquals(expected.getWidth(), relaidOut.getWidth());
    assertEquals(expected.getHeight(), relaidOut.getHeight());
    for (int y = 0; y < expected.getHeight(); y++) {
      for (int x = 0; x < expected.getWidth(); x++) {
        if (expected.getRGB(x, y) != relaidOut.getRGB(x, y)) {
          throw new AssertionError("stale pixel at " + x + "," + y);
        }
      }
    }
  }

  /**
   * While the window is being dragged wider or narrower, the hive must keep the layout it has
   * painted (only the right edge changes) and re-wrap once the resize stops: re-wrapping on every
   * resize event moved every piece on each step of the drag, which looked like the pieces being
   * erased and painted again.
   */
  @Test
  void dragResizingRewrapsOnlyOnceTheResizeStops() throws Exception {
    HexHivePanel panel = newPanel();
    JViewport viewport = new JViewport();
    viewport.setView(panel);
    viewport.setSize(600, 200);
    viewport.doLayout();
    panel.updateData(adapter(300));
    awaitRender(panel);
    Object painted = get(panel, "drawingProperties");
    int wideRowCount = (int) get(painted, "evenRowHexCount");

    for (int width = 590; width >= 300; width -= 10) {
      viewport.setSize(width, 200);
      viewport.doLayout();
      panel.dispatchEvent(new ComponentEvent(panel, ComponentEvent.COMPONENT_RESIZED));
      assertSame(
          painted, get(panel, "drawingProperties"), "re-wrapped during the drag at " + width);
    }

    long deadline = System.currentTimeMillis() + 5_000;
    while (get(panel, "drawingProperties") == painted && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    awaitRender(panel);
    int narrowRowCount = (int) get(get(panel, "drawingProperties"), "evenRowHexCount");
    assertTrue(narrowRowCount < wideRowCount, "rows must re-wrap once the resize stops");
  }

  /**
   * Resizing the window re-wraps the rows and starts a new backing bitmap, which is rendered in the
   * background. Until it is, painting showed the new, empty bitmap: the hive flashed white on every
   * resize. It must keep showing what was painted until the new rows arrive.
   */
  @Test
  void resizingKeepsThePaintedHiveUntilTheNewLayoutIsRendered() throws Exception {
    HexHivePanel panel = newPanel();
    panel.setSize(400, 300);
    panel.updateData(adapter(120));
    awaitRender(panel);
    int painted = nonBackground(paint(panel, 400, 300));
    assertTrue(painted > 1000, "the first layout must have been painted");

    CountDownLatch release = new CountDownLatch(1);
    ((ExecutorService) get(panel, "threadPool"))
        .submit(
            () -> {
              release.await();
              return null;
            });
    try {
      panel.setSize(480, 300);
      panel.updateData(adapter(120));
      BufferedImage duringRender = paint(panel, 480, 300);
      assertTrue(
          nonBackground(duringRender) > painted / 2,
          "the hive went blank while the new layout was rendering");
    } finally {
      release.countDown();
    }
    awaitRender(panel);
    settle(panel);

    HexHivePanel fresh = newPanel();
    fresh.setSize(480, 300);
    fresh.updateData(adapter(120));
    awaitRender(fresh);
    settle(fresh);
    BufferedImage expected = paint(fresh, 480, 300), actual = paint(panel, 480, 300);
    for (int y = 0; y < expected.getHeight(); y++) {
      for (int x = 0; x < expected.getWidth(); x++) {
        if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
          throw new AssertionError("pixel of the old layout left at " + x + "," + y);
        }
      }
    }
  }

  private static BufferedImage paint(HexHivePanel panel, int w, int h) {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    panel.paint(g);
    g.dispose();
    return image;
  }

  private static int nonBackground(BufferedImage image) {
    int n = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) & 0xffffff) != BACKGROUND) n++;
      }
    }
    return n;
  }

  @Test
  void backingBitmapStaysViewportSizedForHugeTorrents() throws Exception {
    // GitHub #1315: the bitmap used to be as tall as all the rows, ~560 MB for 50k pieces at 2x
    int viewportWidth = 1200;
    int viewportHeight = 300;
    double scale = 2.0;
    HexHivePanel panel = newPanel();
    set(panel, "renderScale", scale);
    JViewport viewport = viewportFor(panel, viewportWidth, viewportHeight, adapter(50_000));
    BufferedImage bitmap = awaitRender(panel);

    int contentHeight = (int) get(get(panel, "drawingProperties"), "height");
    assertTrue(contentHeight > viewportHeight * 50, "the hive must be far taller than the view");
    long maxPixels = (long) (viewportWidth * scale) * (long) (2 * viewportHeight * scale + 1);
    assertTrue(
        (long) bitmap.getWidth() * bitmap.getHeight() <= maxPixels,
        "backing bitmap is "
            + bitmap.getWidth()
            + "x"
            + bitmap.getHeight()
            + ", it must stay bounded by the viewport");

    // scrolled far down, the window follows the viewport and stays as small
    viewport.setViewPosition(new Point(0, contentHeight / 2));
    settle(panel);
    bitmap = (BufferedImage) get(panel, "bitmap");
    assertTrue((long) bitmap.getWidth() * bitmap.getHeight() <= maxPixels);
    int originY = (int) get(panel, "bitmapOriginY");
    int windowHeight = (int) get(panel, "bitmapWindowHeight");
    assertTrue(originY <= contentHeight / 2, "window must start above the visible rows");
    assertTrue(
        originY + windowHeight >= contentHeight / 2 + viewportHeight,
        "window must reach below the visible rows");
  }

  @Test
  void movingTheWindowKeepsThePixelsOfTheRowsItStillCovers() throws Exception {
    for (double scale : new double[] {1.0, 1.5, 2.0}) {
      HexHivePanel panel = newPanel();
      set(panel, "renderScale", scale);
      JViewport viewport = viewportFor(panel, 600, 300, adapter(3000));
      settle(panel);
      BufferedImage before = copyOf((BufferedImage) get(panel, "bitmap"));
      int beforeOriginY = (int) get(panel, "bitmapOriginY");

      // far enough for the window to move, close enough for the old and new windows to overlap
      viewport.setViewPosition(new Point(0, 300));
      Class<?> propertiesClass = Class.forName(HexHivePanel.class.getName() + "$DrawingProperties");
      Method ensureBackingBitmap =
          HexHivePanel.class.getDeclaredMethod(
              "ensureBackingBitmap", propertiesClass, Rectangle.class);
      ensureBackingBitmap.setAccessible(true);
      ensureBackingBitmap.invoke(panel, get(panel, "drawingProperties"), panel.getVisibleRect());

      int originY = (int) get(panel, "bitmapOriginY");
      assertTrue(originY > beforeOriginY, "the window must follow the viewport down");
      BufferedImage after = (BufferedImage) get(panel, "bitmap");
      Area kept = (Area) get(panel, "renderedArea");
      assertTrue(!kept.isEmpty(), "rows both windows cover must stay rendered");
      int shift = toDevice(originY, scale) - toDevice(beforeOriginY, scale);
      int compared = 0;
      for (int y = 0; y < after.getHeight(); y++) {
        int beforeY = y + shift;
        double contentY = originY + (y + 0.5) / scale;
        for (int x = 0; x < after.getWidth(); x++) {
          int pixel = after.getRGB(x, y) & 0xffffff;
          if (kept.contains((x + 0.5) / scale, contentY)) {
            compared++;
            assertEquals(
                before.getRGB(x, beforeY) & 0xffffff,
                pixel,
                "kept pixel " + x + "," + y + " moved wrong at scale " + scale);
          } else if (beforeY >= before.getHeight()) {
            assertEquals(BACKGROUND, pixel, "rows new to the window must start blank");
          }
        }
      }
      assertTrue(compared > after.getWidth() * 50, "too few kept pixels compared");

      // the rows that came into view get rendered
      settle(panel);
      Rectangle visible = panel.getVisibleRect();
      assertTrue(
          ((Area) get(panel, "renderedArea"))
              .contains(visible.x + 20, visible.y, visible.width - 60, visible.height),
          "the visible rows must be rendered after scrolling");
    }
  }

  private static int toDevice(int logical, double scale) {
    return (int) Math.round(logical * scale);
  }

  private static BufferedImage copyOf(BufferedImage image) {
    BufferedImage copy =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    copy.getGraphics().drawImage(image, 0, 0, null);
    return copy;
  }

  @Test
  void paintingARenderedHiveQueuesNoMoreRenders() throws Exception {
    HexHivePanel panel = newPanel();
    viewportFor(panel, 600, 300, adapter(3000));
    settle(panel);
    // the padding left of and above the cubes is part of the visible area too
    Rectangle visible =
        panel
            .getVisibleRect()
            .intersection(
                new Rectangle(0, 0, (int) get(get(panel, "drawingProperties"), "width"), 10_000));
    assertTrue(
        ((Area) get(panel, "renderedArea"))
            .contains(visible.x, visible.y, visible.width, visible.height),
        "a rendered visible area must count as rendered, or every paint renders it again");

    requestFocusedRender(panel);
    synchronized (get(panel, "renderLock")) {
      assertTrue(
          !(boolean) get(panel, "renderRunning") && get(panel, "pendingRenderRequest") == null,
          "painting an up to date hive must not queue a render");
    }
  }

  /** Shows {@code panel} in a viewport and lays out the hive for {@code adapter}. */
  private static JViewport viewportFor(
      HexHivePanel panel, int width, int height, HexHivePanel.HexDataAdapter<Object> adapter)
      throws Exception {
    JViewport viewport = new JViewport();
    viewport.setView(panel);
    viewport.setSize(width, height);
    viewport.doLayout();
    panel.updateData(adapter);
    awaitRender(panel);
    // the panel grows to the rows' height once they're laid out
    viewport.doLayout();
    panel.updateData(adapter);
    awaitRender(panel);
    return viewport;
  }

  /** Renders the visible rows and their look-ahead the way painting does. */
  private static void settle(HexHivePanel panel) throws Exception {
    for (int i = 0; i < 3; i++) {
      requestFocusedRender(panel);
      awaitRender(panel);
    }
  }

  /** What every paint does: queue a render of whatever visible rows aren't rendered yet. */
  private static void requestFocusedRender(HexHivePanel panel) throws Exception {
    Class<?> propertiesClass = Class.forName(HexHivePanel.class.getName() + "$DrawingProperties");
    Method requestFocusedRender =
        HexHivePanel.class.getDeclaredMethod(
            "requestFocusedRender",
            propertiesClass,
            HexHivePanel.HexDataAdapter.class,
            boolean.class);
    requestFocusedRender.setAccessible(true);
    requestFocusedRender.invoke(
        panel, get(panel, "drawingProperties"), get(panel, "latestAdapter"), false);
  }

  private static MouseEvent mouseEvent(HexHivePanel panel, int id, int x, int y) {
    return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, x, y, 0, false, 0);
  }

  /** The Pieces tab refreshes every second; only a new cube layout may ask for a layout pass. */
  @Test
  void refreshWithTheSameLayoutRequestsNoLayout() throws Exception {
    HexHivePanel panel = newPanel();
    new JPanel().add(panel); // revalidate() is a no-op without a parent
    HexHivePanel.HexDataAdapter<Object> pieces = adapter(400);
    SwingUtilities.invokeAndWait(() -> panel.updateData(pieces));
    int requests =
        CountingRepaintManager.layoutRequestsDuring(
            () -> {
              for (int tick = 0; tick < 10; tick++) {
                panel.updateData(pieces);
              }
            });
    assertEquals(0, requests);
  }

  @Test
  void refreshWithMoreRowsRequestsALayout() throws Exception {
    HexHivePanel panel = newPanel();
    new JPanel().add(panel);
    SwingUtilities.invokeAndWait(() -> panel.updateData(adapter(40)));
    int requests =
        CountingRepaintManager.layoutRequestsDuring(() -> panel.updateData(adapter(400)));
    assertTrue(requests > 0, "a taller hive must tell its scroll pane");
  }

  /** Center of a piece's cube, from the layout's row counts and origins alone. */
  private static Point expectedCenter(Object props, int piece) throws Exception {
    int evenCount = (int) get(props, "evenRowHexCount");
    int oddCount = (int) get(props, "oddRowHexCount");
    Point evenOrigin = (Point) get(props, "evenRowOrigin");
    Point oddOrigin = (Point) get(props, "oddRowOrigin");
    float hexWidth = (float) get(props, "hexWidth");
    float hexHeight = (float) get(props, "hexHeight");
    int row = 2 * (piece / (evenCount + oddCount));
    int col = piece % (evenCount + oddCount);
    if (col >= evenCount) {
      row++;
      col -= evenCount;
    }
    int originX = row % 2 == 0 ? evenOrigin.x : oddOrigin.x;
    return new Point(
        Math.round(originX + col * hexWidth), Math.round(evenOrigin.y + row * hexHeight * 0.75f));
  }

  @Test
  void pieceAtFindsEveryCubeFromItsCenterToNearItsEdges() throws Exception {
    for (int width : new int[] {300, 317, 480}) {
      HexHivePanel panel = newPanel();
      panel.setSize(width, 400);
      panel.updateData(adapter(97));
      Object props = get(panel, "drawingProperties");
      // the circle inside the hexagon has a radius of sqrt(3)/2 * side, stay a bit within it
      float reach = SIDE * 0.8f;
      for (int piece = 0; piece < 97; piece++) {
        Point center = expectedCenter(props, piece);
        assertEquals(piece, panel.pieceAt(center.x, center.y), "center of piece " + piece);
        for (int angle = 0; angle < 360; angle += 30) {
          int x = (int) Math.round(center.x + reach * Math.cos(Math.toRadians(angle)));
          int y = (int) Math.round(center.y + reach * Math.sin(Math.toRadians(angle)));
          assertEquals(
              piece,
              panel.pieceAt(x, y),
              "piece " + piece + " at " + angle + " degrees, width " + width);
        }
      }
    }
  }

  @Test
  void pieceAtIsMinusOneAwayFromTheCubes() throws Exception {
    HexHivePanel panel = newPanel();
    assertEquals(-1, panel.pieceAt(20, 20), "no layout yet");
    panel.updateData(adapter(40));
    Object props = get(panel, "drawingProperties");
    int evenCount = (int) get(props, "evenRowHexCount");
    float hexWidth = (float) get(props, "hexWidth");
    float hexHeight = (float) get(props, "hexHeight");

    assertEquals(-1, panel.pieceAt(0, 0), "top-left padding");
    Point lastOfFirstRow = expectedCenter(props, evenCount - 1);
    assertEquals(
        -1,
        panel.pieceAt(Math.round(lastOfFirstRow.x + hexWidth), lastOfFirstRow.y),
        "past the end of a row");
    Point last = expectedCenter(props, 39);
    assertEquals(-1, panel.pieceAt(Math.round(last.x + hexWidth), last.y), "after the last piece");
    assertEquals(-1, panel.pieceAt(last.x, Math.round(last.y + hexHeight)), "below the last row");
    assertEquals(-1, panel.pieceAt(-5, -5));
  }

  @Test
  void clickingOrRightClickingACubeTellsTheListenerWhichPiece() throws Exception {
    HexHivePanel panel = newPanel();
    panel.updateData(adapter(40));
    Object props = get(panel, "drawingProperties");
    BlockingQueue<Integer> clicked = new ArrayBlockingQueue<>(8);
    panel.setPieceClickListener((piece, event) -> clicked.add(piece));
    Point seven = expectedCenter(props, 7);
    Point eight = expectedCenter(props, 8);
    SwingUtilities.invokeAndWait(
        () -> {
          // left click
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_PRESSED, seven, 1, false));
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_RELEASED, seven, 1, false));
          // right click where the release is the popup trigger (Windows)
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_PRESSED, eight, 3, false));
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_RELEASED, eight, 3, true));
          // right click where the press is the popup trigger (macOS, Linux): one menu, not two
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_PRESSED, seven, 3, true));
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_RELEASED, seven, 3, false));
          // dragged from one cube to another, no click
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_PRESSED, seven, 1, false));
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_RELEASED, eight, 1, false));
          // between cubes, no piece
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_PRESSED, new Point(0, 0), 1, false));
          panel.dispatchEvent(button(panel, MouseEvent.MOUSE_RELEASED, new Point(0, 0), 1, false));
        });
    assertEquals(List.of(7, 8, 7), List.copyOf(clicked));
  }

  private static MouseEvent button(
      HexHivePanel panel, int id, Point at, int button, boolean popupTrigger) {
    int mask = button == 1 ? MouseEvent.BUTTON1_DOWN_MASK : MouseEvent.BUTTON3_DOWN_MASK;
    return new MouseEvent(
        panel,
        id,
        System.currentTimeMillis(),
        id == MouseEvent.MOUSE_PRESSED ? mask : 0,
        at.x,
        at.y,
        at.x,
        at.y,
        1,
        popupTrigger,
        button == 1 ? MouseEvent.BUTTON1 : MouseEvent.BUTTON3);
  }

  @Test
  void piecesThatWontDownloadAreRedAndHighPriorityOnesAreTinted() throws Exception {
    int[] marks = {
      PiecePriorities.MARK_NONE, PiecePriorities.MARK_SKIP, PiecePriorities.MARK_HIGH,
    };
    HexHivePanel panel = newPanel();
    panel.updateData(
        new HexHivePanel.HexDataAdapter<Object>() {
          @Override
          public void updateData(Object data) {}

          @Override
          public int getTotalHexagonsCount() {
            return 6;
          }

          @Override
          public int getFullHexagonsCount() {
            return 0;
          }

          @Override
          public boolean isFull(int hexOffset) {
            // a downloaded piece shows as downloaded whatever its priority
            return hexOffset >= 3;
          }

          @Override
          public int priorityMark(int hexOffset) {
            return marks[hexOffset % 3];
          }
        });
    BufferedImage bitmap = awaitRender(panel);
    Object props = get(panel, "drawingProperties");
    int[] topFaces = new int[6];
    for (int piece = 0; piece < 6; piece++) {
      Point center = expectedCenter(props, piece);
      // inside the cube's light top face
      topFaces[piece] = bitmap.getRGB(center.x, center.y - SIDE / 2) & 0xffffff;
    }
    for (int a = 0; a < 3; a++) {
      for (int b = a + 1; b < 3; b++) {
        assertNotEquals(topFaces[a], topFaces[b], "marks " + a + " and " + b + " look alike");
      }
    }
    int red = (topFaces[1] >> 16) & 0xff;
    int green = (topFaces[1] >> 8) & 0xff;
    int blue = topFaces[1] & 0xff;
    assertTrue(red > 0xc0 && green < 0x80 && blue < 0x80, "skipped piece is not red");
    for (int piece = 4; piece < 6; piece++) {
      assertEquals(topFaces[3], topFaces[piece], "downloaded piece " + piece + " was tinted");
    }
  }

  private static HexHivePanel newPanel() {
    HexHivePanel panel =
        new HexHivePanel(SIDE, 0x264053, 0xf2f2f2, 0x33b5e5, BACKGROUND, 5, 0, 0, 5, true);
    panel.setSize(300, 400);
    return panel;
  }

  private static HexHivePanel.HexDataAdapter<Object> adapter(int total) {
    return new HexHivePanel.HexDataAdapter<>() {
      @Override
      public void updateData(Object data) {}

      @Override
      public int getTotalHexagonsCount() {
        return total;
      }

      @Override
      public int getFullHexagonsCount() {
        return 0;
      }

      @Override
      public boolean isFull(int hexOffset) {
        return false;
      }
    };
  }

  /** Every cube drawn in one pass onto a canvas wider and taller than {@code bitmap}. */
  private static BufferedImage renderInOnePass(
      HexHivePanel panel, BufferedImage bitmap, double scale) throws Exception {
    Object props = get(panel, "drawingProperties");
    int lastRow = rowIndexForPiece(props, (int) get(props, "numHexs") - 1);
    BufferedImage canvas =
        new BufferedImage(
            bitmap.getWidth() + 40, bitmap.getHeight() + 40, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = canvas.createGraphics();
    graphics.setColor(new Color(BACKGROUND));
    graphics.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    graphics.scale(scale, scale);
    Method drawRows = null;
    for (Method method : HexHivePanel.class.getDeclaredMethods()) {
      if (method.getName().equals("drawRowsToGraphics")) {
        drawRows = method;
      }
    }
    drawRows.setAccessible(true);
    drawRows.invoke(
        panel,
        props,
        get(panel, "latestAdapter"),
        graphics,
        new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight()),
        0,
        lastRow,
        true);
    graphics.dispose();
    return canvas;
  }

  private static int rowIndexForPiece(Object props, int piece) throws Exception {
    Method method = props.getClass().getDeclaredMethod("getRowIndexForPiece", int.class);
    method.setAccessible(true);
    return (int) method.invoke(props, piece);
  }

  private static int rightmostDrawnX(BufferedImage image, int y) {
    int rightmost = -1;
    for (int x = 0; x < image.getWidth(); x++) {
      if ((image.getRGB(x, y) & 0xffffff) != BACKGROUND) {
        rightmost = x;
      }
    }
    return rightmost;
  }

  private static BufferedImage awaitRender(HexHivePanel panel) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    Object renderLock = get(panel, "renderLock");
    while (System.currentTimeMillis() < deadline) {
      synchronized (renderLock) {
        if (!(boolean) get(panel, "renderRunning") && get(panel, "pendingRenderRequest") == null) {
          return (BufferedImage) get(panel, "bitmap");
        }
      }
      Thread.sleep(20);
    }
    throw new AssertionError("render did not finish");
  }

  private static Object get(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
