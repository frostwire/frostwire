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

package com.frostwire.gui.searchfield;

import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UISettings;
import org.limewire.collection.AutoCompleteDictionary;
import org.limewire.collection.StringTrieSet;
import org.limewire.util.OSUtils;

import javax.swing.*;
import javax.swing.text.Document;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.util.Iterator;
import java.util.Vector;

public class SearchField extends JXSearchField {
    /**
     * The sole JPopupMenu that's shared among all the text fields.
     */
    private static JPopupMenu POPUP;
    /**
     * The undo action.
     */
    private static final Action UNDO_ACTION = new FieldAction(I18n.tr("Undo")) {
        public void actionPerformed(ActionEvent e) {
            getField(e).undo();
        }
    };
    /**
     * The cut action
     */
    private static final Action CUT_ACTION = new FieldAction(I18n.tr("Cut")) {
        public void actionPerformed(ActionEvent e) {
            getField(e).cut();
        }
    };
    /**
     * The copy action.
     */
    private static final Action COPY_ACTION = new FieldAction(I18n.tr("Copy")) {
        public void actionPerformed(ActionEvent e) {
            getField(e).copy();
        }
    };
    /**
     * The paste action.
     */
    private static final Action PASTE_ACTION = new FieldAction(I18n.tr("Paste")) {
        public void actionPerformed(ActionEvent e) {
            getField(e).paste();
        }
    };
    /**
     * The delete action.
     */
    private static final Action DELETE_ACTION = new FieldAction(I18n.tr("Delete")) {
        public void actionPerformed(ActionEvent e) {
            getField(e).replaceSelection("");
        }
    };
    /**
     * The select all action.
     */
    private static final Action SELECT_ALL_ACTION = new FieldAction(I18n.tr("Select All")) {
        public void actionPerformed(ActionEvent e) {
            getField(e).selectAll();
        }
    };
    /**
     * The popup the scroll pane is in
     */
    private Popup popup;
    //----------------------------------------------------------------------------
    // Fields
    //----------------------------------------------------------------------------
    AutoCompleteDictionary dict;
    /**
     * The list auto-completable items are shown in
     */
    AutoCompleteList entryList;
    /**
     * The panel the popup is shown in.
     */
    JPanel entryPanel;
    /**
     * Our UndoManager.
     */
    private UndoManager undoManager;
    /**
     * Whether or not we tried to show a popup while this wasn't showing
     */
    private boolean showPending;

    public SearchField() {
        init();
    }

    /**
     * Creates the JPopupMenu that all LimeTextFields will share.
     */
    private static JPopupMenu createPopup() {
        if (POPUP != null) {
            return POPUP;
        }
        // initialize the JPopupMenu with necessary stuff.
        POPUP = new SkinPopupMenu() {
            /**
             *
             */
            private static final long serialVersionUID = -6004124495511263059L;

            public void show(Component invoker, int x, int y) {
                ((SearchField) invoker).updateActions();
                super.show(invoker, x, y);
            }
        };
        POPUP.add(new SkinMenuItem(UNDO_ACTION));
        POPUP.addSeparator();
        POPUP.add(new SkinMenuItem(CUT_ACTION));
        POPUP.add(new SkinMenuItem(COPY_ACTION));
        POPUP.add(new SkinMenuItem(PASTE_ACTION));
        POPUP.add(new SkinMenuItem(DELETE_ACTION));
        POPUP.addSeparator();
        POPUP.add(new SkinMenuItem(SELECT_ALL_ACTION));
        return POPUP;
    }

    /**
     * Undoes the last action.
     */
    private void undo() {
        try {
            if (undoManager != null)
                undoManager.undoOrRedo();
        } catch (CannotUndoException | CannotRedoException ignored) {
        }
    }

    /**
     * Gets the undo manager.
     */
    private UndoManager getUndoManager() {
        return undoManager;
    }

    /**
     * Intercept the 'setDocument' so that we can null out our manager
     * and possibly assign a new one.
     */
    public void setDocument(Document doc) {
        if (doc != getDocument())
            undoManager = null;
        super.setDocument(doc);
    }
    ///// from AutoCompleteTextField
    //----------------------------------------------------------------------------
    // Public methods
    //----------------------------------------------------------------------------

