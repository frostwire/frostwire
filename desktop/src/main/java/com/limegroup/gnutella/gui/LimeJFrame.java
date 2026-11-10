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

package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import javax.swing.*;
import org.limewire.util.SystemUtils;

/** A JFrame that uses LimeWire's icon. */
public class LimeJFrame extends JFrame {
  public LimeJFrame() throws HeadlessException {
    super();
    initialize();
  }

  public LimeJFrame(GraphicsConfiguration gc) {
    super(gc);
    initialize();
  }

  public LimeJFrame(String title, GraphicsConfiguration gc) {
    super(title, gc);
    initialize();
  }

  public LimeJFrame(String title) throws HeadlessException {
    super(title);
    initialize();
  }

  private static List<JPopupMenu> getPopups() {
    MenuSelectionManager msm = MenuSelectionManager.defaultManager();
    MenuElement[] p = msm.getSelectedPath();
    List<JPopupMenu> list = new ArrayList<>(p.length);
    for (MenuElement element : p) {
      if (element instanceof JPopupMenu) {
        list.add((JPopupMenu) element);
      }
    }
    return list;
  }

  private void initialize() {
    if (OSUtils.isMacOSX()) {
      setupPopupHide();
    }
    // Defer icon loading to avoid EDT blocking with file I/O
    SwingUtilities.invokeLater(this::loadWindowIcon);
  }

  private void loadWindowIcon() {
    try {
      ImageIcon limeIcon = GUIMediator.getThemeImage(GUIConstants.FROSTWIRE_64x64_ICON);
      setIconImage(limeIcon.getImage());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Overrides addNotify() to change to a platform specific icon right afterwards.
  @Override
  public void addNotify() {
    super.addNotify();
    setPlatformIcon(this, BackgroundQueuedExecutorService::schedule, SystemUtils::setWindowIcon);
  }

  /**
   * Replaces the Swing icon of a frame that has its native window with the platform's own, in the
   * background. On Windows that is a native call that finds the window handle, reads the icons out
   * of FrostWire.exe the first time and sends the window two messages: none of it needs the EDT,
   * and it froze the window as it appeared (frostwire/frostwire#1522). A frame disposed before the
   * call runs just gets no icon.
   *
   * <p>Thread-safe: only hands the call to {@code background}.
   */
  static void setPlatformIcon(
      Component frame, Executor background, BiConsumer<Component, File> setIcon) {
    background.execute(() -> setIcon.accept(frame, GUIConstants.FROSTWIRE_EXE_FILE));
  }

  private void setupPopupHide() {
    addWindowListener(
        new WindowAdapter() {
          public void windowDeactivated(WindowEvent e) {
            for (JPopupMenu menu : getPopups()) {
              menu.setVisible(false);
            }
          }
        });
  }
}
