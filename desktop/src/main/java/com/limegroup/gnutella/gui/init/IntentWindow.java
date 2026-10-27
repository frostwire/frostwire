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

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executor;
import javax.swing.*;
import org.limewire.util.CommonUtils;

/** State Your Intent. */
final class IntentWindow extends SetupWindow {
  /** Saves the intent file; applySettings runs on the EDT, so the write happens off it. */
  private final Executor writer;

  private boolean setWillNot = false;

  IntentWindow(SetupManager manager) {
    this(manager, BackgroundQueuedExecutorService::schedule);
  }

  IntentWindow(SetupManager manager, Executor writer) {
    super(manager, I18n.tr("State Your Intent"), I18n.tr("One more thing..."));
    this.writer = writer;
  }

  /**
   * Whether the user already confirmed, on an earlier run, that they will not use FrostWire to
   * infringe copyright, in which case this page is not shown. Reads the intent file, so it must not
   * run on the EDT: {@link SetupManager} calls it while deciding the pages, before any is built.
   */
  static boolean isConfirmedOnDisk() {
    return "true".equals(loadProperties().getProperty("willnot"));
  }

  /**
   * Whether the user confirmed on this page. It is only shown when {@link #isConfirmedOnDisk()} is
   * false.
   */
  boolean isConfirmedWillNot() {
    return setWillNot;
  }

  @Override
  protected void createWindow() {
    super.createWindow();
    JPanel innerPanel = new JPanel(new BorderLayout());
    final IntentPanel intentPanel = new IntentPanel();
    innerPanel.add(intentPanel, BorderLayout.CENTER);
    setSetupComponent(innerPanel);
    intentPanel.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
    setNext(null);
    intentPanel.addButtonListener(
        e -> {
          if (intentPanel.hasSelection()) {
            setNext(IntentWindow.this);
            confirmWillNot(intentPanel.isWillNot());
            _manager.enableActions(getAppropriateActions());
          }
        });
  }

  void confirmWillNot(boolean willNot) {
    setWillNot = willNot;
  }

  /**
   * Remembers the confirmation for later runs. Called on the EDT when the user finishes the wizard,
   * so the intent file is written on a background thread.
   */
  @Override
  public void applySettings(boolean loadCoreComponents) {
    if (setWillNot) {
      writer.execute(IntentWindow::saveConfirmedWillNot);
    }
  }

  private static void saveConfirmedWillNot() {
    Properties properties = loadProperties();
    properties.put("willnot", "true");
    try (FileOutputStream out = new FileOutputStream(getPropertiesFile())) {
      properties.store(out, "Started & Ran Versions");
    } catch (IOException ignored) {
      System.out.println(ignored);
    }
  }

  private static Properties loadProperties() {
    Properties properties = new Properties();
    try (FileInputStream fis = new FileInputStream(getPropertiesFile())) {
      properties.load(fis);
    } catch (IOException iox) {
      System.out.println("Could not load properties from property file.");
    }
    return properties;
  }

  private static File getPropertiesFile() {
    return new File(CommonUtils.getUserSettingsDir(), "intent.props");
  }
}
