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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

/**
 * A splash whose build threw (a ConcurrentModificationException in Nimbus,
 * frostwire/frostwire#1539) kept null fields, and every later status update failed on them (Icebase
 * #322beebdff0c, #bfa37395b7e5, #f38c6738a224). The build failure still reaches the caller of
 * begin(); the later calls are skipped.
 */
class SplashWindowFailedInitializeTest {

  @Test
  void callsAfterAFailedBuildAreSkipped() {
    ConcurrentModificationException buildFailure = new ConcurrentModificationException();
    SplashWindow splash =
        new SplashWindow(
            () -> {
              throw buildFailure;
            });

    assertSame(buildFailure, assertThrows(ConcurrentModificationException.class, splash::begin));

    assertDoesNotThrow(() -> splash.setStatusText("Loading Menus..."));
    assertDoesNotThrow(() -> splash.setVisible(false));
    assertDoesNotThrow(splash::toBack);
    assertDoesNotThrow(splash::dispose);
    assertFalse(splash.isShowing());
  }
}
