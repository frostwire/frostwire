/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

import com.frostwire.gui.components.slides.MultimediaSlideshowPanel;
import com.frostwire.gui.components.slides.Slide;
import com.frostwire.gui.components.slides.SlideshowPanel;
import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.RefreshListener;
import com.limegroup.gnutella.settings.SearchSettings;
import com.limegroup.gnutella.settings.UpdateManagerSettings;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class handles the display of search results.
 */
public final class SearchResultDisplayer implements RefreshListener {
    /**
     * The contents of tabbedPane.
     * INVARIANT: entries.size()==# of tabs in tabbedPane
     * LOCKING: +obtain entries' monitor before adjusting number of
     * outstanding searches, i.e., the number of tabs
     * +obtain a ResultPanel's monitor before adding or removing
     * results + to prevent deadlock, never obtain ResultPanel's
     * lock if holding entries'.
     */
    private static final List<SearchResultMediator> entries = new ArrayList<>();
    private static final int MIN_HEIGHT = 220;
    /**
     * Listener for events on the tabbed pane.
     */
    private final PaneListener PANE_LISTENER = new PaneListener();
    /**
     * <tt>JPanel</tt> containing the primary components of the search result
     * display.
     */
    private final JPanel MAIN_PANEL;
    /**
     * The main tabbed pane for displaying different search results.
     */
    private SearchTabbedPane tabbedPane;
    /**
     * Results is a panel that displays either a JTabbedPane when lots of
     * results exist OR a blank ResultPanel when nothing is showing.
     * Use switcher to switch between the two.  The first entry is the
     * blank results panel; the second is the tabbed panel.
     */
    private final JPanel results;
    /**
     * The layout that switches between the dummy result panel
     * and the JTabbedPane.
     */
    private final CardLayout switcher = new CardLayout();
    /**
     * The listener to notify about the currently displaying search
     * changing.
     * <p>
     * TODO: Allow more than one.
     */
    private ChangeListener _activeSearchListener;
    private SlideshowPanel promoSlides;

    /**
     * Constructs the search display elements.
     */
    SearchResultDisplayer() {
        MAIN_PANEL = new BoxPanel(BoxPanel.Y_AXIS);
        MAIN_PANEL.setMinimumSize(new Dimension(0, 0));
        tabbedPane = new SearchTabbedPane();
        results = new JPanel();
        // make the results panel take up as much space as possible
        // for when the window is resized. 
        results.setPreferredSize(new Dimension(10000, 10000));
        results.setLayout(switcher);
        //results.setBackground(Color.WHITE);
        //Add SlideShowPanel here.
        promoSlides = null;
        if (!UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS.getValue()) {
            promoSlides = new MultimediaSlideshowPanel(getDefaultSlides());
        } else {
            promoSlides = new MultimediaSlideshowPanel(UpdateManagerSettings.OVERLAY_SLIDESHOW_JSON_URL.getValue(), getDefaultSlides());
        }
        JPanel p = (JPanel) promoSlides;
        //p.setBackground(Color.WHITE);
        Dimension promoDimensions = new Dimension(717, 380);
        p.setPreferredSize(promoDimensions);
        p.setSize(promoDimensions);
        p.setMaximumSize(promoDimensions);

            /*
             The dummy result panel, used when no searches are active.
            */
        SearchResultMediator DUMMY = new SearchResultMediator(p);

        /* Container for the DUMMY ResultPanel. I'm keeping a reference to this
         * object so that I can refresh the image that it contains.
         */
        JPanel mainScreen = new JPanel(new BorderLayout());
        promoSlides.setupContainerAndControls(mainScreen, true);
        mainScreen.add(DUMMY.getComponent(), BorderLayout.CENTER);
        results.add("dummy", mainScreen);
        switcher.first(results);
        setupTabbedPane();
        MAIN_PANEL.add(results);
        CancelSearchIconProxy.updateTheme();
    }

    public void switchToTabByOffset(int offset) {
        if (tabbedPane != null) {
            tabbedPane.switchToTabByOffset(offset);
        }
    }

