/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import com.frostwire.bittorrent.BTInfoAdditionalMetadataHolder;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.player.MediaPlayer;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconManager;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import com.limegroup.gnutella.gui.tables.NameHolder;
import com.limegroup.gnutella.gui.tables.SizeHolder;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * This class acts as a single line containing all
 * the necessary Library info.
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public final class LibraryFilesTableDataLine extends AbstractLibraryTableDataLine<File> {

    static final int ACTIONS_IDX = 0;

    /**
     * Constant for the column with the wi-fi shared state.
     */
    //static final int SHARE_IDX = 1;

    /**
     * Constant for the column with the icon of the file.
     */
    static final int ICON_IDX = 1;

    /**
     * Constant for the column with the name of the file.
     */
    static final int NAME_IDX = 2;

    /**
     * Constant for the column storing the size of the file.
     */
    static final int SIZE_IDX = 3;

    /**
     * Constant for the column storing the file type (extension or more
     * more general type) of the file.
     */
    static final int TYPE_IDX = 4;

    /**
     * Constant for the column storing the file's path
     */
    static final int PATH_IDX = 5;

    /**
     * Constant for the column indicating the mod time of a file.
     */
    static final int MODIFICATION_TIME_IDX = 6;
    
    static final int PAYMENT_OPTIONS_IDX = 7;
    
    static final int LICENSE_IDX = 8;

    private static final SizeHolder ZERO_SIZED_HOLDER = new SizeHolder(0);

    /**
     * Add the columns to static array _in the proper order_.
     * The *_IDX variables above need to match the corresponding
     * column's position in this array.
     */
    private static LimeTableColumn[] ltColumns;

    /** Variable for the name */
    private String _name;

    /** Variable for the type */
    private String _type;

    /** Variable for the size */
    private long _size;

    /** Cached SizeHolder */
    private SizeHolder _sizeHolder;

    /** Variable for the path */
    private String _path;

    /**
     * The model this is being displayed on
     */
    private final LibraryFilesTableModel _model;

    /**
     * Whether or not the icon has been loaded.
     */
    private boolean _iconLoaded = false;

    /**
     * Whether or not the icon has been scheduled to load.
     */
    private boolean _iconScheduledForLoad = false;

    public LibraryFilesTableDataLine(LibraryFilesTableModel ltm) {
        super();
        _model = ltm;
    }

    public int getColumnCount() {
        return getLimeTableColumns().length;
    }

    private Date lastModified;

    private String license;

    private PaymentOptions paymentOptions;

    /**
     * Initialize the object.
     * It will fail if not given a FileDesc or a File
     * (File is retained for compatibility with the Incomplete folder)
     */
    public void initialize(File file) {
        super.initialize(file);

        String fullPath = file.getPath();
        try {
            fullPath = file.getCanonicalPath();
        } catch (IOException ioe) {
        }

        _name = initializer.getName();
        _type = "";

        if (!file.isDirectory()) {
            //_isDirectory = false;
            int index = _name.lastIndexOf(".");
            int index2 = fullPath.lastIndexOf(File.separator);
            _path = fullPath.substring(0, index2);
            if (index != -1 && index != 0) {
                _type = _name.substring(index + 1);
                _name = _name.substring(0, index);
            }
        } else {
            _path = fullPath;
            //_isDirectory = true;
        }

        // only load file sizes, do nothing for directories
        // directories implicitly set SizeHolder to null and display nothing
        if (initializer.isFile()) {
            _size = initializer.length();
            _sizeHolder = new SizeHolder(_size);
        } else {
            _sizeHolder = ZERO_SIZED_HOLDER;
        }

        this.lastModified = new Date(initializer.lastModified());

        this.actionsHolder = new LibraryActionsHolder(this, false);

        this.nameCell = new NameHolder(_name);
        
        if (initializer != null && 
            initializer.isFile() && 
            FilenameUtils.getExtension(initializer.getName()) != null &&
            FilenameUtils.getExtension(initializer.getName()).endsWith("torrent")) {

            BTInfoAdditionalMetadataHolder additionalMetadataHolder = new BTInfoAdditionalMetadataHolder(initializer, initializer.getName());

            boolean hasLicense = additionalMetadataHolder.getLicenseBroker() != null;
            boolean hasPaymentOptions = additionalMetadataHolder.getPaymentOptions() != null;
            
            if (hasLicense) {
                 System.out.println(initializer);
                 license = additionalMetadataHolder.getLicenseBroker().getLicenseName();
            } else {
                license = "";
            }
            
            if (hasPaymentOptions) {
                paymentOptions = additionalMetadataHolder.getPaymentOptions();
            } else {
                paymentOptions = new PaymentOptions(null, null);
            }
            paymentOptions.setItemName(_name);

        }
    }

    /**
     * Returns the file of this data line.
     */
    public File getFile() {
        return initializer;
    }

    private LibraryActionsHolder actionsHolder;
    private NameHolder nameCell;

    /**
     * Returns the object stored in the specified cell in the table.
     *
     * @param idx  The column of the cell to access
     *
     * @return  The <code>Object</code> stored at the specified "cell" in
     *          the list
     */
    public Object getValueAt(int idx) {
        try {
            boolean isPlaying = isPlaying();
            switch (idx) {
            case ACTIONS_IDX:
                actionsHolder.setPlaying(isPlaying);
                return actionsHolder;
            case ICON_IDX:
                return new PlayableIconCell(getIcon(), isPlaying);
            case NAME_IDX:
                return nameCell;
            case SIZE_IDX:
                return new PlayableCell(this, _sizeHolder, _sizeHolder.toString(), isPlaying, idx);
            case TYPE_IDX:
                return new PlayableCell(this, _type, isPlaying, idx);
            case PATH_IDX:
                return new PlayableCell(this, _path, isPlaying, idx);
            case MODIFICATION_TIME_IDX:
                return new PlayableCell(this, lastModified, lastModified.toString(), isPlaying, idx);
//            case SHARE_IDX:
//                return new FileShareCell(this, initializer.getAbsolutePath(), shared);
            case PAYMENT_OPTIONS_IDX:
                return paymentOptions;
            case LICENSE_IDX:
                return license;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    private boolean isPlaying() {
        if (initializer != null) {
            return MediaPlayer.instance().isThisBeingPlayed(initializer);
        }

        return false;
    }

    public LimeTableColumn getColumn(int idx) {
        return getLimeTableColumns()[idx];
    }

    public boolean isClippable(int idx) {
        switch (idx) {
        case ICON_IDX:
            return false;
        default:
            return true;
        }
    }

    public int getTypeAheadColumn() {
        return NAME_IDX;
    }

    public boolean isDynamic(int idx) {
        return false;
    }

    public String[] getToolTipArray(int col) {
        return new String[] { getInitializeObject().getAbsolutePath() };
    }

    private LimeTableColumn[] getLimeTableColumns() {
        if (ltColumns == null) {
            LimeTableColumn[] temp = { new LimeTableColumn(ACTIONS_IDX, "LIBRARY_TABLE_ACTIONS", I18n.tr("Actions"), 18, true, LibraryActionsHolder.class),

            //new LimeTableColumn(SHARE_IDX, "LIBRARY_TABLE_SHARE", I18n.tr("Wi-Fi Shared"), 18, true, FileShareCell.class),

            new LimeTableColumn(ICON_IDX, "LIBRARY_TABLE_ICON", I18n.tr("Icon"), GUIMediator.getThemeImage("question_mark"), 18, true, PlayableIconCell.class),

            new LimeTableColumn(NAME_IDX, "LIBRARY_TABLE_NAME", I18n.tr("Name"), 239, true, NameHolder.class),

            new LimeTableColumn(SIZE_IDX, "LIBRARY_TABLE_SIZE", I18n.tr("Size"), 62, true, PlayableCell.class),

            new LimeTableColumn(TYPE_IDX, "LIBRARY_TABLE_TYPE", I18n.tr("Type"), 48, true, PlayableCell.class),

            new LimeTableColumn(PATH_IDX, "LIBRARY_TABLE_PATH", I18n.tr("Path"), 108, true, PlayableCell.class),

            new LimeTableColumn(MODIFICATION_TIME_IDX, "LIBRARY_TABLE_MODIFICATION_TIME", I18n.tr("Last Modified"), 20, true, PlayableCell.class),
            
            new LimeTableColumn(PAYMENT_OPTIONS_IDX, "LIBRARY_TABLE_PAYMENT_OPTIONS", I18n.tr("Tips/Donations"), 20, false, PaymentOptions.class),
            
            new LimeTableColumn(LICENSE_IDX, "LIBRARY_TABLE_LICENSE", I18n.tr("License"), 100, true, String.class),
            };
            ltColumns = temp;
        }
        return ltColumns;
    }

    private Icon getIcon() {
        boolean iconAvailable = IconManager.instance().isIconForFileAvailable(initializer);
        if (!iconAvailable && !_iconScheduledForLoad) {
            _iconScheduledForLoad = true;
            BackgroundExecutorService.schedule(new Runnable() {
                public void run() {
                    GUIMediator.safeInvokeAndWait(new Runnable() {
                        public void run() {
                            IconManager.instance().getIconForFile(initializer);
                            _iconLoaded = true;
                            _model.refresh();
                        }
                    });
                }
            });
            return null;
        } else if (_iconLoaded || iconAvailable) {
            return IconManager.instance().getIconForFile(initializer);
        } else {
            return null;
        }
    }
}
