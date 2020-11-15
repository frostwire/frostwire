/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.AlphaIcon;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.archiveorg.ArchiveorgTorrentSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

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
    private final static ImageIcon play_solid;
    private final static AlphaIcon play_transparent;
    private final static ImageIcon download_solid;
    private final static AlphaIcon download_transparent;
    private final static ImageIcon details_solid;
    private final static AlphaIcon details_transparent;
    private final static ImageIcon speaker_icon;

    static {
        play_solid = GUIMediator.getThemeImage("search_result_play_over");
        play_transparent = new AlphaIcon(play_solid, BUTTONS_TRANSPARENCY);
        download_solid = GUIMediator.getThemeImage("search_result_download_over");
        download_transparent = new AlphaIcon(download_solid, BUTTONS_TRANSPARENCY);
        details_solid = GUIMediator.getThemeImage("search_result_details_over");
        details_transparent = new AlphaIcon(details_solid, BUTTONS_TRANSPARENCY);
        speaker_icon = GUIMediator.getThemeImage("speaker");
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
        setLayout(new GridBagLayout());
        GridBagConstraints c;
        labelPlay = new JLabel(play_transparent);
        labelPlay.setToolTipText(I18n.tr("Play/Preview"));
        labelPlay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelPlay_mouseReleased(e);
            }
        });
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelPlay, c);
        labelDownload = new JLabel(download_transparent);
        labelDownload.setToolTipText(I18n.tr("Download"));
        labelDownload.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelDownload_mouseReleased(e);
            }
        });
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelDownload, c);
        labelPartialDownload = new JLabel(details_solid);
        labelPartialDownload.setToolTipText(I18n.tr("Select content to download from this torrent."));
        labelPartialDownload.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                labelPartialDownload_mouseReleased(e);
            }
        });
        c = new GridBagConstraints();
        c.gridx = GridBagConstraints.RELATIVE;
        c.ipadx = 3;
        add(labelPartialDownload, c);
        setEnabled(true);
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
        labelDownload.setIcon(showSolid ? download_solid : download_transparent);
        labelDownload.setVisible(true);
        labelPartialDownload.setIcon(showSolid ? details_solid : details_transparent);
        SearchResult sr = uiSearchResult.getSearchResult();
        labelPartialDownload.setVisible(sr instanceof CrawlableSearchResult || sr instanceof ArchiveorgTorrentSearchResult);
    }

    private boolean isSearchResultPlayable() {
        boolean playable = false;
        if (uiSearchResult.getSearchResult() instanceof SoundcloudSearchResult) {
            return true;
        } else if (uiSearchResult.getSearchResult() instanceof StreamableSearchResult) {
            playable = ((StreamableSearchResult) uiSearchResult.getSearchResult()).getStreamUrl() != null;
        } else if (uiSearchResult.getSearchResult() instanceof TellurideSearchResult) {
            return true;
        }

        if (playable && uiSearchResult.getExtension() != null) {
            MediaType mediaType = MediaType.getMediaTypeForExtension(uiSearchResult.getExtension());
            playable = mediaType != null && (mediaType.equals(MediaType.getAudioMediaType())) || mediaType.equals(MediaType.getVideoMediaType());
        }
        return playable;
    }

    private void updatePlayButton() {
        labelPlay.setIcon((isStreamableSourceBeingPlayed(uiSearchResult)) ? speaker_icon : (showSolid) ? play_solid : play_transparent);
    }

    private void labelPlay_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            SearchResult searchResult = uiSearchResult.getSearchResult();
            if ((searchResult instanceof StreamableSearchResult && !isStreamableSourceBeingPlayed(uiSearchResult)) ||
                    searchResult instanceof TellurideSearchResult) {
                uiSearchResult.play();
            }
            updatePlayButton();
        }
    }

    private void labelPartialDownload_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            MouseListener[] mouseListeners = labelPartialDownload.getMouseListeners();
            for (MouseListener mouseListener : mouseListeners) {
                labelPartialDownload.removeMouseListener(mouseListener);
            }

            SearchResult sr = uiSearchResult.getSearchResult();
            if (sr instanceof CrawlableSearchResult || sr instanceof ArchiveorgTorrentSearchResult) {
                uiSearchResult.download(true);
                if (sr instanceof ArchiveorgTorrentSearchResult) {
                    GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
                }
            }

            for (MouseListener mouseListener : mouseListeners) {
                labelPartialDownload.addMouseListener(mouseListener);
            }
        }
    }

    private void labelDownload_mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            uiSearchResult.download(false);
            GUIMediator.instance().showTransfers(TransfersTab.FilterMode.ALL);
        }
    }

    private boolean isStreamableSourceBeingPlayed(UISearchResult sr) {
        SearchResult delegateSearchResult = sr.getSearchResult();
        if (delegateSearchResult instanceof SoundcloudSearchResult) {
            if (!((SoundcloudSearchResult) delegateSearchResult).fetchedDownloadUrl()) {
                return false;
            }
        }
        return delegateSearchResult instanceof StreamableSearchResult && MediaPlayer.instance().isThisBeingPlayed(((StreamableSearchResult) delegateSearchResult).getStreamUrl());
    }
}
