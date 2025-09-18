/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.options.panes.ipfilter;

import com.frostwire.regex.Pattern;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.options.panes.IPFilterPaneItem;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

import static com.frostwire.gui.theme.ThemeMediator.fixKeyStrokes;

public class AddRangeManuallyDialog extends JDialog {
    private final static Logger LOG = Logger.getLogger(AddRangeManuallyDialog.class);
    private final static Pattern IPV4_PATTERN = Pattern.compile("(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])", java.util.regex.Pattern.CASE_INSENSITIVE);
    private final static Pattern IPV6_PATTERN = Pattern.compile("([0-9a-f]{1,4}\\:{1,2}){4,7}([0-9a-f]){1,4}", java.util.regex.Pattern.CASE_INSENSITIVE);
    private final IPFilterPaneItem dialogListener;
    private final JTextField descriptionTextField;
    private final JTextField rangeStartTextField;
    private final JTextField rangeEndTextField;

    public AddRangeManuallyDialog(IPFilterPaneItem dialogListener) {
        super(getParentDialog(dialogListener), true);
        final String addIPRangeManuallyString = I18n.tr("Add IP Range Manually");
        setTitle(addIPRangeManuallyString);
        this.dialogListener = dialogListener;
        JPanel panel = new JPanel(new MigLayout("fillx, ins 0, insets, nogrid"));
        panel.add(new JLabel(I18n.tr("Description")), "wrap");
        descriptionTextField = new JTextField();
        panel.add(descriptionTextField, "growx, wrap");
        panel.add(new JLabel("<html><strong>" + I18n.tr("Starting IP address") + "</strong></html>"), "wrap");
        rangeStartTextField = new JTextField();
        panel.add(rangeStartTextField, "w 250px, wrap");
        panel.add(new JLabel("<html><strong>" +
                I18n.tr("Ending IP address") +
                "</strong><br/><i>" +
                I18n.tr("Leave blank or repeat 'Starting IP address' to block a single one") +
                "</i></html>"), "growx ,wrap");
        rangeEndTextField = new JTextField();
        panel.add(rangeEndTextField, "w 250px, gapbottom 10px, wrap");
        fixKeyStrokes(descriptionTextField);
        fixKeyStrokes(rangeStartTextField);
        fixKeyStrokes(rangeEndTextField);
        JButton addRangeButton = new JButton(addIPRangeManuallyString);
        panel.add(addRangeButton, "growx");
        addRangeButton.addActionListener((e) -> onAddRangeButtonClicked());
        JButton cancelButton = new JButton(I18n.tr("Cancel"));
        cancelButton.addActionListener((e) -> dispose());
        panel.add(cancelButton, "growx");
        setContentPane(panel);
        setResizable(false);
        setLocationRelativeTo(getParent());
        pack();
    }

    private static JDialog getParentDialog(IPFilterPaneItem paneItem) {
        Component result = paneItem.getContainer();
        do {
            result = result.getParent();
            LOG.info("getParentDialog: getContainer -> " + result.getClass().getName());
        } while (!(result instanceof JDialog));
        return (JDialog) result;
    }

    private void onAddRangeButtonClicked() {
        if (!validateInput()) {
            return;
        }
        dispose();
        dialogListener.onRangeManuallyAdded(
                new IPRange(
                        descriptionTextField.getText(),
                        rangeStartTextField.getText(),
                        rangeEndTextField.getText()));
    }

    private boolean validateInput() {
        String rangeStart = rangeStartTextField.getText().trim();
        String rangeEnd = rangeEndTextField.getText().trim();
        if (isInvalidIPAddress(rangeStart, IPAddressFormat.IPV4) &&
                isInvalidIPAddress(rangeStart, IPAddressFormat.IPV6)) {
            rangeStartTextField.selectAll();
            rangeStartTextField.requestFocus();
            return false;
        }
        if (!rangeEnd.isEmpty() &&
                isInvalidIPAddress(rangeEnd, IPAddressFormat.IPV4) &&
                isInvalidIPAddress(rangeEnd, IPAddressFormat.IPV6)) {
            rangeEndTextField.selectAll();
            rangeEndTextField.requestFocus();
            return false;
        }
        String description = descriptionTextField.getText().trim();
        if (description.isEmpty()) {
            description = I18n.tr("Not available");
        }
        try {
            new IPRange(
                    description,
                    rangeStart,
                    rangeEnd);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    private boolean isInvalidIPAddress(String potentialIPAddress, IPAddressFormat format) {
        if (potentialIPAddress == null || potentialIPAddress.isEmpty()) {
            return true;
        }
        Pattern pattern = format == IPAddressFormat.IPV4 ? IPV4_PATTERN : IPV6_PATTERN;
        return !pattern.matcher(potentialIPAddress).find();
    }

    private enum IPAddressFormat {
        IPV4,
        IPV6
    }
}