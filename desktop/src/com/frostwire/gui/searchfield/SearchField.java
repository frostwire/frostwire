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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.text.Document;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import org.limewire.collection.AutoCompleteDictionary;
import org.limewire.collection.StringTrieSet;
import org.limewire.util.OSUtils;

import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UISettings;

public class SearchField extends JXSearchField {

    /**
     * The sole JPopupMenu that's shared among all the text fields.
     */
    private static JPopupMenu POPUP;

    /**
     * Our UndoManager.
     */
    private UndoManager undoManager;

    public SearchField() {
        init();
    }

    /**
     * Undoes the last action.
     */
    public void undo() {
        try {
            if (undoManager != null)
                undoManager.undoOrRedo();
        } catch (CannotUndoException ignored) {
        } catch (CannotRedoException ignored) {
        }
    }

    /**
     * Sets the UndoManager (but does NOT add it to the document).
     */
    public void setUndoManager(UndoManager undoer) {
        undoManager = undoer;
    }

    /**
     * Gets the undo manager.
     */
    public UndoManager getUndoManager() {
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

    /**
     * Determines if paste is currently available.
     */
    private boolean isPasteAvailable() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor);
        } catch (UnsupportedOperationException he) {
            return false;
        } catch (IllegalStateException ise) {
            return false;
        }
    }

    /**
     * Base Action that all LimeTextField actions extend.
     */
    private static abstract class FieldAction extends AbstractAction {

        /**
         * Constructs a new FieldAction looking up the name from the MessagesBundles.
         */
        public FieldAction(String name) {
            super(I18n.tr(name));
        }

        /**
         * Gets the LimeTextField for the given ActionEvent.
         */
        protected SearchField getField(ActionEvent e) {
            JMenuItem source = (JMenuItem) e.getSource();
            JPopupMenu menu = (JPopupMenu) source.getParent();
            return (SearchField) menu.getInvoker();
        }
    }

    /**
     * The undo action.
     */
    private static Action UNDO_ACTION = new FieldAction(I18n.tr("Undo")) {

        public void actionPerformed(ActionEvent e) {
            getField(e).undo();
        }
    };

    /**
     * The cut action
     */
    private static Action CUT_ACTION = new FieldAction(I18n.tr("Cut")) {

        public void actionPerformed(ActionEvent e) {
            getField(e).cut();
        }
    };

    /**
     * The copy action.
     */
    private static Action COPY_ACTION = new FieldAction(I18n.tr("Copy")) {

        public void actionPerformed(ActionEvent e) {
            getField(e).copy();
        }
    };

    /**
     * The paste action.
     */
    private static Action PASTE_ACTION = new FieldAction(I18n.tr("Paste")) {

        public void actionPerformed(ActionEvent e) {
            getField(e).paste();
        }
    };

    /**
     * The delete action.
     */
    private static Action DELETE_ACTION = new FieldAction(I18n.tr("Delete")) {

        public void actionPerformed(ActionEvent e) {
            getField(e).replaceSelection("");
        }
    };

    /**
     * The select all action.
     */
    private static Action SELECT_ALL_ACTION = new FieldAction(I18n.tr("Select All")) {

        public void actionPerformed(ActionEvent e) {
            getField(e).selectAll();
        }
    };

    ///// from AutoCompleteTextField

    //----------------------------------------------------------------------------
    // Public methods
    //----------------------------------------------------------------------------

    /**
    * Set the dictionary that autocomplete lookup should be performed by.
    *
    * @param dict The dictionary that will be used for the autocomplete lookups.
    */
    public void setDictionary(AutoCompleteDictionary dict) {
        // lazily create the listeners
        if (this.dict == null)
            setUp();
        this.dict = dict;
    }

    /**
    * Gets the dictionary currently used for lookups.
    *
    * @return dict The dictionary that will be used for the autocomplete lookups.
    */
    public AutoCompleteDictionary getDictionary() {
        return dict;
    }

    /**
    * Creates the default dictionary object
    */
    public AutoCompleteDictionary createDefaultDictionary() {
        return new StringTrieSet(true);
    }

    /**
    * Sets whether the component is currently performing autocomplete lookups as
    * keystrokes are performed.
    *
    * @param val True or false.
    */
    public void setAutoComplete(boolean val) {
        UISettings.AUTOCOMPLETE_ENABLED.setValue(val);
    }

    /**
    * Gets whether the component is currently performing autocomplete lookups as
    * keystrokes are performed. Looks up the value in UISettings.
    *
    * @return True or false.
    */
    public boolean getAutoComplete() {
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

    /**
     * Adds the specified string to the underlying dictionary
     */
    public void addToDictionary(String s) {
        if (!getAutoComplete())
            return;

        if (dict == null) {
            setUp();
            this.dict = createDefaultDictionary();
        }
        dict.addEntry(s.trim());
    }

    //----------------------------------------------------------------------------
    // Protected methods
    //----------------------------------------------------------------------------

    protected String lookup(String s) {
        if (dict != null && getAutoComplete() && !s.equals(""))
            return dict.lookup(s);
        return null;
    }

    //----------------------------------------------------------------------------
    // Fields
    //----------------------------------------------------------------------------
    protected AutoCompleteDictionary dict;

    /// from ClearableAutoComplete

    /**
     * Fires an action event.
     *
     * If the popup is visible, this resets the current
     * text to be the selection on the popup (if something was selected)
     * prior to firing the event.
     */
    protected void fireActionPerformed() {
        if (popup != null) {
            String selection = (String) entryList.getSelectedValue();
            if (selection != null) {
                hidePopup();
                setText(selection);
                return;
            }
        }

        super.fireActionPerformed();
    }

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
                switch (evt.getKeyChar()) {
                case KeyEvent.VK_ENTER:
                    break;
                default:
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

    //----------------------------------------------------------------------------
    // Protected methods
    //----------------------------------------------------------------------------

    // overwritten to disable
    protected void setUp() {
    }

    /**
     * Gets the component that is the popup listing other choices.
     */
    protected JComponent getPopupComponent() {
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
    protected void showPopup(Iterator<String> iter) {
        getPopupComponent(); // construct the component.

        boolean different = false;
        Vector<String> v = new Vector<String>();
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

    /**
     * Shows the popup.
     */
    public void showPopup() {
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
    public void autoCompleteInput() {
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

    //----------------------------------------------------------------------------
    // Fields
    //----------------------------------------------------------------------------
    /** The list auto-completable items are shown in */
    protected AutoCompleteList entryList;
    /** The panel the popup is shown in. */
    protected JPanel entryPanel;
    /** The popup the scroll pane is in */
    protected Popup popup;
    /** Whether or not we tried to show a popup while this wasn't showing */
    protected boolean showPending;

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

        public AutoCompleteList() {
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
                    String selection = (String) getSelectedValue();
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
                SearchField.this.setText((String) getSelectedValue());
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
                SearchField.this.setText((String) getSelectedValue());
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

    /**
     * Subclass that provides access to the constructor.
     */
    private static class MyPopup extends Popup {
        public MyPopup(Component owner, Component contents, int x, int y) {
            super(owner, contents, x, y);
        }
    }
}
