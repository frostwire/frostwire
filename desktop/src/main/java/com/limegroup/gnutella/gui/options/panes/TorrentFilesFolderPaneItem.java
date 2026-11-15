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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.gui.bittorrent.TorrentFilesFolderComponent;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorrentFilesFolderPaneItem extends AbstractPaneItem {
  private static final String TITLE = I18n.tr("Torrent Files Folder");
  private final TorrentFilesFolderComponent COMPONENT;

  public TorrentFilesFolderPaneItem() {
    super(TITLE, I18n.tr("Choose the folder where .torrent files will be saved to"));
    COMPONENT = new TorrentFilesFolderComponent(false);
    add(COMPONENT);
  }

  @Override
  public void initOptions() {
    // nothing the component does it.
  }

  @Override
  public boolean applyOptions() throws IOException {
    return applyIfChanged(
        SharingSettings.TORRENTS_DIR_SETTING.getValueAsString(),
        COMPONENT,
        GUIMediator::showError,
        this::updateDefaultTorrentFilesFolder);
  }

  /**
   * Applies the folder typed in {@code component} if it differs from {@code currentFolder}. Options
   * are applied on the EDT and every Apply reaches every pane, so the folder is only checked on
   * disk when the user actually changed it.
   *
   * @return whether the folder changed
   * @throws IOException if the changed folder is not usable; the error was shown
   */
  static boolean applyIfChanged(
      String currentFolder,
      TorrentFilesFolderComponent component,
      Consumer<String> showError,
      Consumer<File> applyFolder)
      throws IOException {
    if (currentFolder.equals(component.getTorrentFilesFolderPath())) {
      return false;
    }
    if (!component.isTorrentFilesFolderPathValid(true)) {
      showError.accept(TorrentFilesFolderComponent.getError());
      throw new IOException();
    }
    applyFolder.accept(new File(component.getTorrentFilesFolderPath()));
    return true;
  }

  private void updateDefaultTorrentFilesFolder(File newTorrentFilesFolder) {
    SharingSettings.TORRENTS_DIR_SETTING.setValue(newTorrentFilesFolder);
  }

  @Override
  public boolean isDirty() {
    return !SharingSettings.TORRENTS_DIR_SETTING
        .getValueAsString()
        .equals(COMPONENT.getTorrentFilesFolderPath());
  }
}
