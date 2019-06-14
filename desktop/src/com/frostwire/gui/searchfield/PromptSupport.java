package com.frostwire.gui.searchfield;

import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * <p>
 * Sets prompt text, foreground, background and {@link FocusBehavior} properties
 * on a JTextComponent by calling
 * {@link JTextComponent#putClientProperty(Object, Object)}. These properties
 * are used by {@link PromptTextUI} instances to render the prompt of a text
 * component.
 * </p>
 *
 * @author Peter Weishapl <petw@gmx.net>
 */
public class PromptSupport {
    /**
     * The prompt text property.
     */
    private static final String PROMPT = "promptText";
    /**
     * The color of the prompt text property.
     */
    private static final String FOREGROUND = "promptForeground";
    /**
     * The prompt background property.
     */
    private static final String BACKGROUND = "promptBackground";
    /**
     * The focus behavior property.
     */
    private static final String FOCUS_BEHAVIOR = "focusBehavior";
    /**
     * The font style property, if different from the components font.
     */
    private static final String FONT_STYLE = "promptFontStyle";

    /**
     * <p>
     * Convenience method to set the <code>promptText</code> and
     * <code>promptTextColor</code> on a {@link JTextComponent}.
     * </p>
     * <p>
     * If <code>stayOnUIChange</code> is true, The prompt support will stay
     * installed, even when the text components UI changes.
     * </p>
     */
    public static void init(String promptText, Color promptForeground, Color promptBackground,
                            final JTextComponent textComponent) {
        if (promptText != null && promptText.length() > 0) {
            setPrompt(promptText, textComponent);
        }
        if (promptForeground != null) {
            setForeground(promptForeground, textComponent);
        }
        if (promptBackground != null) {
            setBackground(promptBackground, textComponent);
        }
    }

    /**
     * Get the {@link FocusBehavior} of <code>textComponent</code>.
     *
     * @return the {@link FocusBehavior} or {@link FocusBehavior#HIDE_PROMPT} if
     * none is set
     */
    static FocusBehavior getFocusBehavior(JTextComponent textComponent) {
        FocusBehavior fb = (FocusBehavior) textComponent.getClientProperty(FOCUS_BEHAVIOR);
        if (fb == null) {
            fb = FocusBehavior.HIDE_PROMPT;
        }
        return fb;
    }

    /**
     * Sets the {@link FocusBehavior} on <code>textComponent</code> and
     * repaints the component to reflect the changes, if it is the focus owner.
     */
    static void setFocusBehavior(FocusBehavior focusBehavior, JTextComponent textComponent) {
        textComponent.putClientProperty(FOCUS_BEHAVIOR, focusBehavior);
        if (textComponent.isFocusOwner()) {
            textComponent.repaint();
        }
    }

    /**
     * Get the prompt text of <code>textComponent</code>.
     */
    static String getPrompt(JTextComponent textComponent) {
        return (String) textComponent.getClientProperty(PROMPT);
    }

    /**
     * <p>
     * Sets the prompt text on <code>textComponent</code>. Also sets the
     * tooltip text to the prompt text if <code>textComponent</code> has no
     * tooltip text or the current tooltip text is the same as the current
     * prompt text.
     * </p>
     */
    public static void setPrompt(String promptText, JTextComponent textComponent) {
        TextUIWrapper.getDefaultWrapper().install(textComponent, true);
        // display prompt as tooltip by default
        if (textComponent.getToolTipText() == null || textComponent.getToolTipText().equals(getPrompt(textComponent))) {
            textComponent.setToolTipText(promptText);
        }
        textComponent.putClientProperty(PROMPT, promptText);
        textComponent.repaint();
    }

    /**
     * Get the foreground color of the prompt text. If no color has been set,
     * the <code>textComponent</code>s disabled text color will be returned.
     */
    public static Color getForeground(JTextComponent textComponent) {
        if (textComponent.getClientProperty(FOREGROUND) == null) {
            return textComponent.getDisabledTextColor();
        }
        return (Color) textComponent.getClientProperty(FOREGROUND);
    }

    /**
     * Sets the foreground color of the prompt on <code>textComponent</code>
     * and repaints the component to reflect the changes. This color will be
     * used when no text is present.
     */
    private static void setForeground(Color promptTextColor, JTextComponent textComponent) {
        textComponent.putClientProperty(FOREGROUND, promptTextColor);
        textComponent.repaint();
    }

    /**
     * Get the background color of the <code>textComponent</code>, when no
     * text is present. If no color has been set, the <code>textComponent</code>s
     * background color will be returned.
     */
    public static Color getBackground(JTextComponent textComponent) {
        if (textComponent.getClientProperty(BACKGROUND) == null) {
            return textComponent.getBackground();
        }
        return (Color) textComponent.getClientProperty(BACKGROUND);
    }

    /**
     * <p>
     * Sets the prompts background color on <code>textComponent</code> and
     * repaints the component to reflect the changes. This background color will
     * only be used when no text is present.
     * </p>
     */
    private static void setBackground(Color background, JTextComponent textComponent) {
        TextUIWrapper.getDefaultWrapper().install(textComponent, true);
        textComponent.putClientProperty(BACKGROUND, background);
        textComponent.repaint();
    }

    /**
     * <p>
     * Set the style of the prompt font, if different from the
     * <code>textComponent</code>s font.
     * </p>
     * <p>
     * Allowed values are {@link Font#PLAIN}, {@link Font#ITALIC},
     * {@link Font#BOLD}, a combination of {@link Font#BOLD} and
     * {@link Font#ITALIC} or <code>null</code> if the prompt font should be
     * the same as the <code>textComponent</code>s font.
     * </p>
     */
    static void setFontStyle(Integer fontStyle, JTextComponent textComponent) {
        textComponent.putClientProperty(FONT_STYLE, fontStyle);
        textComponent.revalidate();
        textComponent.repaint();
    }

    /**
     * Returns the font style of the prompt text, or <code>null</code> if the
     * prompt's font style should not differ from the <code>textComponent</code>s
     * font.
     */
    static Integer getFontStyle(JTextComponent textComponent) {
        return (Integer) textComponent.getClientProperty(FONT_STYLE);
    }

    /**
     * <p>
     * Determines how the {@link JTextComponent} is rendered when focused and no
     * text is present.
     * </p>
     */
    public enum FocusBehavior {
        /**
         * Highlight the prompt text as it would be selected.
         */
        HIGHLIGHT_PROMPT,
        /**
         * Hide the prompt text.
         */
        HIDE_PROMPT
    }
}
