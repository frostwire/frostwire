/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.gui.init;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Greedy word wrapping for plain {@link javax.swing.JLabel}s. Swing HTML labels only wrap when
 * given a CSS width, and CSS pixels do not match screen pixels, so the wizard breaks lines itself
 * from real font metrics and hands the label explicit line breaks.
 */
public final class WrappedText {

  private WrappedText() {}

  /**
   * Split {@code text} into lines no wider than {@code maxWidth}. A single word wider than the
   * limit is broken by character so nothing can overflow.
   *
   * @param width measures a string in pixels, normally {@code FontMetrics::stringWidth}
   */
  public static List<String> wrap(String text, ToIntFunction<String> width, int maxWidth) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isEmpty()) {
      return lines;
    }
    StringBuilder line = new StringBuilder();
    for (String word : text.trim().split("\\s+")) {
      String candidate = line.length() == 0 ? word : line + " " + word;
      if (width.applyAsInt(candidate) <= maxWidth) {
        line.setLength(0);
        line.append(candidate);
        continue;
      }
      if (line.length() > 0) {
        lines.add(line.toString());
        line.setLength(0);
      }
      String rest = word;
      while (width.applyAsInt(rest) > maxWidth && rest.length() > 1) {
        int cut = rest.length() - 1;
        while (cut > 1 && width.applyAsInt(rest.substring(0, cut)) > maxWidth) {
          cut--;
        }
        lines.add(rest.substring(0, cut));
        rest = rest.substring(cut);
      }
      line.append(rest);
    }
    if (line.length() > 0) {
      lines.add(line.toString());
    }
    return lines;
  }

  /** HTML for a label that shows exactly these lines. */
  public static String html(List<String> lines) {
    StringBuilder html = new StringBuilder("<html>");
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0) {
        html.append("<br>");
      }
      html.append(lines.get(i).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
    }
    return html.append("</html>").toString();
  }
}
