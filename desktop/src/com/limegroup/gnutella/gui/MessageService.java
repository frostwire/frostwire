package com.limegroup.gnutella.gui;

import org.limewire.service.Switch;
import org.limewire.setting.IntSetting;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * This class handles displaying messages to the user.
 */
public final class MessageService {
    /**
     * Constant for when the 'Always use this answer' checkbox wants to
     * remember the answer.
     */
    private static final int REMEMBER_ANSWER = 1;
    /**
     * Constant for when the 'Always use this answer' checkbox does not
     * want to remember the answer.
     */
    private static final int FORGET_ANSWER = 0;
    /**
     * <tt>MessageService</tt> instance, following singleton.
     */
    private static final MessageService INSTANCE = new MessageService();
    /**
     * A Map containing disposable messages
     */
    private final Map<String, JDialog> _disposableMessageMap = new HashMap<>();

    /**
     * Initializes all of the necessary messaging components.
     */
    private MessageService() {
        GUIMediator.setSplashScreenString(
                I18n.tr("Loading Messages..."));
    }

    /**
     * Instance accessor for the <tt>MessageService</tt>.
     */
    public static MessageService instance() {
        return INSTANCE;
    }

    /**
     * Convenience method for determining which window should be the parent
     * of message windows.
     *
     * @return the <tt>Component</tt> that should act as the parent of message
     * windows
     */
    public static Component getParentComponent() {
        if (GUIMediator.isOptionsVisible())
            return GUIMediator.getMainOptionsComponent();
        return GUIMediator.getAppFrame();
    }

