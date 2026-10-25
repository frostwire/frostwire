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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

/** Renders the pieces hive off-screen and inspects its backing bitmap. */
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
  void backingBitmapIsRenderedAtDeviceScale() throws Exception {
    HexHivePanel panel = newPanel();
    set(panel, "renderScale", 2.0);
    panel.updateData(adapter(40));
    BufferedImage bitmap = awaitRender(panel);
    Object props = get(panel, "drawingProperties");
    assertEquals((int) get(props, "width") * 2, bitmap.getWidth());
    assertEquals((int) get(props, "height") * 2, bitmap.getHeight());
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
