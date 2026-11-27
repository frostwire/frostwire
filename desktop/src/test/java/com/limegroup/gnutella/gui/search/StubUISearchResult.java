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

package com.limegroup.gnutella.gui.search;

import com.frostwire.licenses.License;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.engines.SearchEngine;
import javax.swing.JPopupMenu;

/** A configurable in-memory search result for tests of the search table and its filters. */
final class StubUISearchResult implements UISearchResult {
  final String filename;
  final String extension;
  final long size;
  final int seeds;
  final String source;
  final String query;
  final long creationTime;
  boolean preliminary;
  SearchEngine engine;
  int downloads;
  boolean lastDownloadPartial;

  StubUISearchResult(String filename, String extension, long size, int seeds, String source) {
    this(filename, extension, size, seeds, source, "query", 0L);
  }

  StubUISearchResult(
      String filename,
      String extension,
      long size,
      int seeds,
      String source,
      String query,
      long creationTime) {
    this.filename = filename;
    this.extension = extension;
    this.size = size;
    this.seeds = seeds;
    this.source = source;
    this.query = query;
    this.creationTime = creationTime;
  }

  SearchResultDataLine line() {
    SearchResultDataLine line = new SearchResultDataLine(new SearchTableColumns());
    line.initialize(this);
    return line;
  }

  private final FileSearchResult searchResult =
      new FileSearchResult() {
        @Override
        public String getFilename() {
          return filename;
        }

        @Override
        public long getSize() {
          return size;
        }

        @Override
        public String getDisplayName() {
          return filename;
        }

        @Override
        public String getDetailsUrl() {
          return "https://example.test/" + filename;
        }

        @Override
        public long getCreationTime() {
          return creationTime;
        }

        @Override
        public String getSource() {
          return source;
        }

        @Override
        public License getLicense() {
          return null;
        }

        @Override
        public String getThumbnailUrl() {
          return null;
        }

        @Override
        public boolean isPreliminary() {
          return preliminary;
        }
      };

  @Override
  public String getFilename() {
    return filename;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Override
  public long getCreationTime() {
    return creationTime;
  }

  @Override
  public String getSource() {
    return source;
  }

  @Override
  public String getExtension() {
    return extension;
  }

  @Override
  public void download(boolean partial) {
    downloads++;
    lastDownloadPartial = partial;
  }

  @Override
  public JPopupMenu createMenu(
      JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
    return popupMenu;
  }

  @Override
  public String getHash() {
    return filename;
  }

  @Override
  public int getSeeds() {
    return seeds;
  }

  @Override
  public SearchEngine getSearchEngine() {
    return engine;
  }

  @Override
  public SearchResult getSearchResult() {
    return searchResult;
  }

  @Override
  public void showSearchResultWebPage(boolean now) {}

  @Override
  public String getDetailsUrl() {
    return "https://example.test/" + filename;
  }

  @Override
  public String getDisplayName() {
    return filename;
  }

  @Override
  public String getQuery() {
    return query;
  }

  @Override
  public void play() {}
}