    /**
     * Initialize the necessary events.
     */
    private void init() {
        setComponentPopupMenu(createPopup());
        undoManager = new UndoManager();
        undoManager.setLimit(1);
        getDocument().addUndoableEditListener(undoManager);
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        enableEvents(AWTEvent.HIERARCHY_EVENT_MASK);
        enableEvents(AWTEvent.FOCUS_EVENT_MASK);
        ThemeMediator.fixKeyStrokes(this);
    }

    /**
     * Updates the actions in each text just before showing the popup menu.
     */
    private void updateActions() {
        String selectedText = getSelectedText();
        if (selectedText == null)
            selectedText = "";
        boolean stuffSelected = !selectedText.equals("");
        boolean allSelected = selectedText.equals(getText());
        UNDO_ACTION.setEnabled(isEnabled() && isEditable() && isUndoAvailable());
        CUT_ACTION.setEnabled(isEnabled() && isEditable() && stuffSelected);
        COPY_ACTION.setEnabled(isEnabled() && stuffSelected);
        PASTE_ACTION.setEnabled(isEnabled() && isEditable() && isPasteAvailable());
        DELETE_ACTION.setEnabled(isEnabled() && stuffSelected);
        SELECT_ALL_ACTION.setEnabled(isEnabled() && !allSelected);
    }

    /**
     * Determines if an Undo is available.
     */
    private boolean isUndoAvailable() {
        return getUndoManager() != null && getUndoManager().canUndoOrRedo();
    }
    //----------------------------------------------------------------------------
    // Protected methods
    //----------------------------------------------------------------------------