    private List<Slide> getDefaultSlides() {
        Slide s1 = new Slide("http://static.frostwire.com/images/overlays/default_now_on_android.png", "http://www.frostwire.com/?from=defaultSlide", 240000, null, null, null, null, null, null, 0, Slide.SLIDE_DOWNLOAD_METHOD_OPEN_URL, null, null, null, null, null, null, null, null,
                null, Slide.OPEN_CLICK_URL_ON_DOWNLOAD);
        Slide s2 = new Slide("http://static.frostwire.com/images/overlays/frostclick_default_overlay.jpg", "http://www.frostclick.com/?from=defaultSlide", 240000, null, null, null, null, null, null, 0, Slide.SLIDE_DOWNLOAD_METHOD_OPEN_URL, null, null, null, null, null, null, null, null,
                null, Slide.OPEN_CLICK_URL_ON_DOWNLOAD);
        return Arrays.asList(s1, s2);
    }

    /**
     * Sets the listener for what searches are currently displaying.
     */
    void setSearchListener(ChangeListener listener) {
        _activeSearchListener = listener;
    }

    SearchResultMediator addResultTab(long token, List<String> searchTokens, SearchInformation info) {
        SearchResultMediator panel = new SearchResultMediator(token, searchTokens, info);
        if (MAIN_PANEL.getHeight() < SearchResultDisplayer.MIN_HEIGHT) {
            GUIMediator.instance().getMainFrame().resizeSearchTransferDivider(SearchResultDisplayer.MIN_HEIGHT);
        }
        return addResultPanelInternal(panel, info.getTitle());
    }

    private void removeTabbedPaneListeners() {
        if (tabbedPane != null) {
            tabbedPane.removeMouseListener(PANE_LISTENER);
            tabbedPane.removeMouseMotionListener(PANE_LISTENER);
            tabbedPane.removeChangeListener(PANE_LISTENER);
        }
    }

    private void addTabbedPaneListeners() {
        if (tabbedPane != null) {
            tabbedPane.addMouseListener(PANE_LISTENER);
            tabbedPane.addMouseMotionListener(PANE_LISTENER);
            tabbedPane.addChangeListener(PANE_LISTENER);
        }
    }

    /**
     * Create a new JTabbedPane and add the necessary
     * listeners.
     */
    private void setupTabbedPane() {
        removeTabbedPaneListeners();
        tabbedPane = new SearchTabbedPane();
        tabbedPane.setRequestFocusEnabled(false);
        results.add("tabbedPane", tabbedPane);
        addTabbedPaneListeners();
    }

    /**
     * When a problem occurs with the JTabbedPane, we can
     * reset it (and hopefully circumvent the problem). We
     * first get all of the components and their respective
     * titles from the current tabbed pane, create a new
     * tabbed pane and add all of the components and titles
     * back in.
     */
    private void resetTabbedPane() {
        ArrayList<SearchResultMediator> ents = new ArrayList<>();
        ArrayList<Component> tabs = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        for (int i = 0; i < tabbedPane.getTabCount() && i < entries.size(); ++i) {
            tabs.add(tabbedPane.getComponent(i));
            titles.add(tabbedPane.getTitleAt(i));
            ents.add(entries.get(i));
        }
        tabbedPane.removeAll();
        entries.clear();
        setupTabbedPane();
        for (int i = 0; i < tabs.size(); ++i) {
            entries.add(ents.get(i));
            tabbedPane.addTab(titles.get(i), tabs.get(i));
        }
    }

