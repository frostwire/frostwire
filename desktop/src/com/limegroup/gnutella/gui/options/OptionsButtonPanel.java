package com.limegroup.gnutella.gui.options;

import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.ButtonRow;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * This class contains the <tt>ButtonRow</tt> instance for the options
 * window.
 */
final class OptionsButtonPanel {
    /**
     * Handle to the enclosed <tt>ButtonRow</tt> instance.
     */
    private final ButtonRow _buttonRow;
    /**
     * Handle to the other button row.
     */
    private final ButtonRow _revertRow;

    /**
     * The constructor creates the <tt>ButtonRow</tt>.
     */
    OptionsButtonPanel() {
        String[] buttonLabelKeys = {
                I18n.tr("OK"),
                I18n.tr("Cancel"),
                I18n.tr("Apply")
        };
        String[] toolTipKeys = {
                I18n.tr("Apply Operation"),
                I18n.tr("Cancel Operation"),
                I18n.tr("Apply Operation"),
        };
        ActionListener[] listeners = {
                new OKListener(), new CancelListener(), new ApplyListener()
        };
        _buttonRow = new ButtonRow(buttonLabelKeys, toolTipKeys, listeners,
                ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);
        buttonLabelKeys = new String[]{I18n.tr("Restore Defaults")};
        toolTipKeys = new String[]{I18n.tr("Revert All Settings to the Factory Defaults")};
        listeners = new ActionListener[]{new RevertListener()};
        _revertRow = new ButtonRow(buttonLabelKeys, toolTipKeys, listeners,
                ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE);
    }

    /**
     * Returns the <tt>Component</tt> that contains the <tt>ButtonRow</tt>.
     */
    Component getComponent() {
        JPanel box = new BoxPanel(BoxPanel.X_AXIS);
        box.add(Box.createHorizontalStrut(50));
        box.add(_revertRow);
        //box.add(Box.createHorizontalGlue());
        _buttonRow.setAlignmentX(1f);
        box.add(_buttonRow);
        //box.add(Box.createHorizontalGlue());
        return box;
    }

    /**
     * Listener for the revert to default option.
     * Reverts all options to their factory defaults.
     */
    private class RevertListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            OptionsMediator.instance().revertOptions();
            OptionsMediator.instance().setOptionsVisible(false);
        }
    }

    /**
     * The listener for the ok button.  Applies the current options and
     * makes the window not visible.
     */
    private class OKListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            // close the window only if the new settings
            // work correctly, as the user may need to
            // change the settings before closing.
            try {
                OptionsMediator.instance().applyOptions();
                OptionsMediator.instance().setOptionsVisible(false);
            } catch (IOException ioe) {
                // nothing we should do here.  a message should
                // have been displayed to the user with more information
            }
        }
    }

    /**
     * The listener for the cancel button.
     */
    private class CancelListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            OptionsMediator.instance().setOptionsVisible(false);
        }
    }

    /**
     * The listener for the apply button.  Applies the current settings.
     */
    private class ApplyListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            try {
                OptionsMediator.instance().applyOptions();
            } catch (IOException ioe) {
                // nothing we should do here.  a message should
                // have been displayed to the user with more information
            }
        }
    }
}
