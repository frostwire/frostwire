/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.IceBridgeTrafficMeter;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IceBridgeTrafficGraphTest {

  @Test
  void newestPointIsAtTheRightEdgeAndThePeakAtTheTop() {
    double[] values = {0, 50, 100};
    List<double[]> points = points(IceBridgeTrafficGraph.line(values, 100, 299, 26));
    assertEquals(3, points.size());
    // A history still filling up grows in from the right: 3 of 300 slots, one pixel apart.
    assertEquals(297, points.get(0)[0], 0.001);
    assertEquals(299, points.get(2)[0], 0.001);
    assertEquals(25, points.get(0)[1], 0.001); // zero at the bottom, inside the border
    assertEquals(1, points.get(2)[1], 0.001); // the peak at the top, inside the border
  }

  @Test
  void drawsReceivedInBlueAndSentInRed() {
    int n = IceBridgeTrafficMeter.HISTORY_SIZE;
    double[] received = new double[n];
    double[] sent = new double[n];
    for (int i = 0; i < n; i++) {
      received[i] = 1_000;
      sent[i] = 200;
    }
    IceBridgeTrafficGraph graph = new IceBridgeTrafficGraph();
    graph.setSize(180, 26);
    graph.setHistory(received, sent);
    BufferedImage image = new BufferedImage(180, 26, BufferedImage.TYPE_INT_ARGB);
    graph.paint(image.getGraphics());
    assertTrue(contains(image, IceBridgeTrafficGraph.RECEIVED), "no blue received line");
    assertTrue(contains(image, IceBridgeTrafficGraph.SENT), "no red sent line");
  }

  private static boolean contains(BufferedImage image, java.awt.Color color) {
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if ((image.getRGB(x, y) & 0xFFFFFF) == (color.getRGB() & 0xFFFFFF)) {
          return true;
        }
      }
    }
    return false;
  }

  private static List<double[]> points(java.awt.Shape shape) {
    List<double[]> out = new ArrayList<>();
    double[] coords = new double[6];
    for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
      it.currentSegment(coords);
      out.add(new double[] {coords[0], coords[1]});
    }
    return out;
  }
}
