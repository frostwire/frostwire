package com.frostwire.gui.searchfield;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Maintains a list of recent searches and persists this list automatically
 * using {@link Preferences}. A recent searches popup menu can be installed on
 *
 * @author Peter Weishapl <petw@gmx.net>
 */
class RecentSearches implements ActionListener {
    private Preferences prefsNode;
    private final List<String> recentSearches = new ArrayList<>();
    private final List<ChangeListener> listeners = new ArrayList<>();

    /**
     * Creates a list of recent searches and uses <code>saveName</code> to
     * persist this list under the {@link Preferences} user root node. Existing
     * entries will be loaded automatically.
     *
     * @param saveName a unique name for saving this list of recent searches
     */
    RecentSearches(String saveName) {
        this(null, saveName);
    }

    /**
     * Creates a list of recent searches and uses <code>saveName</code> to
     * persist this list under the <code>prefs</code> node. Existing entries
     * will be loaded automatically.
     *
     * @param saveName a unique name for saving this list of recent searches. If
     *                 saveName is <code>null</code>, the list will not be
     *                 persisted
     */
    private RecentSearches(Preferences prefs, String saveName) {
        if (prefs == null) {
            try {
                prefs = Preferences.userRoot();
            } catch (AccessControlException ace) {
                // disable persistency, if we aren't allowed to access
                // preferences.
                Logger.getLogger(getClass().getName()).warning("cannot acces preferences. persistency disabled.");
            }
        }
        if (prefs != null && saveName != null) {
            this.prefsNode = prefs.node(saveName);
            load();
        }
    }

    private void load() {
        // load persisted entries
        try {
            String[] recent = new String[prefsNode.keys().length];
            for (String key : prefsNode.keys()) {
                recent[prefsNode.getInt(key, -1)] = key;
            }
            recentSearches.addAll(Arrays.asList(recent));
        } catch (Exception ex) {
            // ignore
        }
    }

    private void save() {
        if (prefsNode == null) {
            return;
        }
        try {
            prefsNode.clear();
        } catch (BackingStoreException e) {
            // ignore
        }
        int i = 0;
        for (String search : recentSearches) {
            prefsNode.putInt(search, i++);
        }
    }

    /**
     * Add a search string as the first element. If the search string is
     * <code>null</code> or empty nothing will be added. If the search string
     * already exists, the old element will be removed. The modified list will
     * automatically be persisted.
     * <p>
     * If the number of elements exceeds the maximum number of entries, the last
     * entry will be removed.
     *
     * @param searchString the search string to add
     * @see #getMaxRecents()
     */
    private void put(String searchString) {
        if (searchString == null || searchString.trim().length() == 0) {
            return;
        }
        int lastIndex = recentSearches.indexOf(searchString);
        if (lastIndex != -1) {
            recentSearches.remove(lastIndex);
        }
        recentSearches.add(0, searchString);
        if (getLength() > getMaxRecents()) {
            recentSearches.remove(recentSearches.size() - 1);
        }
        save();
        fireChangeEvent();
    }

    /**
     * Returns all recent searches in this list.
     *
     * @return the recent searches
     */
    private String[] getRecentSearches() {
        return recentSearches.toArray(new String[]{});
    }

    /**
     * The number of recent searches.
     *
     * @return number of recent searches
     */
    private int getLength() {
        return recentSearches.size();
    }

    /**
     * Remove all recent searches.
     */
    private void removeAll() {
        recentSearches.clear();
        save();
        fireChangeEvent();
    }

    /**
     * Returns the maximum number of recent searches.
     *
     * @return the maximum number of recent searches
     * @see #put(String)
     */
    private int getMaxRecents() {
        return 5;
    }

    /**
     * Add a change listener. A {@link ChangeEvent} will be fired whenever a
     * search is added or removed.
     *
     * @param l the {@link ChangeListener}
     */
    private void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    /**
     * Remove a change listener.
     *
     * @param l a registered {@link ChangeListener}
     */
    private void removeChangeListener(ChangeListener l) {
        listeners.remove(l);
    }

