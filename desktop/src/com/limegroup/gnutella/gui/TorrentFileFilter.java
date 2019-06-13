package com.limegroup.gnutella.gui;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class TorrentFileFilter extends FileFilter {
    public static final TorrentFileFilter INSTANCE = new TorrentFileFilter();

    /* (non-Javadoc)
     * @see java.io.FileFilter#accept(java.io.File)
     */
    public boolean accept(File file) {
        return file.isDirectory() || file.getName().toLowerCase().endsWith(".torrent");
    }

    /* (non-Javadoc)
     * @see javax.swing.filechooser.FileFilter#getDescription()
     */
    public String getDescription() {
        // TODO i18nize
        return "Torrents";
    }
}