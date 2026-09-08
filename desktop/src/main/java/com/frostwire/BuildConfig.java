/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire;

import org.limewire.util.CommonUtils;

/** Desktop build-mode constants. */
public final class BuildConfig {
  public static final boolean DEBUG =
      CommonUtils.isRunningFromGradle() || CommonUtils.isRunningFromIntelliJ();

  private BuildConfig() {}
}
