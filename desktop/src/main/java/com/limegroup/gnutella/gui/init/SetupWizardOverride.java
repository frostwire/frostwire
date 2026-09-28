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

/**
 * QA switch that shows the setup wizard on every launch, as it appears on first run or after an
 * update, without wiping any settings.
 *
 * <p>Enable with any of:
 *
 * <ul>
 *   <li>{@code FW_FORCE_SETUP_WIZARD=1 ./gradlew run} (the environment reaches the app)
 *   <li>{@code -Dfw.force.setup.wizard=true} on the JVM command line
 * </ul>
 *
 * A value of {@code false} or {@code 0} disables it.
 */
public final class SetupWizardOverride {

  static final String PROPERTY = "fw.force.setup.wizard";
  static final String ENVIRONMENT_VARIABLE = "FW_FORCE_SETUP_WIZARD";

  private SetupWizardOverride() {}

  public static boolean isForced() {
    return isForced(System.getProperty(PROPERTY), System.getenv(ENVIRONMENT_VARIABLE));
  }

  public static boolean isForced(String propertyValue, String environmentValue) {
    return enabled(propertyValue) || enabled(environmentValue);
  }

  /** A flag given without a value ({@code -Dfw.force.setup.wizard}) counts as on. */
  private static boolean enabled(String value) {
    if (value == null) {
      return false;
    }
    String v = value.trim();
    return !(v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("no"));
  }
}
