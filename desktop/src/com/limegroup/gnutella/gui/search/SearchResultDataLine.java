/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.IconManager;
import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.util.Date;

/**
 * A single line of a search result.
 */
public final class SearchResultDataLine extends AbstractDataLine<UISearchResult> {
    /**
     * The SearchTableColumns.
     */
    private final SearchTableColumns COLUMNS;
    /**
     * The SearchResult that created this particular line.
     */
    private UISearchResult RESULT;
    /**
     * The media type of this document.
     */
    private NamedMediaType _mediaType;
    /**
     * The date this was added to the network.
     */
    private Date addedOn;
    private SearchResultActionsHolder actionsHolder;
    private SearchResultNameHolder name;
    private String seeds;
    private Icon icon;
    private SizeHolder size;
    private SourceHolder source;

    public SearchResultDataLine(SearchTableColumns stc) {
        COLUMNS = stc;
    }

    /**
     * Initializes this line with the specified search result.
     */
    public void initialize(UISearchResult sr) {
        super.initialize(sr);
        RESULT = sr;
        _mediaType = NamedMediaType.getFromExtension(getExtension());
        addedOn = sr.getCreationTime() > 0 ? new Date(sr.getCreationTime()) : null;
        actionsHolder = new SearchResultActionsHolder(sr);
        name = new SearchResultNameHolder(sr);
        seeds = RESULT.getSeeds() <= 0 || !(RESULT instanceof TorrentUISearchResult) ? "" : String.valueOf(RESULT.getSeeds());
        icon = getIcon();
        size = new SizeHolder(getSize());
        source = new SourceHolder(RESULT);
    }

    /**
     * Updates cached data about this line.
     */
    public void update() {
    }

    public String toString() {
        return getFilename() + " (" + getSeeds() + ")";
    }

    private boolean isDownloading() {
        return RESULT.getHash() != null && BTDownloadMediator.instance().isDownloading(RESULT.getHash());
    }

    /**
     * Returns the NamedMediaType.
     */
    NamedMediaType getNamedMediaType() {
        return _mediaType;
    }

    /**
     * Returns the icon.
     */
    private Icon getIcon() {
        //gubs: seems like this didn't fly
        //maybe the icon isn't refreshed.
        //see MetadataModel.addProperties()
        if (isDownloading()) {
            return GUIMediator.getThemeImage("downloading");
        }
        String ext = FilenameUtils.getExtension(getFilename());
        return IconManager.instance().getIconForExtension(ext);
    }

    /**
     * Returns the extension of this result.
     */
    String getExtension() {
        return RESULT.getExtension();
    }

    /**
     * Returns this filename, as passed to the constructor.  Limitation:
     * if the original filename was "a.", the returned value will be
     * "a".
     */
    public String getFilename() {
        return RESULT.getFilename();
    }

    public String getDisplayName() {
        return RESULT.getDisplayName();
    }

    /**
     * Gets the size of this TableLine.
     */
    public double getSize() {
        return RESULT.getSize();
    }

    /**
     * Gets the LimeTableColumn for this column.
     */
    public LimeTableColumn getColumn(int idx) {
        return COLUMNS.getColumn(idx);
    }

    /**
     * Returns the number of columns.
     */
    public int getColumnCount() {
        return SearchTableColumns.COLUMN_COUNT;
    }

    /**
     * Determines if the column is dynamic.
     */
    public boolean isDynamic(int idx) {
        return false;
    }

    /**
     * Determines if the column is clippable.
     */
    public boolean isClippable(int idx) {
        switch (idx) {
            case SearchTableColumns.COUNT_IDX:
            case SearchTableColumns.TYPE_IDX:
                return false;
            default:
                return true;
        }
    }

    public int getTypeAheadColumn() {
        return SearchTableColumns.NAME_IDX;
    }

    /**
     * Gets the value for the specified idx.
     */
    public Object getValueAt(int index) {
        switch (index) {
            case SearchTableColumns.ACTIONS_IDX:
                return actionsHolder;
            case SearchTableColumns.COUNT_IDX:
                return seeds;
            case SearchTableColumns.TYPE_IDX:
                return icon;
            case SearchTableColumns.NAME_IDX:
                return name;
            case SearchTableColumns.SIZE_IDX:
                return size;
            case SearchTableColumns.SOURCE_IDX:
                return source;
            case SearchTableColumns.ADDED_IDX:
                return addedOn;
            case SearchTableColumns.EXTENSION_IDX:
                return getExtension();
            default:
                return null;
        }
    }

    /**
     * Returns <code>true</code> if <code>this</code> {@link UISearchResult}
     * is the same kind as <code>line</code>'s, e.g. one from gnutella and
     * one from gnutella. Currently we compare classes.
     *
     * @param line line to which we compare
     * @return <code>true</code> if <code>this</code> {@link UISearchResult}
     * is the same kind as <code>line</code>'s, e.g. one from
     * gnutella and one from gnutella
     */
    final boolean isSameKindAs(SearchResultDataLine line) {
        return getSearchResult().getClass().equals(line.getSearchResult().getClass());
    }

    /**
     * Returns the underlying search result.
     *
     * @return the underlying search result
     */
    final UISearchResult getSearchResult() {
        return RESULT;
    }

    public int getSeeds() {
        return RESULT.getSeeds();
    }

    public String getHash() {
        return RESULT.getHash();
    }

    public SearchEngine getSearchEngine() {
        return RESULT.getSearchEngine();
    }
}