/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.util.MacOSXUtils;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Adds FrostWire to, or removes it from, the programs the OS starts when the user logs in. */
public final class LoginItem {
  private LoginItem() {}

  /**
   * Adds or removes FrostWire on the background queue. On Windows that checks the Start Menu folder
   * and copies or deletes a shortcut in it, and on macOS it calls into the OS, so the setup wizard
   * and the Options pane, which apply the choice on the EDT, must not wait for it: a slow disk
   * froze the wizard on the same kind of file check (Icebase #9325f45dd41f).
   */
  public static void setOffEdt(boolean allow) {
    set(allow, BackgroundQueuedExecutorService::schedule, LoginItem::setNow);
  }

  static void set(boolean allow, Executor executor, Consumer<Boolean> setter) {
    executor.execute(() -> setter.accept(allow));
  }

  /** Adds or removes FrostWire on the calling thread, which must not be the EDT. */
  private static void setNow(boolean allow) {
    if (OSUtils.isMacOSX()) {
      MacOSXUtils.setLoginStatus(allow);
    } else if (WindowsUtils.isLoginStatusAvailable()) {
      WindowsUtils.setLoginStatus(allow);
    }
  }
}
