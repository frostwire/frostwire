/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import org.limewire.setting.SettingsGroupManager;

/**
 * Saves every settings group (frostwire.props and friends) off the EDT. UI handlers that persist
 * settings call {@link #request()} instead of {@code SettingsGroupManager.instance().save()}, which
 * writes and flushes files on the calling thread. Saves run on the serial {@link
 * BackgroundQueuedExecutorService} queue and bursts coalesce. The shutdown thread still saves
 * synchronously on exit (LifecycleManagerImpl), so nothing queued here is lost by quitting.
 */
public final class BackgroundSettingsSave {
  private static final CoalescingBackgroundSave SAVE =
      new CoalescingBackgroundSave(
          () -> SettingsGroupManager.instance().save(), BackgroundQueuedExecutorService::schedule);

  private BackgroundSettingsSave() {}

  /** Saves all settings soon on a background thread; returns immediately. */
  public static void request() {
    SAVE.request();
  }
}
