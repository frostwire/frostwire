package com.frostwire.gui.searchfield;

import com.frostwire.gui.theme.ThemeMediator;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * A text field with a find icon in which the user enters text that identifies
 * items to search for.
 * <p>
 * JXSearchField almost looks and behaves like a native Windows Vista search
 * box, a Mac OS X search field, or a search field like the one used in Mozilla
 * Thunderbird 2.0 - depending on the current look and feel.
 * <p>
 * JXSearchField is a text field that contains a find button and a cancel
 * button. The find button normally displays a lens icon appropriate for the
 * current look and feel. The cancel button is used to clear the text and
 * therefore only visible when text is present. It normally displays a 'x' like
 * icon. Text can also be cleared, using the 'Esc' key.
 * <p>
 * The position of the find and cancel buttons can be customized by either
 * changing the search fields (text) margin or button margin, or by changing the
 * {@link LayoutStyle}.
 * <p>
 * JXSearchField supports two different search modes: {@link SearchMode#INSTANT}
 * and {@link SearchMode#REGULAR}.
 * <p>
 * A search can be performed by registering an {@link ActionListener}. The
 * {@link ActionEvent}s command property contains the text to search for. The
 * search should be cancelled, when the command text is empty or null.
 *
 * @author Peter Weishapl <petw@gmx.net>
 * @see RecentSearches
 */
public class JXSearchField extends JXTextField {
    private static final long serialVersionUID = -6555528038058656677L;
    /**
     * The default instant search delay.
     */
    private static final int DEFAULT_INSTANT_SEARCH_DELAY = 180;
    /**
     * The key used to invoke the cancel action.
     */
    private static final KeyStroke CANCEL_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

    // ensure at least the default ui is registered
    static {
        new JXSearchFieldAddon().addDefaults();
    }

    private JButton findButton;
    private JButton cancelButton;
    private JButton popupButton;
    private LayoutStyle layoutStyle;
    private SearchMode searchMode = SearchMode.INSTANT;
    private boolean useSeperatePopupButton;
    private boolean useSeperatePopupButtonSet;
    private boolean layoutStyleSet;
    private int instantSearchDelay = DEFAULT_INSTANT_SEARCH_DELAY;
    private boolean promptFontStyleSet;
    private Timer instantSearchTimer;

    /**
     * Creates a new search field with a default prompt.
     */
    JXSearchField() {
        this("");//UIManagerExt.getString("SearchField.prompt"));
    }

    /**
     * Creates a new search field with the given prompt and
     * {@link SearchMode#INSTANT}.
     */
    private JXSearchField(String prompt) {
        super(prompt);
        // use the native search field if possible.
        boolean isDarkTheme = ThemeMediator.isDarkLafThemeOn();
        setUseNativeSearchFieldIfPossible(!isDarkTheme);
        // install default actions
        setCancelAction(new ClearAction());
        setFindAction(new FindAction());
        // We cannot register the ClearAction through the Input- and
        // ActionMap because ToolTipManager registers the escape key with an
        // action that hides the tooltip every time the tooltip is changed and
        // then the ClearAction will never be called.
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (CANCEL_KEY.equals(KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiersEx()))) {
                    getCancelAction().actionPerformed(
                            new ActionEvent(JXSearchField.this, e.getID(), KeyEvent.getKeyText(e.getKeyCode())));
                }
            }
        });
        // Map specific native properties to general JXSearchField properties.
        addPropertyChangeListener(NativeSearchFieldSupport.FIND_POPUP_PROPERTY, evt -> {
            JPopupMenu oldPopup = (JPopupMenu) evt.getOldValue();
            firePropertyChange("findPopupMenu", oldPopup, evt.getNewValue());
        });
        addPropertyChangeListener(NativeSearchFieldSupport.CANCEL_ACTION_PROPERTY, evt -> {
            ActionListener oldAction = (ActionListener) evt.getOldValue();
            firePropertyChange("cancelAction", oldAction, evt.getNewValue());
        });
        addPropertyChangeListener(NativeSearchFieldSupport.FIND_ACTION_PROPERTY, evt -> {
            ActionListener oldAction = (ActionListener) evt.getOldValue();
            firePropertyChange("findAction", oldAction, evt.getNewValue());
        });
    }

    /**
     * Returns the current {@link SearchMode}.
     *
     * @return the current {@link SearchMode}.
     */
    private SearchMode getSearchMode() {
        return searchMode;
    }

    /**
     * Sets the current search mode. See {@link SearchMode} for a description of
     * the different search modes.
     *
     * @param searchMode {@link SearchMode#INSTANT} or {@link SearchMode#REGULAR}
     */
    public void setSearchMode(SearchMode searchMode) {
        firePropertyChange("searchMode", this.searchMode, this.searchMode = searchMode);
    }

    /**
     * Returns <code>true</code> if the current {@link SearchMode} is
     * {@link SearchMode#INSTANT}.
     *
     * @return <code>true</code> if the current {@link SearchMode} is
     * {@link SearchMode#INSTANT}
     */
    boolean isInstantSearchMode() {
        return SearchMode.INSTANT.equals(getSearchMode());
    }

    /**
     * Returns <code>true</code> if the current {@link SearchMode} is
     * {@link SearchMode#REGULAR}.
     *
     * @return <code>true</code> if the current {@link SearchMode} is
     * {@link SearchMode#REGULAR}
     */
    boolean isRegularSearchMode() {
        return SearchMode.REGULAR.equals(getSearchMode());
    }

    /**
     * Get the instant search delay in milliseconds. The default delay is 50
     * Milliseconds.
     *
     * @return the instant search delay in milliseconds
     */
    int getInstantSearchDelay() {
        return instantSearchDelay;
    }

    /**
     * Set the instant search delay in milliseconds. In
     * {@link SearchMode#INSTANT}, when the user changes the text, an action
     * event will be fired after the specified instant search delay.
     * <p>
     * It is recommended to use a instant search delay to avoid the firing of
     * unnecessary events. For example when the user replaces the whole text
     * with a different text the search fields underlying {@link Document}
     * typically fires 2 document events. The first one, because the old text is
     * removed and the second one because the new text is inserted. If the
     * instant search delay is 0, this would result in 2 action events being
     * fired. When a instant search delay is used, the first document event
     * typically is ignored, because the second one is fired before the delay is
     * over, which results in a correct behavior because only the last and only
     * relevant event will be delivered.
     */
    public void setInstantSearchDelay(int instantSearchDelay) {
        firePropertyChange("instantSearchDelay", this.instantSearchDelay, this.instantSearchDelay = instantSearchDelay);
    }

    /**
     * Get the current {@link LayoutStyle}.
     */
    LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    /**
     * Set the current {@link LayoutStyle}. See {@link LayoutStyle} for a
     * description of how this affects layout and behavior of the search field.
     *
     * @param layoutStyle {@link LayoutStyle#MAC} or {@link LayoutStyle#VISTA}
     */
    private void setLayoutStyle(LayoutStyle layoutStyle) {
        layoutStyleSet = true;
        firePropertyChange("layoutStyle", this.layoutStyle, this.layoutStyle = layoutStyle);
    }

    /**
     * Returns <code>true</code> if the current {@link LayoutStyle} is
     * {@link LayoutStyle#VISTA}.
     */
    boolean isVistaLayoutStyle() {
        return LayoutStyle.VISTA.equals(getLayoutStyle());
    }

    /**
     * Returns <code>true</code> if the current {@link LayoutStyle} is
     * {@link LayoutStyle#MAC}.
     */
    boolean isMacLayoutStyle() {
        return LayoutStyle.MAC.equals(getLayoutStyle());
    }

    /**
     * Set the margin space around the search field's text.
     *
     * @see javax.swing.text.JTextComponent#setMargin(java.awt.Insets)
     */
    public void setMargin(Insets m) {
        super.setMargin(m);
    }

    /**
     * Returns the cancel action, or an instance of {@link ClearAction}, if
     * none has been set.
     *
     * @return the cancel action
     */
    private ActionListener getCancelAction() {
        ActionListener a = NativeSearchFieldSupport.getCancelAction(this);
        if (a == null) {
            a = new ClearAction();
        }
        return a;
    }

    /**
     * Sets the action that is invoked, when the user presses the 'Esc' key or
     * clicks the cancel button.
     */
    private void setCancelAction(ActionListener cancelAction) {
        NativeSearchFieldSupport.setCancelAction(this, cancelAction);
    }

    /**
     * Returns the cancel button.
     * <p>
     * Calls {@link #createCancelButton()} to create the cancel button and
     * registers an {@link ActionListener} that delegates actions to the
     * {@link ActionListener} returned by {@link #getCancelAction()}, if
     * needed.
     *
     * @return the cancel button
     */
    final JButton getCancelButton() {
        if (cancelButton == null) {
            cancelButton = createCancelButton();
            cancelButton.addActionListener(e -> getCancelAction().actionPerformed(e));
        }
        return cancelButton;
    }

    /**
     * Creates and returns the cancel button.
     * <p>
     * Override to use a custom cancel button.
     *
     * @return the cancel button
     * @see #getCancelButton()
     */
    private JButton createCancelButton() {
        return new BuddyButton();
    }

    /**
     * Returns the action that is invoked when the enter key is pressed or the
     * find button is clicked. If no action has been set, a new instance of
     * {@link FindAction} will be returned.
     *
     * @return the find action
     */
    private ActionListener getFindAction() {
        ActionListener a = NativeSearchFieldSupport.getFindAction(this);
        if (a == null) {
            a = new FindAction();
        }
        return a;
    }

    /**
     * Sets the action that is invoked when the enter key is pressed or the find
     * button is clicked.
     *
     * @return the find action
     */
    private void setFindAction(ActionListener findAction) {
        NativeSearchFieldSupport.setFindAction(this, findAction);
    }

    /**
     * Returns the find button.
     * <p>
     * Calls {@link #createFindButton()} to create the find button and registers
     * an {@link ActionListener} that delegates actions to the
     * {@link ActionListener} returned by {@link #getFindAction()}, if needed.
     *
     * @return the find button
     */
    final JButton getFindButton() {
        if (findButton == null) {
            findButton = createFindButton();
            findButton.addActionListener(e -> getFindAction().actionPerformed(e));
        }
        return findButton;
    }

    /**
     * Creates and returns the find button. The buttons action is set to the
     * action returned by getSearchAction().
     * <p>
     * Override to use a custom find button.
     *
     * @return the find button
     * @see #getFindButton()
     */
    private JButton createFindButton() {
        return new BuddyButton();
    }

    /**
     * Returns the popup button. If a find popup menu is set, it will be
     * displayed when this button is clicked.
     * <p>
     * This button will only be visible, if {@link #isUseSeperatePopupButton()}
     * returns <code>true</code>. Otherwise the popup menu will be displayed
     * when the find button is clicked.
     *
     * @return the popup button
     */
    final JButton getPopupButton() {
        if (popupButton == null) {
            popupButton = createPopupButton();
        }
        return popupButton;
    }

    /**
     * Creates and returns the popup button. Override to use a custom popup
     * button.
     *
     * @return the popup button
     * @see #getPopupButton()
     */
    private JButton createPopupButton() {
        return new BuddyButton();
    }

    /**
     * Returns <code>true</code> if the popup button should be visible and
     * used for displaying the find popup menu. Otherwise, the find popup menu
     * will be displayed when the find button is clicked.
     *
     * @return <code>true</code> if the popup button should be used
     */
    boolean isUseSeperatePopupButton() {
        return useSeperatePopupButton;
    }

    /**
     * Set if the popup button should be used for displaying the find popup
     * menu.
     */
    private void setUseSeparatePopupButton(boolean useSeperatePopupButton) {
        useSeperatePopupButtonSet = true;
        firePropertyChange("useSeperatePopupButton", this.useSeperatePopupButton,
                this.useSeperatePopupButton = useSeperatePopupButton);
    }

    private void setUseNativeSearchFieldIfPossible(@SuppressWarnings("SameParameterValue") boolean useNativeSearchFieldIfPossible) {
        TextUIWrapper.getDefaultWrapper().uninstall(this);
        NativeSearchFieldSupport.setSearchField(this, useNativeSearchFieldIfPossible);
        TextUIWrapper.getDefaultWrapper().install(this, true);
        updateUI();
    }

    /**
     * Updates the cancel, find and popup buttons enabled state in addition to
     * setting the search fields editable state.
     *
     * @see #updateButtonState()
     * @see javax.swing.text.JTextComponent#setEditable(boolean)
     */
    public void setEditable(boolean b) {
        super.setEditable(b);
        updateButtonState();
    }

    /**
     * Updates the cancel, find and popup buttons enabled state in addition to
     * setting the search fields enabled state.
     *
     * @see #updateButtonState()
     * @see javax.swing.text.JTextComponent#setEnabled(boolean)
     */
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateButtonState();
    }

    /**
     * Enables the cancel action if this search field is editable and enabled,
     * otherwise it will be disabled. Enabled the search action and popup button
     * if this search field is enabled, otherwise it will be disabled.
     */
    private void updateButtonState() {
        getCancelButton().setEnabled(isEditable() & isEnabled());
        getFindButton().setEnabled(isEnabled());
        getPopupButton().setEnabled(isEnabled());
    }

    /**
     * Returns the find popup menu.
     *
     * @return the find popup menu
     * @see #setFindPopupMenu(JPopupMenu)
     */
    JPopupMenu getFindPopupMenu() {
        return NativeSearchFieldSupport.getFindPopupMenu(this);
    }

    /**
     * Sets the popup menu that will be displayed when the popup button is
     * clicked. If a find popup menu is set and
     * {@link #isUseSeperatePopupButton()} returns <code>false</code>, the
     * popup button will be displayed instead of the find button. Otherwise the
     * popup button will be displayed in addition to the find button.
     * <p>
     * The find popup menu is managed using {@link NativeSearchFieldSupport} to
     * achieve compatibility with the native search field support provided by
     * the Mac Look And Feel since Mac OS 10.5.
     * <p>
     * If a recent searches save key has been set and therefore a recent
     * searches popup menu is installed, this method does nothing. You must
     * first remove the recent searches save key, by calling
     *
     * @param findPopupMenu the popup menu, which will be displayed when the popup button
     *                      is clicked
     */
    void setFindPopupMenu(JPopupMenu findPopupMenu) {
        if (isManagingRecentSearches()) {
            return;
        }
        NativeSearchFieldSupport.setFindPopupMenu(this, findPopupMenu);
    }

    private boolean isManagingRecentSearches() {
        return false;
    }

    /**
     * Returns the {@link Timer} used to delay the firing of action events in
     * instant search mode when the user enters text.
     * <p>
     * This timer calls {@link #postActionEvent()}.
     *
     * @return the {@link Timer} used to delay the firing of action events
     */
    Timer getInstantSearchTimer() {
        if (instantSearchTimer == null) {
            instantSearchTimer = new Timer(0, e -> postActionEvent());
            instantSearchTimer.setRepeats(false);
        }
        return instantSearchTimer;
    }

    /**
     * Returns <code>true</code> if this search field is the focus owner or
     * the find popup menu is visible.
     * <p>
     * This is a hack to make the search field paint the focus indicator in Mac
     * OS X Aqua when the find popup menu is visible.
     *
     * @return <code>true</code> if this search field is the focus owner or
     * the find popup menu is visible
     */
    public boolean hasFocus() {
        if (getFindPopupMenu() != null && getFindPopupMenu().isVisible()) {
            return true;
        }
        return super.hasFocus();
    }

    /**
     * Overriden to also update the find popup menu if set.
     */
    public void updateUI() {
        super.updateUI();
        if (getFindPopupMenu() != null) {
            SwingUtilities.updateComponentTreeUI(getFindPopupMenu());
        }
    }

    /**
     * Hack to enable the UI delegate to set default values depending on the
     * current Look and Feel, without overriding custom values.
     */
    public void setPromptFontStyle(Integer fontStyle) {
        super.setPromptFontStyle(fontStyle);
        promptFontStyleSet = true;
    }

    /**
     * Hack to enable the UI delegate to set default values depending on the
     * current Look and Feel, without overriding custom values.
     *
     * @param propertyName the name of the property to change
     * @param value        the new value of the property
     */
    void customSetUIProperty(String propertyName, Object value) {
        customSetUIProperty(propertyName, value, false);
    }

    /**
     * Hack to enable the UI delegate to set default values depending on the
     * current Look and Feel, without overriding custom values.
     *
     * @param propertyName the name of the property to change
     * @param value        the new value of the property
     * @param override     override custom values
     */
    private void customSetUIProperty(String propertyName, Object value, @SuppressWarnings("SameParameterValue") boolean override) {
        if ("useSeperatePopupButton".equals(propertyName)) {
            if (!useSeperatePopupButtonSet || override) {
                setUseSeparatePopupButton((Boolean) value);
                useSeperatePopupButtonSet = false;
            }
        } else if ("layoutStyle".equals(propertyName)) {
            if (!layoutStyleSet || override) {
                setLayoutStyle(LayoutStyle.valueOf(value.toString()));
                layoutStyleSet = false;
            }
        } else if ("promptFontStyle".equals(propertyName)) {
            if (!promptFontStyleSet || override) {
                setPromptFontStyle((Integer) value);
                promptFontStyleSet = false;
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Overriden to prevent any delayed {@link ActionEvent}s from being sent
     * after posting this action.
     * <p>
     * For example, if the current {@link SearchMode} is
     * {@link SearchMode#INSTANT} and the instant search delay is greater 0. The
     * user enters some text and presses enter. This method will be invoked
     * immediately because the users presses enter. However, this method would
     * be invoked after the instant search delay, if we would not prevent it
     * here.
     */
    public void postActionEvent() {
        getInstantSearchTimer().stop();
        super.postActionEvent();
    }

    /**
     * Defines, how the find and cancel button are layouted.
     */
    public enum LayoutStyle {
        /**
         * <p>
         * In VISTA layout style, the find button is placed on the right side of
         * the search field. If text is entered, the find button is replaced by
         * the cancel button when the actual search mode is
         * {@link SearchMode#INSTANT}. When the search mode is
         * {@link SearchMode#REGULAR} the find button will always stay visible
         * and the cancel button will never be shown. However, 'Escape' can
         * still be pressed to clear the text.
         * </p>
         */
        VISTA,
        /**
         * <p>
         * In MAC layout style, the find button is placed on the left side of
         * the search field and the cancel button on the right side. The cancel
         * button is only visible when text is present.
         * </p>
         */
        MAC
    }

    /**
     * Defines when action events are posted.
     */
    public enum SearchMode {
        /**
         * <p>
         * In REGULAR search mode, an action event is fired, when the user
         * presses enter or clicks the find button.
         * </p>
         * <p>
         * However, if a find popup menu is set and layout style is
         * {@link LayoutStyle#MAC}, no action will be fired, when the find
         * button is clicked, because instead the popup menu is shown. A search
         * can therefore only be triggered, by pressing the enter key.
         * </p>
         * <p>
         * The find button can have a rollover and a pressed icon, defined by
         * the "SearchField.rolloverIcon" and "SearchField.pressedIcon" UI
         * properties. When a find popup menu is set,
         * "SearchField.popupRolloverIcon" and "SearchField.popupPressedIcon"
         * are used.
         * </p>
         */
        REGULAR,
        /**
         * In INSTANT search mode, an action event is fired, when the user
         * presses enter or changes the search text.
         * <p>
         * The action event is delayed about the number of milliseconds
         * specified by {@link JXSearchField#getInstantSearchDelay()}.
         * <p>
         * No rollover and pressed icon is used for the find button.
         */
        INSTANT
    }

    /**
     * Invoked when the cancel button or the 'Esc' key is pressed. Sets the
     * text in the search field to <code>null</code>.
     */
    class ClearAction extends AbstractAction {
        private static final long serialVersionUID = -3349455592229414653L;

        ClearAction() {
            putValue(SHORT_DESCRIPTION, "Clear Search Text");
        }

        /**
         * Calls {@link #clear()}.
         */
        public void actionPerformed(ActionEvent e) {
            clear();
        }

        /**
         * Sets the search field's text to <code>null</code> and requests the
         * focus for the search field.
         */
        void clear() {
            setText(null);
            requestFocusInWindow();
        }
    }

    /**
     * Invoked when the find button is pressed.
     */
    public class FindAction extends AbstractAction {
        private static final long serialVersionUID = 6512288404613230232L;

        FindAction() {
        }

        /**
         * In regular search mode posts an action event if the search field is
         * the focus owner.
         * <p>
         * Also requests the focus for the search field and selects the whole
         * text.
         */
        public void actionPerformed(ActionEvent e) {
            if (isFocusOwner() && isRegularSearchMode()) {
                postActionEvent();
            }
            requestFocusInWindow();
            selectAll();
        }
    }
}