    private void fireChangeEvent() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) {
            l.stateChanged(e);
        }
    }

    /**
     * Creates the recent searches popup menu which will be used by
     * <code>searchField</code>.
     * <p>
     * Override to return a custom popup menu.
     *
     * @param searchField the search field the returned popup menu will be installed on
     * @return the recent searches popup menu
     */
    private JPopupMenu createPopupMenu(JTextField searchField) {
        return new RecentSearchesPopup(this, searchField);
    }

    /**
     * Install a recent the searches popup menu returned by
     * Also registers an {@link ActionListener} on <code>searchField</code>
     * and adds the search string to the list of recent searches whenever a
     * {@link ActionEvent} is received.
     * <p>
     * Uses {@link NativeSearchFieldSupport} to achieve compatibility with the native
     * search field support provided by the Mac Look And Feel since Mac OS 10.5.
     *
     * @param searchField the search field to install a recent searches popup menu on
     */
    public void install(JTextField searchField) {
        searchField.addActionListener(this);
        NativeSearchFieldSupport.setFindPopupMenu(searchField, createPopupMenu(searchField));
    }

    /**
     * Remove the recent searches popup from <code>searchField</code> when
     * installed and stop listening for {@link ActionEvent}s fired by the
     * search field.
     *
     * @param searchField uninstall recent searches popup menu
     */
    void uninstall(JXSearchField searchField) {
        searchField.removeActionListener(this);
        if (searchField.getFindPopupMenu() instanceof RecentSearchesPopup) {
            removeChangeListener((ChangeListener) searchField.getFindPopupMenu());
            searchField.setFindPopupMenu(null);
        }
    }

    /**
     * Calls {@link #put(String)} with the {@link ActionEvent}s action command
     * as the search string.
     */
    public void actionPerformed(ActionEvent e) {
        put(e.getActionCommand());
    }

    /**
     * The popup menu returned by
     */
    public static class RecentSearchesPopup extends JPopupMenu implements ActionListener, ChangeListener {
        private static final long serialVersionUID = 3389724537449677787L;
        private final RecentSearches recentSearches;
        private final JTextField searchField;
        private JMenuItem clear;

        /**
         * Creates a new popup menu based on the given {@link RecentSearches}
         * and {@link JXSearchField}.
         */
        RecentSearchesPopup(RecentSearches recentSearches, JTextField searchField) {
            this.searchField = searchField;
            this.recentSearches = recentSearches;
            recentSearches.addChangeListener(this);
            buildMenu();
        }

        /**
         * Rebuilds the menu according to the recent searches.
         */
        private void buildMenu() {
            setVisible(false);
            removeAll();
            if (recentSearches.getLength() == 0) {
                JMenuItem noRecent = new JMenuItem(UIManager.getString("SearchField.noRecentsText"));
                noRecent.setEnabled(false);
                add(noRecent);
            } else {
                JMenuItem recent = new JMenuItem(UIManager.getString("SearchField.recentsMenuTitle"));
                recent.setEnabled(false);
                add(recent);
                for (String searchString : recentSearches.getRecentSearches()) {
                    JMenuItem mi = new JMenuItem(searchString);
                    mi.addActionListener(this);
                    add(mi);
                }
                addSeparator();
                clear = new JMenuItem(UIManager.getString("SearchField.clearRecentsText"));
                clear.addActionListener(this);
                add(clear);
            }
        }

        /**
         * Sets {@link #searchField}s text to the {@link ActionEvent}s action
         * command and call {@link JXSearchField#postActionEvent()} to fire an
         * {@link ActionEvent}, if <code>e</code>s source is not the clear
         * menu item. If the source is the clear menu item, all recent searches
         * will be removed.
         */
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == clear) {
                recentSearches.removeAll();
            } else {
                searchField.setText(e.getActionCommand());
                searchField.postActionEvent();
            }
        }

        /**
         * Every time the recent searches fires a {@link ChangeEvent} call
         * {@link #buildMenu()} to rebuild the whole menu.
         */
        public void stateChanged(ChangeEvent e) {
            buildMenu();
        }
    }
}
