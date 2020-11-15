/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
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
package com.frostwire.gui.library;

import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.gui.player.MediaPlayer;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.NameHolder;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class LibraryPlaylistsTableDataLine extends AbstractLibraryTableDataLine<PlaylistItem> {
    /**
     * sort index column
     */
    static final int SORT_INDEX_IDX = 0;
    static final int ACTIONS_IDX = 1;
    /**
     * Starred column
     */
    static final int STARRED_IDX = 2;
    /**
     * Size column (in bytes)
     */
    private static final int SIZE_IDX = 11;
    /**
     * TYPE column
     */
    private static final int TYPE_IDX = 12;
    private static final LimeTableColumn SORT_INDEX_COLUMN = new LimeTableColumn(SORT_INDEX_IDX, "PLAYLIST_TABLE_SORT_INDEX", I18n.tr("Index"), 30, true, false, false, PlaylistItemStringProperty.class);
    private static final LimeTableColumn ACTIONS_COLUMN = new LimeTableColumn(ACTIONS_IDX, "PLAYLIST_TABLE_ACTIONS", I18n.tr("Actions"), 36, true, false, false, LibraryActionsHolder.class);
    private static final LimeTableColumn STARRED_COLUMN = new LimeTableColumn(STARRED_IDX, "PLAYLIST_TABLE_STARRED", I18n.tr("Starred"), 20, true, false, false, PlaylistItemStarProperty.class);
    /**
     * Title column
     */
    private static final int TITLE_IDX = 3;
    private static final LimeTableColumn TITLE_COLUMN = new LimeTableColumn(TITLE_IDX, "PLAYLIST_TABLE_TITLE", I18n.tr("Title"), 80, true, NameHolder.class);
    /**
     * Artist column
     */
    private static final int ARTIST_IDX = 4;
    private static final LimeTableColumn ARTIST_COLUMN = new LimeTableColumn(ARTIST_IDX, "PLAYLIST_TABLE_ARTIST", I18n.tr("Artist"), 80, true, PlaylistItemStringProperty.class);
    /**
     * Length column (in hour:minutes:seconds format)
     */
    private static final int LENGTH_IDX = 5;
    private static final LimeTableColumn LENGTH_COLUMN = new LimeTableColumn(LENGTH_IDX, "PLAYLIST_TABLE_LENGTH", I18n.tr("Length"), 150, true, PlaylistItemStringProperty.class);
    /**
     * Album column
     */
    private static final int ALBUM_IDX = 6;
    private static final LimeTableColumn ALBUM_COLUMN = new LimeTableColumn(ALBUM_IDX, "PLAYLIST_TABLE_ALBUM", I18n.tr("Album"), 120, true, PlaylistItemStringProperty.class);
    /**
     * Track column
     */
    private static final int TRACK_IDX = 7;
    private static final LimeTableColumn TRACK_COLUMN = new LimeTableColumn(TRACK_IDX, "PLAYLIST_TABLE_TRACK", I18n.tr("Track"), 20, true, PlaylistItemTrackProperty.class);
    /**
     * Genre column
     */
    private static final int GENRE_IDX = 8;
    private static final LimeTableColumn GENRE_COLUMN = new LimeTableColumn(GENRE_IDX, "PLAYLIST_TABLE_GENRE", I18n.tr("Genre"), 80, true, PlaylistItemStringProperty.class);
    /**
     * Bitrate column info
     */
    private static final int BITRATE_IDX = 9;
    private static final LimeTableColumn BITRATE_COLUMN = new LimeTableColumn(BITRATE_IDX, "PLAYLIST_TABLE_BITRATE", I18n.tr("Bitrate"), 60, true, PlaylistItemBitRateProperty.class);
    /**
     * Comment column info
     */
    private static final int COMMENT_IDX = 10;
    private static final LimeTableColumn COMMENT_COLUMN = new LimeTableColumn(COMMENT_IDX, "PLAYLIST_TABLE_COMMENT", I18n.tr("Comment"), 20, false, PlaylistItemStringProperty.class);
    private static final LimeTableColumn SIZE_COLUMN = new LimeTableColumn(SIZE_IDX, "PLAYLIST_TABLE_SIZE", I18n.tr("Size"), 80, false, PlaylistItemStringProperty.class);
    private static final LimeTableColumn TYPE_COLUMN = new LimeTableColumn(TYPE_IDX, "PLAYLIST_TABLE_TYPE", I18n.tr("Type"), 40, true, PlaylistItemStringProperty.class);
    /**
     * Year column
     */
    private static final int YEAR_IDX = 13;
    private static final LimeTableColumn YEAR_COLUMN = new LimeTableColumn(YEAR_IDX, "PLAYLIST_TABLE_YEAR", I18n.tr("Year"), 30, false, PlaylistItemStringProperty.class);
    /**
     * Total number of columns
     */
    private static final int NUMBER_OF_COLUMNS = 14;
    /**
     * Coverts the size of the PlayListItem into readable form post-fixed with
     * Kb or Mb
     */
    private SizeHolder sizeHolder;
    private boolean exists;
    private String bitrate;
    private NameHolder nameCell;
    private LibraryActionsHolder actionsHolder;

    /**
     * Number of columns
     */
    public int getColumnCount() {
        return NUMBER_OF_COLUMNS;
    }

    /**
     * Sets up the DataLine for use with the playlist.
     */
    public void initialize(PlaylistItem item) {
        super.initialize(item);
        sizeHolder = new SizeHolder(item.getFileSize());
        exists = new File(item.getFilePath()).exists();
        bitrate = initializer.getTrackBitrate();
        if (bitrate != null && bitrate.length() > 0 && !bitrate.endsWith(" kbps")) {
            bitrate = bitrate + " kbps";
        }
        this.nameCell = new NameHolder(initializer.getTrackTitle());
        this.actionsHolder = new LibraryActionsHolder(this, false);
    }

    /**
     * Returns the value for the specified index.
     */
    public Object getValueAt(int idx) {
        boolean playing = isPlaying();
        switch (idx) {
            case SORT_INDEX_IDX:
                return new PlaylistItemIntProperty(this, initializer.getSortIndex(), exists);
            case ACTIONS_IDX:
                actionsHolder.setPlaying(playing);
                return actionsHolder;
            case STARRED_IDX:
                return new PlaylistItemStarProperty(this, exists);
            case ALBUM_IDX:
                return new PlaylistItemStringProperty(this, initializer.getTrackAlbum(), exists);
            case ARTIST_IDX:
                return new PlaylistItemStringProperty(this, initializer.getTrackArtist(), exists);
            case BITRATE_IDX:
                return new PlaylistItemBitRateProperty(this, bitrate, exists);
            case COMMENT_IDX:
                return new PlaylistItemStringProperty(this, initializer.getTrackComment(), exists);
            case GENRE_IDX:
                return new PlaylistItemStringProperty(this, initializer.getTrackGenre(), exists);
            case LENGTH_IDX:
                return new PlaylistItemStringProperty(this, LibraryUtils.getSecondsInDDHHMMSS((int) initializer.getTrackDurationInSecs()), exists);
            case SIZE_IDX:
                return new PlaylistItemStringProperty(this, sizeHolder.toString(), exists);
            case TITLE_IDX:
                return nameCell;
            case TRACK_IDX:
                return new PlaylistItemTrackProperty(this, initializer.getTrackNumber(), exists);
            case TYPE_IDX:
                return new PlaylistItemStringProperty(this, initializer.getFileExtension(), exists);
            case YEAR_IDX:
                return new PlaylistItemStringProperty(this, initializer.getTrackYear(), exists);
        }
        return null;
    }

    private boolean isPlaying() {
        return initializer != null && MediaPlayer.instance().isThisBeingPlayed(initializer);
    }

    /**
     * Return the table column for this index.
     */
    public LimeTableColumn getColumn(int idx) {
        switch (idx) {
            case SORT_INDEX_IDX:
                return SORT_INDEX_COLUMN;
            case ACTIONS_IDX:
                return ACTIONS_COLUMN;
            case STARRED_IDX:
                return STARRED_COLUMN;
            case ALBUM_IDX:
                return ALBUM_COLUMN;
            case ARTIST_IDX:
                return ARTIST_COLUMN;
            case BITRATE_IDX:
                return BITRATE_COLUMN;
            case COMMENT_IDX:
                return COMMENT_COLUMN;
            case GENRE_IDX:
                return GENRE_COLUMN;
            case LENGTH_IDX:
                return LENGTH_COLUMN;
            case SIZE_IDX:
                return SIZE_COLUMN;
            case TITLE_IDX:
                return TITLE_COLUMN;
            case TRACK_IDX:
                return TRACK_COLUMN;
            case TYPE_IDX:
                return TYPE_COLUMN;
            case YEAR_IDX:
                return YEAR_COLUMN;
        }
        return null;
    }

    public boolean isClippable(int idx) {
        return false;
    }

    public int getTypeAheadColumn() {
        return STARRED_IDX;
    }

    public boolean isDynamic(int idx) {
        return false;
    }

    /**
     * @return the PlayListItem for this table row
     */
    PlaylistItem getPlayListItem() {
        return initializer;
    }

    /**
     * Creates a tool tip for each row of the playlist. Tries to grab any information
     * that was extracted from the Meta-Tag or passed in to the PlaylistItem as
     * a property map
     */
    public String[] getToolTipArray(int col) {
        List<String> list = new ArrayList<>();
        if (!StringUtils.isNullOrEmpty(initializer.getTrackTitle(), true)) {
            list.add(I18n.tr("Title") + ": " + initializer.getTrackTitle());
        }
        if (!StringUtils.isNullOrEmpty(initializer.getTrackNumber(), true)) {
            list.add(I18n.tr("Track") + ": " + initializer.getTrackNumber());
        }
        list.add(I18n.tr("Duration") + ": " + LibraryUtils.getSecondsInDDHHMMSS((int) initializer.getTrackDurationInSecs()));
        if (!StringUtils.isNullOrEmpty(initializer.getTrackArtist(), true)) {
            list.add(I18n.tr("Artist") + ": " + initializer.getTrackArtist());
        }
        if (!StringUtils.isNullOrEmpty(initializer.getTrackAlbum(), true)) {
            list.add(I18n.tr("Album") + ": " + initializer.getTrackAlbum());
        }
        if (!StringUtils.isNullOrEmpty(initializer.getTrackGenre(), true)) {
            list.add(I18n.tr("Genre") + ": " + initializer.getTrackGenre());
        }
        if (!StringUtils.isNullOrEmpty(initializer.getTrackYear(), true)) {
            list.add(I18n.tr("Year") + ": " + initializer.getTrackYear());
        }
        if (!StringUtils.isNullOrEmpty(initializer.getTrackComment(), true)) {
            list.add(I18n.tr("Comment") + ": " + initializer.getTrackComment());
        }
        if (list.size() == 1) {
            if (!StringUtils.isNullOrEmpty(initializer.getFileName(), true)) {
                list.add(I18n.tr("File") + ": " + initializer.getFileName());
            }
            if (!StringUtils.isNullOrEmpty(initializer.getFilePath(), true)) {
                list.add(I18n.tr("Folder") + ": " + FilenameUtils.getPath(initializer.getFilePath()));
            }
            if (!StringUtils.isNullOrEmpty(initializer.getTrackBitrate(), true)) {
                list.add(I18n.tr("Bitrate") + ": " + initializer.getTrackBitrate());
            }
        }
        return list.toArray(new String[0]);
    }

    public File getFile() {
        return new File(initializer.getFilePath());
    }
}