    /**
     * Display a standardly formatted error message with
     * the specified String.
     *
     * @param message the message to display to the user
     */
    public final void showError(final String message) {
        GUIMediator.safeInvokeLater(() -> {
            JOptionPane optionPane = new JOptionPane();
            optionPane.setMessageType(JOptionPane.ERROR_MESSAGE);
            JEditorPane editorPane = new JEditorPane();
            //so that it will use the font we tell it.
            editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            editorPane.setEditable(false);
            editorPane.setOpaque(false);
            editorPane.setFont(new Font("Arial", Font.PLAIN, 12));
            editorPane.setContentType("text/html");
            editorPane.addHyperlinkListener(e -> {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    GUIMediator.openURL(e.getURL().toString());
                }
            });
            editorPane.setText(message);
            optionPane.setMessage(editorPane);
            optionPane.setOpaque(true);
            JDialog dialog = optionPane.createDialog(getParentComponent(), I18n.tr("Error"));
            dialog.setVisible(true);
        });
    }

    /**
     * Display a standardly formatted error message with
     * the specified String.
     *
     * @param message the message to display to the user
     * @param ignore  the Boolean setting to store/retrieve whether or not to
     *                ignore this message in the future.
     */
    final void showError(final String message, final Switch ignore) {
        if (!ignore.getValue()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(getParentComponent(),
                    doNotDisplayAgainLabel(message, ignore),
                    I18n.tr("Error"),
                    JOptionPane.ERROR_MESSAGE));
        }
    }

    /**
     * Display a standardly formatted warning message with
     * the specified String.
     *
     * @param message the message to display to the user
     * @param ignore  the Boolean setting to store/retrieve whether or not to
     *                ignore this message in the future.
     */
    final void showWarning(final String message, final Switch ignore) {
        if (!ignore.getValue()) {
            GUIMediator.safeInvokeLater(() -> JOptionPane.showMessageDialog(getParentComponent(), doNotDisplayAgainLabel(message, ignore), I18n.tr("Warning"),
                    JOptionPane.WARNING_MESSAGE));
        }
    }

    /**
     * Display a standardly formatted warning message with
     * the specified String.
     *
     * @param message the message to display to the user
     */
    final void showWarning(final String message) {
        GUIMediator.safeInvokeLater(() -> JOptionPane.showMessageDialog(getParentComponent(), getLabel(message), I18n.tr("Warning"), JOptionPane.WARNING_MESSAGE));
    }

    /**
     * Displays a standardly formatted information message with
     * the specified Component.
     *
     * @param toDisplay the object to display in the message
     */
    public final void showMessage(final Component toDisplay) {
        GUIMediator.safeInvokeLater(() -> JOptionPane.showMessageDialog(getParentComponent(), toDisplay, I18n.tr("Message"), JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * Display a standardly formatted information message with
     * the specified String.
     *
     * @param message the message to display to the user
     */
    final void showMessage(final String message) {
        GUIMediator.safeInvokeLater(() -> JOptionPane.showMessageDialog(getParentComponent(), getLabel(message), I18n.tr("Message"), JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * Display a standardly formatted information message with
     * the specified String.  Store whether or not to display message
     * again in the BooleanSetting ignore.
     *
     * @param message the message to display to the user
     * @param ignore  the Boolean setting to store/retrieve whether or not to
     *                ignore this message in the future.
     */
    final void showMessage(final String message, final Switch ignore) {
        if (!ignore.getValue()) {
            GUIMediator.safeInvokeLater(() -> JOptionPane.showMessageDialog(getParentComponent(), doNotDisplayAgainLabel(message, ignore), I18n.tr("Message"),
                    JOptionPane.INFORMATION_MESSAGE));
        }
    }

    /**
     * Display a disposable message with the specified String.
     *
     * @param dialogKey the key to use for access to showing/hiding this dialog
     * @param message   The message to display int he dialog
     * @param msgType   The <tt>JOptionPane</tt> message type. @see javax.swing.JOptionPane.
     *                  May be one of ERROR_MESSAGE, WARNING_MESSAGE, INFORMATION_MESSAGE,
     *                  or PLAIN_MESSAGE.
     */
    final void showDisposableMessage(String dialogKey, String message, int msgType) {
        showDisposableMessage(dialogKey, message, null, msgType);
    }

    /**
     * Display a disposable message with
     * the specified String.  Store whether or not to display message
     * again in the BooleanSetting ignore.
     *
     * @param dialogKey the key to use for access to showing/hiding this dialog
     * @param message   The message to display int he dialog
     * @param ignore    the Boolean setting to store/retrieve whether or not to
     *                  ignore this message in the future.
     * @param msgType   The <tt>JOptionPane</tt> message type. @see javax.swing.JOptionPane.
     *                  May be one of ERROR_MESSAGE, WARNING_MESSAGE, INFORMATION_MESSAGE,
     *                  or PLAIN_MESSAGE.
     */
    final void showDisposableMessage(final String dialogKey, final String message, final Switch ignore, final int msgType) {
        String title;
        switch (msgType) {
            case JOptionPane.ERROR_MESSAGE:
                title = I18n.tr("Error");
                break;
            case JOptionPane.WARNING_MESSAGE:
                title = I18n.tr("Warning");
                break;
            case JOptionPane.INFORMATION_MESSAGE:
            case JOptionPane.PLAIN_MESSAGE:
                title = I18n.tr("Message");
                break;
            default:
                throw new IllegalArgumentException("Unsupported Message Type: " + msgType);
        }
        final String finalTitle = title;
        if (ignore == null || !ignore.getValue()) {
            GUIMediator.safeInvokeLater(() -> {
                if (_disposableMessageMap.containsKey(dialogKey)) {
                    JDialog dialog = _disposableMessageMap.get(dialogKey);
                    dialog.toFront();
                    dialog.setVisible(true);
                } else {
                    Object component = message;
                    if (ignore != null)
                        component = doNotDisplayAgainLabel(message, ignore);
                    JOptionPane pane = new JOptionPane(component, msgType);
                    JDialog dialog = pane.createDialog(getParentComponent(), finalTitle);
                    dialog.setModal(true);
                    _disposableMessageMap.put(dialogKey, dialog);
                    dialog.setVisible(true);
                    // dialog has been disposed by user OR by core
                    _disposableMessageMap.remove(dialogKey);
                }
            });
        }
    }

    /**
     * Hides the disposable message specified by the dialogKey.
     */
    final void hideDisposableMessage(final String dialogKey) {
        GUIMediator.safeInvokeLater(() -> {
            JDialog dialog = _disposableMessageMap.get(dialogKey);
            if (dialog != null) {
                dialog.setVisible(false);
                dialog.dispose();
            }
        });
    }

    /**
     * Display a standardly formatted confirmation message with
     * the specified String.
     *
     * @param message the message to display to the user
     */
    final void showConfirmMessage(final String message) {
        GUIMediator.safeInvokeLater(() -> JOptionPane.showConfirmDialog(getParentComponent(), getLabel(message), I18n.tr("Message"), JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * Display a standardly formatted confirmation message with
     * the specified String.  Store whether or not to display
     * the message again in the BooleanSetting ignore.
     *
     * @param message the message to display to the user
     * @param ignore  the Boolean setting to store/retrieve whether or not to
     *                ignore this message in the future.
     */
    final void showConfirmMessage(final String message, final Switch ignore) {
        if (!ignore.getValue()) {
            GUIMediator.safeInvokeLater(() -> JOptionPane.showConfirmDialog(getParentComponent(),
                    doNotDisplayAgainLabel(message, ignore),
                    I18n.tr("Message"),
                    JOptionPane.INFORMATION_MESSAGE));
        }
    }

    final DialogOption showYesNoMessage(String message, DialogOption defaultOption) {
        return showYesNoMessage(message, I18n.tr("Message"), defaultOption);
    }

    /**
     * Displays a message to the user and returns
     * MessageService.YES_OPTION if the user selects yes and
     * MessageService.NO_OPTION if the user selects no.
     *
     * @param message the message to display to the user
     */
    final DialogOption showYesNoMessage(String message) {
        return showYesNoMessage(message, I18n.tr("Message"));
    }

    /**
     * Displays a message and a list underneath to the user passing on the
     * return value from
     * {@link JOptionPane#showConfirmDialog(Component, Object, String, int)}.
     *
     * @param message      the message to display to the user
     * @param listModel    the array of object to be displayed in the list
     * @param messageType  either {@link JOptionPane#YES_NO_OPTION},
     *                     {@link JOptionPane#YES_NO_CANCEL_OPTION} or {@link JOptionPane#OK_CANCEL_OPTION}.
     * @param listRenderer an optional list cell renderer, can be <code>null</code>
     */
    final int showConfirmListMessage(String message, Object[] listModel, int messageType,
                                     ListCellRenderer<Object> listRenderer) {
        return showConfirmListMessage(message, listModel, messageType, listRenderer,
                I18n.tr("Message"));
    }

    /**
     * Displays a message and a list underneath to the user passing on the
     * return value from
     * {@link JOptionPane#showConfirmDialog(Component, Object, String, int)}.
     *
     * @param message      the message to display to the user
     * @param listModel    the array of object to be displayed in the list
     * @param messageType  either {@link JOptionPane#YES_NO_OPTION},
     *                     {@link JOptionPane#YES_NO_CANCEL_OPTION} or {@link JOptionPane#OK_CANCEL_OPTION}.
     * @param listRenderer an optional list cell renderer, can be <code>null</code>
     */
    private int showConfirmListMessage(String message, Object[] listModel,
                                       int messageType, ListCellRenderer<Object> listRenderer, String title) {
        JList<Object> list = new JList<>(listModel);
        list.setVisibleRowCount(5);
        list.setSelectionForeground(list.getForeground());
        list.setSelectionBackground(list.getBackground());
        list.setFocusable(false);
        if (listRenderer != null) {
            list.setCellRenderer(listRenderer);
        }
        Object[] content = new Object[]{
                new MultiLineLabel(message, 400),
                Box.createVerticalStrut(ButtonRow.BUTTON_SEP),
                new JScrollPane(list)
        };
        return JOptionPane.showConfirmDialog(getParentComponent(),
                content, title, messageType);
    }

    private DialogOption showYesNoMessage(String message, String title) {
        return showYesNoMessage(message, title, DialogOption.YES);
    }

    final DialogOption showYesNoMessage(String message, String title, int msgType) {
        return showYesNoMessage(message, title, msgType, DialogOption.YES);
    }

    /**
     * Displays a message to the user and returns
     * MessageService.YES_OPTION if the user selects yes and
     * MessageService.NO_OPTION if the user selects no.
     *
     * @param message the message to display to the user
     * @param title   the title on the dialog
     */
    final DialogOption showYesNoMessage(String message, String title, DialogOption defaultOption) {
        return showYesNoMessage(message, title, JOptionPane.WARNING_MESSAGE, defaultOption);
    }

    /**
     * Displays a message to the user and returns
     * MessageService.YES_OPTION if the user selects yes and
     * MessageService.NO_OPTION if the user selects no.
     *
     * @param message       the message to display to the user
     * @param title         the title on the dialog
     * @param msgType       type message and icon
     * @param defaultOption
     */
    private DialogOption showYesNoMessage(String message, String title, int msgType, DialogOption defaultOption) {
        final String[] options = {DialogOption.YES.getText(),
                DialogOption.NO.getText()
        };
        int option;
        try {
            option =
                    JOptionPane.showOptionDialog(getParentComponent(),
                            getLabel(message),
                            title,
                            JOptionPane.YES_NO_OPTION,
                            msgType, null,
                            options, defaultOption.getText());
        } catch (InternalError ie) {
            // happens occasionally, assume no.
            option = JOptionPane.NO_OPTION;
        }
        if (option == JOptionPane.YES_OPTION) {
            return DialogOption.YES;
        }
        return DialogOption.NO;
    }

    final DialogOption showYesNoMessage(String message, IntSetting defValue) {
        return showYesNoMessage(message, defValue, DialogOption.YES);
    }

    /**
     * Displays a message to the user and returns
     * MessageService.YES_OPTION if the user selects yes and
     * MessageService.NO_OPTION if the user selects no.  Stores
     * the default response in IntSetting default.
     *
     * @param message  the message to display to the user
     * @param defValue the IntSetting to store/retrieve the default
     *                 value for this question.
     */
    final DialogOption showYesNoMessage(String message, IntSetting defValue, DialogOption defaultOption) {
        final String[] options = {DialogOption.YES.getText(),
                DialogOption.NO.getText()
        };
        DialogOption ret = DialogOption.parseInt(defValue.getValue());
        if (ret == DialogOption.YES || ret == DialogOption.NO)
            return ret;
        // We only get here if the default didn't have a valid value.
        int option;
        try {
            option =
                    JOptionPane.showOptionDialog(getParentComponent(),
                            alwaysUseThisAnswerLabel(message, defValue),
                            I18n.tr("Message"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE, null,
                            options, defaultOption.getText());
        } catch (ArrayIndexOutOfBoundsException | InternalError aioobe) {
            // happens occasionally on windows, assume no.
            option = JOptionPane.NO_OPTION;
        } // happens occasionally, assume no.
        if (option == JOptionPane.YES_OPTION)
            ret = DialogOption.YES;
        else
            ret = DialogOption.NO;
        // If we wanted to remember the answer, remember it.
        if (defValue.getValue() == REMEMBER_ANSWER)
            defValue.setValue(ret.toInt());
        else
            defValue.setValue(FORGET_ANSWER);
        return ret;
    }

    /**
     * Displays a message to the user and returns
     * MessageService.YES_OPTION if the user selects yes and
     * MessageService.NO_OPTION if the user selects no.
     * MessageService.CANCEL_OPTION if the user selects cancel.
     *
     * @param message the message to display to the user
     */
    final DialogOption showYesNoCancelMessage(String message) {
        int option;
        try {
            option =
                    JOptionPane.showConfirmDialog(getParentComponent(),
                            getLabel(message),
                            I18n.tr("Message"),
                            JOptionPane.YES_NO_CANCEL_OPTION);
        } catch (InternalError ie) {
            // happens occasionally, assume no.
            option = JOptionPane.NO_OPTION;
        }
        if (option == JOptionPane.YES_OPTION)
            return DialogOption.YES;
        else if (option == JOptionPane.NO_OPTION)
            return DialogOption.NO;
        return DialogOption.CANCEL;
    }

    /**
     * Displays a message to the user and returns
     * MessageService.YES_OPTION if the user selects yes and
     * MessageService.NO_OPTION if the user selects no.
     * MessageService.CANCEL_OPTION if the user selects cancel.  Stores
     * the default response in IntSetting default.
     *
     * @param message  the message to display to the user
     * @param defValue the IntSetting to store/retrieve the default
     *                 value for this question.
     */
    final DialogOption showYesNoCancelMessage(String message, IntSetting defValue) {
        // if default has a valid value, use it.
        DialogOption ret = DialogOption.parseInt(defValue.getValue());
        if (ret == DialogOption.YES || ret == DialogOption.NO)
            return ret;
        // We only get here if the default didn't have a valid value.
        int option;
        try {
            option =
                    JOptionPane.showConfirmDialog(getParentComponent(),
                            alwaysUseThisAnswerLabel(message, defValue),
                            I18n.tr("Message"),
                            JOptionPane.YES_NO_CANCEL_OPTION);
        } catch (ArrayIndexOutOfBoundsException | InternalError aioobe) {
            // happens occasionally on windows, assume cancel.
            option = JOptionPane.CANCEL_OPTION;
        } // happens occasionally, assume cancel.
        if (option == JOptionPane.YES_OPTION)
            ret = DialogOption.YES;
        else if (option == JOptionPane.NO_OPTION)
            ret = DialogOption.NO;
        else
            ret = DialogOption.CANCEL;
        // If we wanted to remember the answer, remember it.
        if (defValue.getValue() == REMEMBER_ANSWER && ret != DialogOption.CANCEL)
            defValue.setValue(ret.toInt());
        else
            defValue.setValue(FORGET_ANSWER);
        return ret;
    }

    /**
     * Displays a message to the user and returns
     * MessageService.YES_OPTION if the user selects yes and
     * MessageService.NO_OPTION if the user selects no.
     * MessageService.OTHER_OPTION if the user selects other.  Stores
     * the default response in IntSetting default.
     *
     * @param message  the message to display to the user
     * @param defValue the IntSetting to store/retrieve the default
     *                 value for this question.
     */
    final DialogOption showYesNoOtherMessage(String message, IntSetting defValue, String otherName) {
        final String[] options = {DialogOption.YES.getText(),
                DialogOption.NO.getText(), otherName
        };
        // if default has a valid value, use it.
        DialogOption ret = DialogOption.parseInt(defValue.getValue());
        if (ret == DialogOption.YES || ret == DialogOption.NO)
            return ret;
        // We only get here if the default didn't have a valid value.
        int option;
        try {
            option = JOptionPane.showOptionDialog(getParentComponent(),
                    alwaysUseThisAnswerLabel(message, defValue),
                    I18n.tr("Message"),
                    0,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    DialogOption.YES.getText());
        } catch (ArrayIndexOutOfBoundsException | InternalError aioobe) {
            // happens occasionally on windows, assume cancel.
            option = JOptionPane.CLOSED_OPTION;
        } // happens occasionally, assume cancel.
        if (option == 0) // Yes
            ret = DialogOption.YES;
        else if (option == 1) // No
            ret = DialogOption.NO;
        else if (option == 2) // Other
            ret = DialogOption.OTHER;
        else
            ret = DialogOption.CANCEL;
        // If we wanted to remember the answer, remember it.
        if (defValue.getValue() == REMEMBER_ANSWER && ret != DialogOption.OTHER && ret != DialogOption.CANCEL)
            defValue.setValue(ret.toInt());
        else
            defValue.setValue(FORGET_ANSWER);
        return ret;
    }

    /**
     * Display a standardly formatted question message with
     * the specified String and optionally an inital input value.
     *
     * @param message      the message to display to the user
     * @param initialValue the initial value of the input, can be null.
     * @return a String containing the user input
     */
    final String showInputMessage(String message, String initialValue) {
        if (initialValue == null)
            return JOptionPane.showInputDialog(
                    getParentComponent(),
                    message);
        else
            return JOptionPane.showInputDialog(
                    getParentComponent(),
                    message,
                    initialValue);
    }

    private JComponent getLabel(String message) {
        if (message.startsWith("<html"))
            return new HTMLLabel(message);
        else
            return new MultiLineLabel(message, 400);
    }

    private JComponent doNotDisplayAgainLabel(
            final String message, final Switch setting) {
        JPanel thePanel = new JPanel(new BorderLayout(0, 15));
        JCheckBox option = new JCheckBox(
                I18n.tr("Do not display this message again")
        );
        JComponent lbl = getLabel(message);
        thePanel.add(lbl, BorderLayout.NORTH);
        thePanel.add(option, BorderLayout.WEST);
        option.addItemListener(e -> setting.setValue(e.getStateChange() == ItemEvent.SELECTED));
        return thePanel;
    }

    private JComponent alwaysUseThisAnswerLabel(
            final String message, final IntSetting setting) {
        JPanel thePanel = new JPanel(new BorderLayout(0, 15));
        JCheckBox option = new JCheckBox(
                I18n.tr("Always use this answer")
        );
        JComponent lbl = getLabel(message);
        thePanel.add(lbl, BorderLayout.NORTH);
        thePanel.add(option, BorderLayout.WEST);
        option.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED)
                setting.setValue(REMEMBER_ANSWER);
            else
                setting.setValue(FORGET_ANSWER);
        });
        return thePanel;
    }
}
