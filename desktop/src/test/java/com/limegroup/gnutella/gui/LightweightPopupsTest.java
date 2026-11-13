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

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Popups and tooltips stay lightweight (Swing's default). FrostWire forced them heavyweight to stay
 * above the SWT Browser and the MPlayer canvas, both long gone; as native windows every tooltip and
 * menu waited on Windows' toolkit thread (Icebase #88e9eae4d5de and others,
 * frostwire/frostwire#1552).
 */
class LightweightPopupsTest {

  @Test
  void nothingTurnsLightweightPopupsOff() throws IOException {
    List<String> offenders;
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      offenders =
          files
              .filter(p -> p.toString().endsWith(".java"))
              .filter(
                  p -> {
                    try {
                      String source = Files.readString(p).replaceAll("\\s+", "");
                      return source.contains("setLightWeightPopupEnabled(false)")
                          || source.contains("setDefaultLightWeightPopupEnabled(false)");
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  })
              .map(Path::toString)
              .toList();
    }

    assertEquals(List.of(), offenders, "heavyweight popups forced in");
  }
}
