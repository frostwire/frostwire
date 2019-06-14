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

package com.limegroup.gnutella.gui.init;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.URLLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * This abstract class creates a <tt>JPanel</tt> that uses
 * <tt>BoxLayout</tt> for setup windows.  It defines many of the
 * basic accessor and mutator methods required by subclasses.
 */
abstract class SetupWindow extends JPanel {
    /**
     * The width of the setup window.
     */
    static final int SETUP_WIDTH = 700;
    /**
     * The height of the setup window.
     */
    static final int SETUP_HEIGHT = 520;
    /**
     * Constant handle to the setup manager mediator class.
     */
    final SetupManager _manager;
    /**
     * Variable for the name of this window for use with <tt>CardLayout</tt>.
     */
    private final String _key;
    /**
     * Variable for the key of the label to display.
     */
    private final String _labelKey;
    /**
     * Variable for the URL where more info exists.  Null if none.
     */
    private final String _moreInfoURL;
    /**
     * Variable for the next window in the sequence.
     */
    private SetupWindow _next;
    /**
     * Variable for the previous window in the sequence.
     */
    private SetupWindow _previous;

    /**
     * Creates a new setup window with the specified label.
     *
     * @param key      the title of the window for use with <tt>CardLayout</tt>
     *                 and for use in obtaining the locale-specific caption for this
     *                 window
     * @param labelKey the key for locale-specific label to be displayed
     *                 in the window
     */
    SetupWindow(final SetupManager manager, final String key, final String labelKey) {
        this(manager, key, labelKey, null);
    }

    /**
     * Creates a new setup window with the specified label.
     *
     * @param key      the title of the window for use with <tt>CardLayout</tt>
     *                 and for use in obtaining the locale-specific caption for this
     *                 window
     * @param labelKey the key for locale-specific label to be displayed
     *                 in the window
     */
    SetupWindow(SetupManager manager, String key, String labelKey, String moreInfoURL) {
        _manager = manager;
        _key = key;
        _labelKey = labelKey;
        _moreInfoURL = moreInfoURL;
    }

    void createWindow() {
        removeAll();
        setLayout(new BorderLayout());
        JPanel jpTop = new JPanel();
        jpTop.setLayout(new BorderLayout());
        //jpTop.setBackground(Color.white);
        jpTop.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
        jpTop.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeMediator.LIGHT_BORDER_COLOR));
        add(jpTop, BorderLayout.NORTH);
        JPanel jpTitle = new JPanel(new BorderLayout());
        jpTitle.setOpaque(false);
        jpTop.add(jpTitle, BorderLayout.CENTER);
        JLabel jlTitle = new JLabel(I18n.tr(_key));
        jlTitle.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 5));
        jlTitle.setFont(new Font("Dialog", Font.BOLD, 16));
        //jlTitle.setForeground(Color.black);
        jlTitle.setOpaque(false);
        jpTitle.add(jlTitle, BorderLayout.NORTH);
        MultiLineLabel jtaDescription = new MultiLineLabel(I18n.tr(_labelKey));
        jtaDescription.setOpaque(false);
        jtaDescription.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        //jtaDescription.setForeground(Color.black);
        jtaDescription.setBackground(jpTitle.getBackground());
        jtaDescription.setFont(new Font("Dialog", Font.PLAIN, 12));
        jpTitle.add(jtaDescription, BorderLayout.CENTER);
        if (_moreInfoURL != null) {
            JLabel jlURL = new URLLabel(_moreInfoURL, I18n.tr("Learn more about this option..."));
            jlURL.setOpaque(false);
            jlURL.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            //jlURL.setForeground(Color.black);
            jlURL.setOpaque(false);
            jpTitle.add(jlURL, BorderLayout.SOUTH);
        }
        JLabel jlIcon = new JLabel();
        jlIcon.setOpaque(false);
        jlIcon.setIcon(getIcon());
        jlIcon.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 8));
        jpTop.add(jlIcon, BorderLayout.EAST);
    }

    /**
     * Accessor for the name of the panel
     *
     * @return the unique identifying name for this panel
     */
    public String getName() {
        // GTK L&F calls this method before the constructor has finished,
        // so we can't do a lookup with a null key.
        if (_key == null)
            return null;
        else
            return I18n.tr(_key);
    }

    /**
     * Accessor for the unique identifying key of the window
     * in the <tt>CardLayout</tt>.
     *
     * @return the unique identifying key for the window.
     */
    public String getKey() {
        return _key;
    }

    Icon getIcon() {
        return null;
    }

    /**
     * Accessor for the next panel in the sequence.
     *
     * @return the next window in the sequence
     */
    public SetupWindow getNext() {
        return _next;
    }

    /**
     * Sets the next SetupWindow in the sequence.
     */
    public void setNext(SetupWindow next) {
        _next = next;
    }

    /**
     * Accessor for the previous panel in the sequence.
     *
     * @return the previous window in the sequence
     */
    public SetupWindow getPrevious() {
        return _previous;
    }

    /**
     * Sets the previous SetupWindow in the sequence.
     *
     * @param previous the window to set as the previous window
     */
    public void setPrevious(SetupWindow previous) {
        _previous = previous;
    }

    /**
     * Called each time this window is opened.
     */
    void handleWindowOpeningEvent() {
        createWindow();
        _manager.enableActions(getAppropriateActions());
    }

    int getAppropriateActions() {
        // always enable cancel
        int actions = SetupManager.ACTION_CANCEL;
        if (_next == this) {
            // last page, finish is enabled
            actions |= SetupManager.ACTION_FINISH;
        } else if (_next != null) {
            // not last page, enable next
            actions |= SetupManager.ACTION_NEXT;
        }
        if (_previous != this) {
            // not first page, enable previous
            actions |= SetupManager.ACTION_PREVIOUS;
        }
        return actions;
    }

    /**
     * Applies the settings currently set in this window.
     * <p>
     * If loadCoreComponents is false, core components should not be loaded.
     * This is useful when you just want to temporarily save the current settings,
     * but do not plan on finishing this step immediately.
     *
     * @param loadCoreComponents true if settings should be applied
     *                           AND core components should be loaded.  false if only settings
     *                           should be applied (but components shouldn't be loaded).
     * @throws ApplySettingsException if there was a problem applying the
     *                                settings
     */
    public void applySettings(boolean loadCoreComponents) throws ApplySettingsException {
    }

    /**
     * @param setupComponent the <tt>Component</tt> to add to this window
     */
    void setSetupComponent(JComponent setupComponent) {
        setupComponent.setBorder(new EmptyBorder(20, 10, 10, 10));
        add(setupComponent, BorderLayout.CENTER);
        invalidate();
        validate();
    }

    Dimension calculatePreferredSize() {
        createWindow();
        return getLayout().preferredLayoutSize(this);
    }

    static class MultiLineLabel extends JTextArea {
        /**
         * Creates a label that can have multiple lines and that has the
         * default width.
         *
         * @param s the <tt>String</tt> to display in the label
         */
        MultiLineLabel(String s) {
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setHighlighter(null);
            setText(s);
        }
    }
}
