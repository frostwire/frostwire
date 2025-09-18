/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UISettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import javax.swing.*;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * @see com.limegroup.gnutella.gui.GUIConstants.Feature enum for available experimental features.
 * Created on Mar/7/17
 */
public class ExperimentalFeaturesPaneItem extends AbstractPaneItem {
    private final JCheckBox alphaCheckbox;
    private final JCheckBox betaCheckbox;
    private final JLabel note; // couldn't make this a local variable due to a Swing bug on which it'd be added twice.
    private final boolean initialAlphaValue;
    private final boolean initialBetaValue;

    public ExperimentalFeaturesPaneItem() {
        super(I18n.tr("Experimental Features"),
                I18n.tr("Warning: These experimental features may change, break, or disappear at any time. We make absolutely no guarantees about what may happen if you turn one of these experiments on. FrostWire may delete all your data, or your security and privacy could be compromised in unexpected ways. Please procede with caution."));
        initialAlphaValue = FrostWireUtils.isIsRunningFromSource() || UISettings.ALPHA_FEATURES_ENABLED.getValue();
        initialBetaValue = FrostWireUtils.isIsRunningFromSource() || UISettings.BETA_FEATURES_ENABLED.getValue();
        alphaCheckbox = new JCheckBox();
        betaCheckbox = new JCheckBox();
        note = new JLabel(I18n.tr("Note: All features are enabled by default when running FrostWire from source"));
        note.setVisible(false);
    }

    @Override
    public boolean isDirty() {
        return initialAlphaValue != alphaCheckbox.isSelected() || initialBetaValue != betaCheckbox.isSelected();
    }

    @Override
    public void initOptions() {
        alphaCheckbox.setText("<html><strong>" + I18n.tr("Enable ALPHA features") + "</strong>. " + I18n.tr("Bleeding edge, unstable, tested by developers only, very risky.") + "</html>");
        betaCheckbox.setText("<html><strong>" + I18n.tr("Enable BETA features") + "</strong>. " + I18n.tr("Feature complete, unknown bugs, tested by QA, somewhat risky.") + "</html>");
        alphaCheckbox.setSelected(initialAlphaValue);
        betaCheckbox.setSelected(initialBetaValue);
        add(alphaCheckbox);
        add(betaCheckbox);
        add(note);
        if (FrostWireUtils.isIsRunningFromSource()) {
            alphaCheckbox.setEnabled(false);
            betaCheckbox.setEnabled(false);
            note.setVisible(true);
        }
    }

    @Override
    public boolean applyOptions() {
        if (FrostWireUtils.isIsRunningFromSource()) {
            // no need to save these settings when running from source
            return false;
        }
        UISettings.ALPHA_FEATURES_ENABLED.setValue(alphaCheckbox.isSelected());
        UISettings.BETA_FEATURES_ENABLED.setValue(betaCheckbox.isSelected());
        return isDirty();
    }
}
