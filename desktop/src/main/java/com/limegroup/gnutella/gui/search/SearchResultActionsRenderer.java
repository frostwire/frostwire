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

import com.frostwire.gui.AlphaIcon;
import com.frostwire.util.PlaybackUtil;
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.gui.theme.IconRepainter;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.internetarchive.InternetArchiveTorrentSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.VPNDropGuard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchResultActionsRenderer extends FWAbstractJPanelTableCellRenderer {
    private final static float BUTTONS_TRANSPARENCY = 0.85f;
    // Lazy-loaded icons to avoid EDT blocking during class loading
    private static ImageIcon play_solid;
    private static AlphaIcon play_transparent;
    private static ImageIcon download_solid;
    private static AlphaIcon download_transparent;
    private static ImageIcon details_solid;
    private static AlphaIcon details_transparent;
    private static volatile boolean iconsLoaded = false;

    /**
     * Lazy load icons on first access to avoid EDT blocking during class loading
     */
    private static synchronized void ensureIconsLoaded() {
        if (iconsLoaded) {
            return;
        }
        try {
            play_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("search_result_play_over"));
            play_transparent = new AlphaIcon(play_solid, BUTTONS_TRANSPARENCY);
            download_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("search_result_download_over"));
            download_transparent = new AlphaIcon(download_solid, BUTTONS_TRANSPARENCY);
            details_solid = (ImageIcon) IconRepainter.brightenIfDarkTheme(GUIMediator.getThemeImage("search_result_details_over"));
            details_transparent = new AlphaIcon(details_solid, BUTTONS_TRANSPARENCY);
            iconsLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JLabel labelPlay;
    private JLabel labelPartialDownload;
    private JLabel labelDownload;
    private UISearchResult uiSearchResult;
    private boolean showSolid;

    public SearchResultActionsRenderer() {
        setupUI();
    }

    private void setupUI() {
        ensureIconsLoaded(); // Ensure icons are loaded on first use
        setLayout(new GridBagLayout());
        labelPlay = new JLabel(play_transparent);
        labelPlay.setToolTipText(I18n.tr("Play/Preview"));
        labelPlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelPlay_mouseReleased(e);
            }
        });
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelPlay, c);
        labelDownload = new JLabel(download_transparent);
        labelDownload.setToolTipText(I18n.tr("Download"));

        labelDownload.addMouseListener(newDownloadAdapter());
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelDownload, c);
        labelPartialDownload = new JLabel(details_solid);
        labelPartialDownload.setToolTipText(I18n.tr("Select content to download from this torrent."));
        labelPartialDownload.addMouseListener(newDownloadAdapter());
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelPartialDownload, c);
        setEnabled(true);
    }

    private MouseListener newDownloadAdapter() {
        return new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelDownloadAction_mouseReleased(e);
            }
        };
    }

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
        updateUIData((SearchResultActionsHolder) dataHolder, table, row);
    }

    private void updateUIData(SearchResultActionsHolder actionsHolder, JTable table, int row) {
        cancelEdit();
        if (actionsHolder == null) {
            return;
        }
        uiSearchResult = actionsHolder.getSearchResult();
        if (uiSearchResult == null) {
            return;
        }
        showSolid = mouseIsOverRow(table, row);
        labelPlay.setVisible(isSearchResultPlayable());
        if (labelPlay.isVisible()) {
            updatePlayButton();
        }
        SearchResult sr = uiSearchResult.getSearchResult();
        // Preliminary results require a secondary search/step (format selection, file selection, etc.)
        boolean isPreliminary = sr.isPreliminary() || sr instanceof CrawlableSearchResult || sr instanceof InternetArchiveTorrentSearchResult;

        labelDownload.setIcon(showSolid ? download_solid : download_transparent);
        labelDownload.setVisible(!isPreliminary);

        // [+] Preliminary Download - shows format/content selection
        labelPartialDownload.setIcon(showSolid ? details_solid : details_transparent);
        labelPartialDownload.setVisible(isPreliminary);
    }

    private boolean isSearchResultPlayable() {
        SearchResult sr = uiSearchResult.getSearchResult();

        // Partial download results (TelluridePartialUISearchResult, YouTube, etc.) are playable
        if (uiSearchResult instanceof TelluridePartialUISearchResult) {
            return true;
        }

        if (sr instanceof SoundcloudSearchResult) {
            return true;
        }
        if (sr instanceof TellurideSearchResult) {
            return true;
        }

        // Streamable results with actual stream URLs
        if (sr instanceof StreamableSearchResult) {
            if (((StreamableSearchResult) sr).getStreamUrl() != null) {
                if (uiSearchResult.getExtension() != null) {
                    MediaType mediaType = MediaType.getMediaTypeForExtension(uiSearchResult.getExtension());
                    return mediaType != null && ((mediaType.equals(MediaType.getAudioMediaType())) || mediaType.equals(MediaType.getVideoMediaType()));
                }
            }
        }
        return false;
    }

    private void updatePlayButton() {
        labelPlay.setIcon((showSolid) ? play_solid : play_transparent);
    }

    private void labelPlay_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            SearchResult searchResult = uiSearchResult.getSearchResult();
            if (searchResult instanceof StreamableSearchResult || searchResult instanceof TellurideSearchResult || searchResult.isPreliminary()) {
                uiSearchResult.play();
            }
            updatePlayButton();
        }
    }

    /**
     * Handles both preliminary and direct downloads
     */
    private void labelDownloadAction_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            SearchResult sr = uiSearchResult.getSearchResult();
            boolean isTorrent = sr instanceof TorrentSearchResult || sr instanceof CrawlableSearchResult;
            // Check VPN-Drop protection for torrent transfers
            if (isTorrent && !VPNDropGuard.canUseBitTorrent()) {
                return;
            }
            uiSearchResult.download(isTorrent);
            // Only show Transfers tab for direct downloads, not for preliminary results that trigger secondary searches
            boolean isPreliminary = sr.isPreliminary() || uiSearchResult instanceof TelluridePartialUISearchResult;
            if (!isPreliminary) {
                GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
            }
        }
    }
}
