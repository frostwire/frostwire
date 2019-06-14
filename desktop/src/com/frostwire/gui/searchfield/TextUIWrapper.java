package com.frostwire.gui.searchfield;

import javax.swing.*;
import javax.swing.plaf.TextUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.JTextComponent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * TODO:
 *
 * @param <UI>
 * @author Peter Weishapl <petw@gmx.net>
 */
public abstract class TextUIWrapper<UI extends TextUI> {
    private static final DefaultWrapper defaultWrapper = new DefaultWrapper();
    private final TextUIChangeHandler uiChangeHandler = new TextUIChangeHandler();
    private final Class<UI> wrapperClass;

    TextUIWrapper(Class<UI> wrapperClass) {
        this.wrapperClass = wrapperClass;
    }

    public static TextUIWrapper<? extends PromptTextUI> getDefaultWrapper() {
        return defaultWrapper;
    }

    /**
     * <p>
     * Wraps and replaces the current UI of the given <code>textComponent</code>,
     * by calling {@link #wrapUI(JTextComponent)} if necessary.
     * </p>
     *
     * @param textComponent
     * @param stayOnUIChange if <code>true</code>, a {@link PropertyChangeListener} is
     *                       registered, which listens for UI changes and wraps any new UI
     *                       object.
     */
    public final void install(final JTextComponent textComponent, boolean stayOnUIChange) {
        replaceUIIfNeeded(textComponent);
        if (stayOnUIChange) {
            uiChangeHandler.install(textComponent);
        }
    }

    /**
     * Wraps and replaces the text components current UI by calling
     * , if the text components current UI is not an
     * instance of the given wrapper class.
     *
     * @param textComponent
     * @return <code>true</code> if the UI has been replaced
     */
    boolean replaceUIIfNeeded(JTextComponent textComponent) {
        if (wrapperClass.isAssignableFrom(textComponent.getUI().getClass())) {
            return false;
        }
        textComponent.setUI(wrapUI(textComponent));
        return true;
    }

    /**
     * Override to return the appropriate UI wrapper object for the given
     * {@link TextUI}.
     *
     * @return the wrapping UI
     */
    protected abstract UI wrapUI(JTextComponent textComponent);

    /**
     * <p>
     * Removes the {@link PropertyChangeListener}, which listens for "UI"
     * property changes (if installed) and then calls
     * {@link JComponent#updateUI()} on the <code>textComponent</code> to set
     * the UI object provided by the current {@link UIDefaults}.
     * </p>
     *
     * @param textComponent
     */
    public final void uninstall(final JTextComponent textComponent) {
        uiChangeHandler.uninstall(textComponent);
        textComponent.updateUI();
    }

    public static final class DefaultWrapper extends TextUIWrapper<PromptTextUI> {
        private DefaultWrapper() {
            super(PromptTextUI.class);
        }

        /**
         * <p>
         * Creates a new {@link PromptTextUI}, which wraps the given
         * <code>textUI</code>.
         * </p>
         * <p>
         * If <code>textUI</code> is of type {@link PromptTextUI},
         * <code>textUI</code> will be returned. If <code>textUI</code> is
         * of type {@link BasicTextFieldUI} a {@link BuddyTextFieldUI} object
         * will be returned. If <code>textUI</code> is of type
         * {@link BasicTextAreaUI} a {@link PromptTextAreaUI} object will be
         * returned.
         * </p>
         *
         * @param textComponent wrap this components UI
         * @return a {@link PromptTextUI} which wraps <code>textUI</code>
         */
        @Override
        public PromptTextUI wrapUI(JTextComponent textComponent) {
            TextUI textUI = textComponent.getUI();
            if (textUI instanceof PromptTextUI) {
                return (PromptTextUI) textUI;
            } else if (textUI instanceof BasicTextFieldUI) {
                if (textComponent instanceof JXSearchField) {
                    return new SearchFieldUI(textUI);
                } else {
                    return new BuddyTextFieldUI(textUI);
                }
            }
            throw new IllegalArgumentException();
        }

        /**
         * Every time the UI needs to be replaced we also need to make sure,
         * that all buddy components are also in the component hierarchy.
         * (That's because {@link BasicTextUI} removes all our buddies upon UI
         * changes).
         */
        @Override
        protected boolean replaceUIIfNeeded(JTextComponent textComponent) {
            boolean replaced = super.replaceUIIfNeeded(textComponent);
            if (replaced && textComponent instanceof JTextField) {
                BuddySupport.ensureBuddiesAreInComponentHierarchy((JTextField) textComponent);
            }
            return replaced;
        }
    }

    private final class TextUIChangeHandler extends AbstractUIChangeHandler {
        public void propertyChange(PropertyChangeEvent evt) {
            JTextComponent txt = (JTextComponent) evt.getSource();
            replaceUIIfNeeded(txt);
        }
    }
}