    private SearchResultMediator addResultPanelInternal(SearchResultMediator panel, String title) {
        entries.add(panel);
        // XXX: LWC-1214 (hack)
        try {
            tabbedPane.addTab(title, CancelSearchIconProxy.createSelected(), panel.getComponent());
        } catch (ArrayIndexOutOfBoundsException e) {
            resetTabbedPane();
            entries.add(panel);
            tabbedPane.addTab(title, CancelSearchIconProxy.createSelected(), panel.getComponent());
        }
        // XXX: LWC-1088 (hack)
        try {
            tabbedPane.setSelectedIndex(entries.size() - 1);
        } catch (java.lang.IndexOutOfBoundsException ioobe) {
            resetTabbedPane();
            tabbedPane.setSelectedIndex(entries.size() - 1);
            // This will happen under OS X in apple.laf.CUIAquaTabbedPaneTabState.getIndex().
            // we grab all of the components from the current 
            // tabbed pane, create a new tabbed pane, and dump
            // the components back into it.
            //
            // For steps-to-reproduce, see:
            // https://www.limewire.org/jira/browse/LWC-1088
        }
        try {
            tabbedPane.setProgressActiveAt(entries.size() - 1, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Make sure Parallel searches are not beyond the maximum to avoid CPU from burning
        if (SearchSettings.PARALLEL_SEARCH.getValue() > SearchSettings.MAXIMUM_PARALLEL_SEARCH) {
            SearchSettings.PARALLEL_SEARCH.revertToDefault();
        }
        //Remove an old search if necessary
        if (entries.size() > SearchSettings.PARALLEL_SEARCH.getValue()) {
            killSearchAtIndex(0);
        }
        promoSlides.setVisible(false);
        switcher.last(results); //show tabbed results
        // If there are lots of tabs, this ensures everything
        // is properly visible. 
        MAIN_PANEL.revalidate();
        return panel;
    }

    /**
     * If i rp is no longer the i'th panel of this, returns silently. Otherwise
     * adds line to rp under the given group. Updates the count on the tab in
     * this and restarts the spinning lime.
     */
    void addQueryResult(long token, UISearchResult line, SearchResultMediator rp) {
        if (rp.isStopped()) {
            return;
        }
        //Actually add the line.   Must obtain rp's monitor first.
        if (!rp.matches(token))//GUID of rp!=replyGuid
            throw new IllegalArgumentException("guids don't match");
        rp.add(line);
        int resultPanelIndex;
        // Search for the ResultPanel to verify it exists.
        resultPanelIndex = entries.indexOf(rp);
        // If we couldn't find it, silently exit.
        if (resultPanelIndex == -1) {
            return;
        }
        //Update index on tab.  Don't forget to add 1 since line hasn't
        //actually been added!
        tabbedPane.setTitleAt(resultPanelIndex, titleOf(rp));
    }

    void updateSearchIcon(SearchResultMediator rp, boolean active) {
        int resultPanelIndex;
        // Search for the ResultPanel to verify it exists.
        resultPanelIndex = entries.indexOf(rp);
        // If we couldn't find it, silently exit.
        if (resultPanelIndex == -1) {
            return;
        }
        //Update index on tab.  Don't forget to add 1 since line hasn't
        //actually been added!
        tabbedPane.setProgressActiveAt(resultPanelIndex, active);
    }

    /**
     * Shows the popup menu that displays various options to the user.
     */
    private void showMenu(MouseEvent e) {
        SearchResultMediator rp = getSelectedResultPanel();
        if (rp != null) {
            JPopupMenu menu = rp.createPopupMenu(new SearchResultDataLine[0]);
            Point p = e.getPoint();
            if (menu != null) {
                try {
                    menu.show(MAIN_PANEL, p.x + 1, p.y - 6);
                } catch (IllegalComponentStateException icse) {
                    // happens occasionally, ignore.
                }
            }
        }
    }

    /**
     * Returns the currently selected <tt>ResultPanel</tt> instance.
     *
     * @return the currently selected <tt>ResultPanel</tt> instance,
     * or <tt>null</tt> if there is no currently selected panel
     */
    SearchResultMediator getSelectedResultPanel() {
        int i = tabbedPane.getSelectedIndex();
        if (i == -1)
            return null;
        try {
            return entries.get(i);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Returns the <tt>ResultPanel</tt> for the specified GUID.
     *
     * @return the ResultPanel that matches the specified GUID, or null
     * if none match.
     */
    SearchResultMediator getResultPanelForGUID(long token) {
        for (SearchResultMediator rp : entries) {
            if (rp.matches(token)) { //order matters: rp may be a dummy guid.
                return rp;
            }
        }
        return null;
    }

    /**
     * Get index for point.
     */
    private int getIndexForPoint(int x, int y) {
        TabbedPaneUI ui = tabbedPane.getUI();
        return ui.tabForCoordinate(tabbedPane, x, y);
    }

    int getIndexForTabComponent(Component c) {
        for (int i = 0; i < entries.size(); i++) {
            SearchResultMediator rp = entries.get(i);
            if (rp.getComponent().equals(c)) {
                return i;
            }
        }
        return -1;
    }

    public void closeCurrentTab() {
        int index = tabbedPane.getSelectedIndex();
        if (index != -1) {
            killSearchAtIndex(index);
        }
    }

    void closeTabAt(int i) {
        try {
            SearchResultMediator searchResultMediator = entries.get(i);
            if (searchResultMediator != null) {
                killSearchAtIndex(i);
            }
        } catch (Throwable ignored) {
        }
    }

    void closeAllTabs() {
        while (entries != null && entries.size() > 0) {
            closeTabAt(0);
        }
    }

    void closeOtherTabs() {
        if (entries == null || entries.size() < 2) {
            //nothing to close.
            return;
        }
        int index = tabbedPane.getSelectedIndex();
        if (index != -1) {
            final SearchResultMediator currentMediator = entries.get(index);
            int i = 0;
            while (entries.size() > 1 && i < entries.size()) {
                if (entries.get(i) != currentMediator) {
                    closeTabAt(i);
                } else {
                    i++;
                }
            }
        }
    }

    void killSearchAtIndex(int i) {
        SearchResultMediator killed = entries.remove(i);
        try {
            tabbedPane.removeTabAt(i);
        } catch (IllegalArgumentException iae) {
            // happens occasionally on osx w/ java 1.4.2_05, ignore.
        } catch (ArrayIndexOutOfBoundsException oob) {
            // happens occassionally on os x because of apple.laf.*
            resetTabbedPane();
            tabbedPane.removeTabAt(i);
        }
        fixIcons();
        SearchMediator.searchKilled(killed);
        if (entries.size() == 0) {
            try {
                promoSlides.setVisible(true);
                switcher.first(results); //show dummy table
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                //happens on jdk1.5 beta w/ windows XP, ignore.
            }
        }
    }

    /**
     * called by ResultPanel when the views are changed. Used to set the
     * tab to indicate the correct number of TableLines in the current
     * view.
     */
    void setTabDisplayCount(SearchResultMediator rp) {
        Object panel;
        int i = 0;
        boolean found = false;
        for (; i < entries.size(); i++) {//safe its synchronized
            panel = entries.get(i);
            if (panel == rp) {
                found = true;
                break;
            }
        }
        if (found)//find the number of lines in model
            tabbedPane.setTitleAt(i, titleOf(rp));
    }

    private void fixIcons() {
        int sel = tabbedPane.getSelectedIndex();
        for (int i = 0; i < entries.size() && i < tabbedPane.getTabCount(); i++) {
            tabbedPane.setIconAt(i, i == sel ? CancelSearchIconProxy.createSelected() : CancelSearchIconProxy.createPlain());
        }
    }

    /**
     * Returns the <tt>JComponent</tt> instance containing all of the search
     * result ui components.
     *
     * @return the <tt>JComponent</tt> instance containing all of the search
     * result ui components
     */
    JComponent getComponent() {
        return MAIN_PANEL;
    }

    /**
     * Every second, redraw only the tab portion of the TabbedPane
     * and determine if we should stop the lime spinning.
     */
    public void refresh() {
        if (tabbedPane.isVisible() && tabbedPane.isShowing()) {
            Rectangle allBounds = tabbedPane.getBounds();
            Component comp;
            try {
                comp = tabbedPane.getSelectedComponent();
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                resetTabbedPane();
                comp = tabbedPane.getSelectedComponent();
                // happens on OSX occasionally, ignore.
            }
            if (comp != null) {
                Rectangle compBounds = comp.getBounds();
                // The length of the tab rectangle will extend
                // over the bounds of the entire TabbedPane
                // up to 1 before the y scale of the visible component.
                Rectangle allTabs = new Rectangle(allBounds.x, allBounds.y, allBounds.width, compBounds.y - 1);
                tabbedPane.repaint(allTabs);
            }
        }
    }

    /**
     * Returns the title of the specified ResultPanel.
     */
    private String titleOf(SearchResultMediator rp) {
        int total = rp.totalResults();
        String title = rp.getTitle();
        if (title.length() > 40) {
            title = title.substring(0, 39) + "...";
        }
        return title + " (" + total + " " + I18n.tr("results") + ")";
    }

    int tabCount() {
        int result = 0;
        if (entries != null && !entries.isEmpty()) {
            result = entries.size();
        }
        return result;
    }

    int currentTabIndex() {
        return tabbedPane.getSelectedIndex();
    }

    /**
     * Listens for events on the JTabbedPane and dispatches commands.
     */
    private class PaneListener extends MouseAdapter implements MouseListener, MouseMotionListener, ChangeListener {
        /**
         * The last index that was rolled over.
         */
        private int lastIdx = -1;

        /**
         * Either closes the selected tab or notifies the listener
         * that a tab was clicked.
         */
        public void mouseClicked(MouseEvent e) {
            if (tryPopup(e))
                return;
            if (SwingUtilities.isLeftMouseButton(e)) {
                int x = e.getX();
                int y = e.getY();
                int idx;
                idx = shouldKillIndex(x, y);
                if (idx != -1) {
                    lastIdx = -1;
                    killSearchAtIndex(idx);
                }
                if (idx == -1)
                    stateChanged(null);
            }
        }

        /**
         * Returns the index of the tab if the coordinates x,y can close it.
         * Otherwise returns -1.
         */
        private int shouldKillIndex(int x, int y) {
            int idx = getIndexForPoint(x, y);
            if (idx != -1) {
                Icon icon = tabbedPane.getIconAt(idx);
                if (icon instanceof CancelSearchIconProxy)
                    if (((CancelSearchIconProxy) icon).shouldKill(x, y))
                        return idx;
            }
            return -1;
        }

        /**
         * Resets the last armed icon.
         */
        private void resetIcon() {
            if (lastIdx != -1 && lastIdx < tabbedPane.getTabCount()) {
                if (lastIdx == tabbedPane.getSelectedIndex())
                    tabbedPane.setIconAt(lastIdx, CancelSearchIconProxy.createSelected());
                else
                    tabbedPane.setIconAt(lastIdx, CancelSearchIconProxy.createPlain());
                lastIdx = -1;
            }
        }

        public void mousePressed(MouseEvent e) {
            tryPopup(e);
        }

        public void mouseReleased(MouseEvent e) {
            tryPopup(e);
        }

        public void mouseEntered(MouseEvent e) {
        }

        public void mouseExited(MouseEvent e) {
            resetIcon();
        }

        public void mouseDragged(MouseEvent e) {
        }

        /**
         * Shows the popup if this was a popup trigger.
         */
        private boolean tryPopup(final MouseEvent e) {
            if (e.isPopupTrigger()) {
                // make sure the given tab is selected.
                int idx = getIndexForPoint(e.getX(), e.getY());
                if (idx != -1) {
                    try {
                        tabbedPane.setSelectedIndex(idx);
                    } catch (ArrayIndexOutOfBoundsException aioobe) {
                        resetTabbedPane();
                        tabbedPane.setSelectedIndex(idx);
                    }
                }
                showMenu(e);
                return true;
            }
            return false;
        }

        /**
         * Forwards events to the activeSearchListener.
         */
        public void stateChanged(ChangeEvent e) {
            _activeSearchListener.stateChanged(e);
            fixIcons();
        }
    }
}
