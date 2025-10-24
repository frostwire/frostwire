package com.limegroup.gnutella.gui;

/**
 * All kinds of options a dialog can return.
 */
public enum DialogOption {
    // NOTE: The IDs are serialized to disk, and should never change.
    /**
     * Constant for when the user selects the yes button
     * in a message.
     */
    YES(I18n.tr("Yes"), 101),
    /**
     * Constant for when the user selects the no button
     * in a message.
     */
    NO(I18n.tr("No"), 102),
    /**
     * Constant for when the user selects the cancel button
     * in a message giving the user a cancel option.
     */
    CANCEL(I18n.tr("Cancel"), 103),
    /**
     * Constant for when the user selects an "other" button
     * in a message giving the user an "other" option.
     */
    OTHER(null, 104),
    /**
     * An invalid DialogOption
     */
    INVALID(null, -1);
    private final String text;
    private final int id;

    DialogOption(String text, int id) {
        this.text = text;
        this.id = id;
    }

    public static DialogOption parseInt(int num) {
        for (DialogOption option : values()) {
            if (option.id == num)
                return option;
        }
        return INVALID;
    }

    public String getText() {
        return I18n.tr(this.text);
    }

    public int toInt() {
        return id;
    }
}

