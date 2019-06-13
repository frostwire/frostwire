package com.limegroup.gnutella;

import com.frostwire.bittorrent.BTDownload;

import java.io.File;

/**
 * Callback to notify the GUI of asynchronous backend events.
 * The methods in this fall into the following categories:
 * <p/>
 * <ul>
 * <li>Query replies (for displaying results) and query strings
 * (for the monitor)
 * <li>Update in shared file statistics
 * <li>Change of connection state
 * <li>New or dead uploads or downloads
 * <li>New chat requests and chat messages
 * <li>Error messages
 * </ul>
 */
public interface ActivityCallback {
    /**
     * Add a file to the download window
     */
    void addDownload(BTDownload dl);

    void updateDownload(BTDownload dl);

    /**
     * Show active downloads
     */
    void showDownloads();

    /**
     * Tell the GUI to deiconify.
     */
    void restoreApplication();

    /**
     * Try to download the torrent file
     */
    void handleTorrent(File torrentFile);

    void handleTorrentMagnet(String request, boolean partialDownload);

    boolean isRemoteDownloadsAllowed();
}
