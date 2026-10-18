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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.theme.IconRepainter;
import com.frostwire.util.SafeText;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.tables.NameHolder;
import javax.swing.Icon;

/**
 * @author gubatron
 * @author aldenml
 */
final class SearchResultNameHolder extends NameHolder {
  private final String displayName;
  private static Icon downloadedIcon;

  private final String query;
  private final String downloadKey;

  SearchResultNameHolder(final UISearchResult sr) {
    super(SafeText.sanitize(sr.getDisplayName()));
    this.displayName = sr.getDisplayName();
    this.query = sr.getQuery();
    this.downloadKey = DownloadedSearchResults.keyOf(sr);
  }

  String getQuery() {
    return query;
  }

  /** Results the user already started downloading, in this or an earlier search, get an arrow. */
  @Override
  public Icon getIcon() {
    if (!DownloadedSearchResults.instance().mightContain(downloadKey)) {
      return null;
    }
    if (downloadedIcon == null) {
      downloadedIcon =
          IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("downloading_small"));
    }
    return downloadedIcon;
  }

  public String toString() {
    return displayName;
  }
}
