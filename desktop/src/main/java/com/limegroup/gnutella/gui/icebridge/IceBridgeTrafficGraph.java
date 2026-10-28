/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.icebridge;

import com.frostwire.search.relay.icebridge.IceBridgeTrafficMeter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/**
 * A small time series of IceBridge traffic: bytes received per second in blue and sent in red, over
 * the last {@link IceBridgeTrafficMeter#HISTORY_SIZE} polls (five minutes), newest on the right.
 * Both lines share one scale, the window's peak, so their heights compare.
 *
 * <p>EDT only: {@link #setHistory} replaces the data and repaints.
 */
final class IceBridgeTrafficGraph extends JComponent {
  static final Color RECEIVED = new Color(0x1E, 0x88, 0xE5);
  static final Color SENT = new Color(0xE5, 0x39, 0x35);

  // It sits in the console's status bar between the rates and the totals and takes the width
  // between them; one text line high so the bar keeps its height.
  private static final int WIDTH = 360;
  private static final int MIN_WIDTH = 120;
  private static final int HEIGHT = 16;

  private double[] received = new double[0];
  private double[] sent = new double[0];

  IceBridgeTrafficGraph() {
    setPreferredSize(new Dimension(WIDTH, HEIGHT));
    setMinimumSize(new Dimension(MIN_WIDTH, HEIGHT));
    setOpaque(false);
  }

  void setHistory(double[] received, double[] sent) {
    this.received = received;
    this.sent = sent;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Insets insets = getInsets();
      g2.translate(insets.left, insets.top);
      int w = getWidth() - insets.left - insets.right - 1;
      int h = getHeight() - insets.top - insets.bottom - 1;
      double peak = Math.max(peak(received), peak(sent));
      if (peak <= 0) {
        return;
      }
      g2.setStroke(new BasicStroke(1.5f));
      g2.setColor(RECEIVED);
      g2.draw(line(received, peak, w, h));
      g2.setColor(SENT);
      g2.draw(line(sent, peak, w, h));
    } finally {
      g2.dispose();
    }
  }

  /**
   * The series as a polyline across the full history width, its newest point at the right edge, so
   * a history that is still filling up grows in from the right.
   */
  static Path2D line(double[] values, double peak, int width, int height) {
    Path2D path = new Path2D.Double();
    double step = width / (double) (IceBridgeTrafficMeter.HISTORY_SIZE - 1);
    int first = IceBridgeTrafficMeter.HISTORY_SIZE - values.length;
    for (int i = 0; i < values.length; i++) {
      double x = (first + i) * step;
      // One pixel of margin so the 1.5 px stroke isn't clipped at the top and bottom.
      double y = height - 1 - (height - 2) * Math.max(0, values[i]) / peak;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    return path;
  }

  private static double peak(double[] values) {
    double max = 0;
    for (double v : values) {
      max = Math.max(max, v);
    }
    return max;
  }
}
