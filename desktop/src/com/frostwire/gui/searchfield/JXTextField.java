package com.frostwire.gui.searchfield;

import javax.swing.*;
import java.awt.*;

/**
 * {@link JTextField}, with integrated support for prompts and buddies.
 *
 * @author Peter Weishapl <petw@gmx.net>
 * @see PromptSupport
 * @see BuddySupport
 */
class JXTextField extends JTextField {
    private static final long serialVersionUID = 7120788755640067659L;

    JXTextField(String promptText) {
        this(promptText, null);
    }

    private JXTextField(String promptText, Color promptForeground) {
        this(promptText, promptForeground, null);
    }

    private JXTextField(String promptText, Color promptForeground,
                        Color promptBackground) {
        PromptSupport.init(promptText, promptForeground, promptBackground,
                this);
    }

    /**
     * @see PromptSupport#setPrompt(String, javax.swing.text.JTextComponent)
     */
    public void setPrompt(String labelText) {
        PromptSupport.setPrompt(labelText, this);
    }

    /**
     * @see PromptSupport#setFontStyle(Integer, javax.swing.text.JTextComponent)
     */
    void setPromptFontStyle(Integer fontStyle) {
        PromptSupport.setFontStyle(fontStyle, this);
    }

    /**
     * @see BuddySupport#getOuterMargin(JTextField)
     */
    Insets getOuterMargin() {
        return BuddySupport.getOuterMargin(this);
    }

    /**
     * @see BuddySupport#setOuterMargin(JTextField, Insets)
     */
    void setOuterMargin(Insets margin) {
        BuddySupport.setOuterMargin(this, margin);
    }
}
