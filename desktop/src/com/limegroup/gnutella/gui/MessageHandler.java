package com.limegroup.gnutella.gui;

import javax.swing.SwingUtilities;

import org.limewire.service.MessageCallback;
import org.limewire.service.Switch;


/**
 * Displays messages to the user using the standard LimeWire messaging service
 * classes.
 */
public class MessageHandler implements MessageCallback {

    /**
     * Creats a new <tt>MessageHandler</tt> for displaying messages to the user.
     */
    public MessageHandler() {}

    // Inherit doc comment.
    public void showError(final String messageKey) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showError(I18n.tr(messageKey));
            }
        });
    }
    
    // Inherit doc comment.
    public void showError(final String messageKey,
                          final Switch ignore) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showError(I18n.tr(messageKey), ignore);
            }
        });
    }  

    // Inherit doc comment.
    public void showMessage(final String messageKey) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showMessage(I18n.tr(messageKey));
            }
        });
    }

    // Inherit doc comment.
    public void showMessage(final String messageKey,
                            final Switch ignore) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showMessage(I18n.tr(messageKey), ignore);
            }
        });
    }

    public void showFormattedError(final String errorKey,
                                   final Object... args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showError(I18n.tr(errorKey, args));
            }
        });
    }

    public void showFormattedError(final String errorKey,
                                   final Switch ignore,
                                   final Object... args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showError(I18n.tr(errorKey, args), ignore);
            }
        });
    }

    public void showFormattedMessage(final String messageKey,
                                     final Object... args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showMessage(I18n.tr(messageKey, args));
            }
        });
    }

    public void showFormattedMessage(final String messageKey,
                                     final Switch ignore,
                                     final Object... args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUIMediator.showMessage(I18n.tr(messageKey, args), ignore);
            }
        });
    }

}
