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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.Line;
import com.limegroup.gnutella.gui.shell.LimeAssociationOption;
import com.limegroup.gnutella.gui.shell.FrostAssociations;
import com.limegroup.gnutella.settings.QuestionsHandler;

public class AssociationsWindow extends SetupWindow {
    /**
     * 
     */
    private static final long serialVersionUID = -8599946599240459538L;

    /** a mapping of checkboxes to associations */
    private Map<JCheckBox, LimeAssociationOption> associations = new HashMap<JCheckBox, LimeAssociationOption>();

    /** Check box to check associations on startup. */
    private JRadioButton always, never, ask;

    AssociationsWindow(SetupManager manager) {
        super(manager, I18n.tr("File & Protocol Associations"), I18n.tr("What type of resources should FrostWire open?"));
    }

    protected void createWindow() {
        super.createWindow();

        // Similar to the options window, except that the radio buttons default to
        // "always" and all supported associations are allowed.

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        gbc.insets = new Insets(1, 4, 1, 0);
        for (LimeAssociationOption option : FrostAssociations.getSupportedAssociations()) {
            JCheckBox box = new JCheckBox(option.getDescription());
            box.setSelected(true);
            associations.put(box, option);
            panel.add(box, gbc);
        }

        gbc.insets = new Insets(9, 3, 9, 3);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new Line(), gbc);

        gbc.fill = GridBagConstraints.NONE;

        gbc.insets = new Insets(1, 0, 2, 0);
        panel.add(new JLabel(I18n.tr("What should FrostWire do with the selected associations on startup?")), gbc);
        int value = QuestionsHandler.GRAB_ASSOCIATIONS.getValue();
        always = new JRadioButton(I18n.tr("Always take the selected associations."), DialogOption.parseInt(value) == DialogOption.YES);
        never = new JRadioButton(I18n.tr("Ignore all missing associations."), DialogOption.parseInt(value) == DialogOption.NO);
        ask = new JRadioButton(I18n.tr("Ask me what to do when an association is missing."), DialogOption.parseInt(value) != DialogOption.YES && DialogOption.parseInt(value) != DialogOption.NO);
        ButtonGroup grabGroup = new ButtonGroup();
        grabGroup.add(always);
        grabGroup.add(ask);
        grabGroup.add(never);
        always.setSelected(true);

        gbc.insets = new Insets(1, 4, 1, 0);
        panel.add(always, gbc);
        panel.add(ask, gbc);
        panel.add(never, gbc);

        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridheight = GridBagConstraints.REMAINDER;
        panel.add(Box.createGlue(), gbc);

        setSetupComponent(panel);
    }

    public void applySettings(boolean loadCoreComponents) {
        for (Map.Entry<JCheckBox, LimeAssociationOption> entry : associations.entrySet()) {
            LimeAssociationOption option = entry.getValue();
            if (entry.getKey().isSelected()) {
                option.setAllowed(true);
                option.setEnabled(true);
            } else {
                // only disallow options that were previously enabled.
                if (option.isEnabled())
                    option.setAllowed(false);
                option.setEnabled(false);
            }
        }

        DialogOption value = DialogOption.INVALID;
        if (always.isSelected())
            value = DialogOption.YES;
        else if (never.isSelected())
            value = DialogOption.NO;
        QuestionsHandler.GRAB_ASSOCIATIONS.setValue(value.toInt());
    }
}