    /**
     * Determines if paste is currently available.
     */
    private boolean isPasteAvailable() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor);
        } catch (UnsupportedOperationException | IllegalStateException he) {
            return false;
        }
    }

    /**
     * Creates the default dictionary object
     */
    AutoCompleteDictionary createDefaultDictionary() {
        return new StringTrieSet(true);
    }
    /// from ClearableAutoComplete

    /**
     * Gets whether the component is currently performing autocomplete lookups as
     * keystrokes are performed. Looks up the value in UISettings.
     *
     * @return True or false.
     */
    boolean getAutoComplete() {
        return UISettings.AUTOCOMPLETE_ENABLED.getValue();
    }

    /**
     * Adds the current value of the field underlying dictionary
     */
    public void addToDictionary() {
        if (!getAutoComplete())
            return;
        if (dict == null) {
            setUp();
            this.dict = createDefaultDictionary();
        }
        dict.addEntry(getText().trim());
    }

    protected String lookup(String s) {
        if (dict != null && getAutoComplete() && !s.equals(""))
            return dict.lookup(s);
        return null;
    }

    /**
     * Fires an action event.
     * <p>
     * If the popup is visible, this resets the current
     * text to be the selection on the popup (if something was selected)
     * prior to firing the event.
     */
    protected void fireActionPerformed() {
        if (popup != null) {
            String selection = entryList.getSelectedValue();
            if (selection != null) {
                hidePopup();
                setText(selection);
                return;
            }
        }
        super.fireActionPerformed();
    }
    //----------------------------------------------------------------------------
    // Protected methods
    //----------------------------------------------------------------------------

    /**
     * Forwards necessary events to the AutoCompleteList.
     */
    public void processKeyEvent(KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_UP || evt.getKeyCode() == KeyEvent.VK_DOWN)
            evt.consume();
        super.processKeyEvent(evt);
        if (dict != null) {
            switch (evt.getID()) {
                case KeyEvent.KEY_PRESSED:
                    switch (evt.getKeyCode()) {
                        case KeyEvent.VK_UP:
                            if (popup != null)
                                entryList.decrementSelection();
                            else
                                showPopup(dict.iterator());
                            break;
                        case KeyEvent.VK_DOWN:
                            if (popup != null)
                                entryList.incrementSelection();
                            else
                                showPopup(dict.iterator());
                            break;
                        case KeyEvent.VK_ESCAPE:
                            if (popup != null) {
                                hidePopup();
                                selectAll();
                            }
                            break;
                    }
                    break;
                case KeyEvent.KEY_TYPED:
                    if (evt.getKeyChar() == KeyEvent.VK_ENTER) {
                    } else {
                        autoCompleteInput();
                    }
            }
        }
    }

    /**
     * Ensures the popup gets hidden if this text-box is hidden and that
     * the popup is shown if a previous show is pending (from trying to
     * autocomplete while it wasn't visible).
     */
    protected void processHierarchyEvent(HierarchyEvent evt) {
        super.processHierarchyEvent(evt);
        if ((evt.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == HierarchyEvent.SHOWING_CHANGED) {
            boolean showing = isShowing();
            if (!showing && popup != null)
                hidePopup();
            else if (showing && popup == null && showPending)
                autoCompleteInput();
        }
    }

    /**
     * Ensures that if we lose focus, the popup goes away.
     */
    protected void processFocusEvent(FocusEvent evt) {
        super.processFocusEvent(evt);
        if (evt.getID() == FocusEvent.FOCUS_LOST) {
            if (popup != null)
                hidePopup();
        }
    }

    // overwritten to disable
    private void setUp() {
    }

    /**
     * Gets the component that is the popup listing other choices.
     */
    JComponent getPopupComponent() {
        if (entryPanel != null)
            return entryPanel;
        entryPanel = new JPanel(new GridBagLayout());
        entryPanel.setBorder(UIManager.getBorder("List.border"));
        entryPanel.setBackground(UIManager.getColor("List.background"));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = GridBagConstraints.REMAINDER;
        entryList = new AutoCompleteList();
        JScrollPane entryScrollPane = new JScrollPane(entryList);
        entryScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        entryScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        entryPanel.add(entryScrollPane, c);
        entryPanel.add(new ClearHistory(), c);
        return entryPanel;
    }

    /**
     * Fills the popup with text & shows it.
     */
    void showPopup(Iterator<String> iter) {
        getPopupComponent(); // construct the component.
        boolean different = false;
        Vector<String> v = new Vector<>();
        ListModel<String> model = entryList.getModel();
        for (int i = 0; iter.hasNext(); i++) {
            String next = iter.next();
            v.add(next);
            if (!different && i < model.getSize())
                different |= !next.equals(model.getElementAt(i));
        }
        different |= model.getSize() != v.size();
        // if things were different, reset the data.
        if (different) {
            entryList.setListData(v);
            entryList.clearSelection();
        }
        entryList.setCurrentText(getText());
        showPopup();
    }
    //----------------------------------------------------------------------------
    // Fields
    //----------------------------------------------------------------------------

    /**
     * Shows the popup.
     */
    private void showPopup() {
        // only show the popup if we're currently visible.
        // due to delay in focus-forwarding & key-pressing events,
        // we may not be visible by the time this is called.
        if (popup == null && entryList.getModel().getSize() > 0) {
            if (isShowing()) {
                Point origin = getLocationOnScreen();
                PopupFactory pf = PopupFactory.getSharedInstance();
                Component parent = this;
                // OSX doesn't handle MOUSE_CLICKED events correctly
                // using medium-weight popups, so we need to force
                // PopupFactory to return a heavy-weight popup.
                // This is done by adding a panel into a Popup, which implicitly
                // adds it into a Popup.HeavyWeightWindow, which PopupFactory happens
                // to check as a condition for returning a heavy-weight popup.
                // In an ideal world, the OSX bug would be fixed.
                // In a less ideal world, Popup & PopupFactory would be written so that
                // outside developers can correctly subclass methods.
                if (OSUtils.isMacOSX()) {
                    parent = new JPanel();
                    new MyPopup(this, parent, 0, 0);
                }
                popup = pf.getPopup(parent, getPopupComponent(), origin.x, origin.y + getHeight() + 1);
                showPending = false;
                popup.show();
            } else {
                showPending = true;
            }
        }
    }

    /**
     * Hides the popup window.
     */
    public void hidePopup() {
        showPending = false;
        if (popup != null) {
            popup.hide();
            popup = null;
        }
    }

    /**
     * Displays the popup window with a list of auto-completable choices,
     * if any exist.
     */
    void autoCompleteInput() {
        String input = getText();
        if (input != null && input.length() > 0) {
            Iterator<String> it = dict.iterator(input);
            if (it.hasNext())
                showPopup(it);
            else
                hidePopup();
        } else {
            hidePopup();
        }
    }

    /**
     * Base Action that all LimeTextField actions extend.
     */
    private static abstract class FieldAction extends AbstractAction {
        /**
         * Constructs a new FieldAction looking up the name from the MessagesBundles.
         */
        FieldAction(String name) {
            super(I18n.tr(name));
        }

        /**
         * Gets the LimeTextField for the given ActionEvent.
         */
        SearchField getField(ActionEvent e) {
            JMenuItem source = (JMenuItem) e.getSource();
            JPopupMenu menu = (JPopupMenu) source.getParent();
            return (SearchField) menu.getInvoker();
        }
    }

    /**
     * Subclass that provides access to the constructor.
     */
    private static class MyPopup extends Popup {
        MyPopup(Component owner, Component contents, int x, int y) {
            super(owner, contents, x, y);
        }
    }

    /**
     * Component that clears the history of the dictionary when clicked.
     */
    private class ClearHistory extends JButton {
        /**
         *
         */
        private static final long serialVersionUID = 601999394867955024L;

        ClearHistory() {
            super(I18n.tr("Clear History"));
            enableEvents(AWTEvent.MOUSE_EVENT_MASK);
            setFocusable(false);
        }

        protected void processMouseEvent(MouseEvent me) {
            super.processMouseEvent(me);
            if (me.getID() == MouseEvent.MOUSE_CLICKED) {
                SearchField.this.dict.clear();
                hidePopup();
            }
        }
    }

    /**
     * A list that's used to show auto-complete items.
     */
    protected class AutoCompleteList extends JList<String> {
        /**
         *
         */
        private static final long serialVersionUID = -7324769835640667828L;
        private String currentText;

        AutoCompleteList() {
            super();
            enableEvents(AWTEvent.MOUSE_EVENT_MASK);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            setFocusable(false);
            if (OSUtils.isWindows()) {
                setFont(ThemeMediator.DIALOG_FONT);
            }
        }

        /**
         * Sets the text field's selection with the clicked item.
         */
        protected void processMouseEvent(MouseEvent me) {
            super.processMouseEvent(me);
            if (me.getID() == MouseEvent.MOUSE_CLICKED) {
                int idx = locationToIndex(me.getPoint());
                if (idx != -1 && isSelectedIndex(idx)) {
                    String selection = getSelectedValue();
                    SearchField.this.setText(selection);
                    SearchField.this.hidePopup();
                }
            }
        }

        /**
         * Sets the text to place in the text field when items are unselected.
         */
        void setCurrentText(String text) {
            currentText = text;
        }

        /**
         * Increments the selection by one.
         */
        void incrementSelection() {
            if (getSelectedIndex() == getModel().getSize() - 1) {
                SearchField.this.setText(currentText);
                clearSelection();
            } else {
                int selectedIndex = getSelectedIndex() + 1;
                setSelectedIndex(selectedIndex);
                ensureIndexIsVisible(selectedIndex);
                SearchField.this.setText(getSelectedValue());
            }
        }

        /**
         * Decrements the selection by one.
         */
        void decrementSelection() {
            if (getSelectedIndex() == 0) {
                SearchField.this.setText(currentText);
                clearSelection();
            } else {
                int selectedIndex = getSelectedIndex();
                if (selectedIndex == -1)
                    selectedIndex = getModel().getSize() - 1;
                else
                    selectedIndex--;
                setSelectedIndex(selectedIndex);
                ensureIndexIsVisible(selectedIndex);
                SearchField.this.setText(getSelectedValue());
            }
        }

        /**
         * Sets the size according to the number of entries.
         */
        public Dimension getPreferredScrollableViewportSize() {
            int width = SearchField.this.getSize().width - 2;
            int rows = Math.min(getModel().getSize(), 8);
            int height = rows * getCellBounds(0, 0).height;
            return new Dimension(width, height);
        }
    }
}
