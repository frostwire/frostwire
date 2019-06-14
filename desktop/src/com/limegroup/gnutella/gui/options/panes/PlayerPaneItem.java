/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.PlayerSettings;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PlayerPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("FrostWire Media Player");
    private final static String LABEL = I18n.tr("You can play your media with the native operating system player if the format is supported.");
    private final JCheckBox CHECK_BOX = new JCheckBox();
    private final JCheckBox VIDEO_PREVIEW_WITH_INTERNAL_PLAYER_CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     *
     */
    public PlayerPaneItem() {
        super(TITLE, LABEL);
        String CHECK_BOX_LABEL = I18n.tr("Play with the native media player");
        LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL, CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(comp.getComponent());
        String VIDEO_PREVIEW_WITH_INTERNAL_PLAYER_CHECK_BOX_LABEL = I18n.tr("Play search result video previews with internal player");
        add(new LabeledComponent(VIDEO_PREVIEW_WITH_INTERNAL_PLAYER_CHECK_BOX_LABEL, VIDEO_PREVIEW_WITH_INTERNAL_PLAYER_CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT).getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.
     * <p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        CHECK_BOX.setSelected(PlayerSettings.USE_OS_DEFAULT_PLAYER.getValue());
        VIDEO_PREVIEW_WITH_INTERNAL_PLAYER_CHECK_BOX.setSelected(PlayerSettings.USE_FW_PLAYER_FOR_CLOUD_VIDEO_PREVIEWS.getValue());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        PlayerSettings.USE_OS_DEFAULT_PLAYER.setValue(CHECK_BOX.isSelected());
        PlayerSettings.USE_FW_PLAYER_FOR_CLOUD_VIDEO_PREVIEWS.setValue(VIDEO_PREVIEW_WITH_INTERNAL_PLAYER_CHECK_BOX.isSelected());
        return false;
    }

    public boolean isDirty() {
        return PlayerSettings.USE_OS_DEFAULT_PLAYER.getValue() != CHECK_BOX.isSelected() ||
                PlayerSettings.USE_FW_PLAYER_FOR_CLOUD_VIDEO_PREVIEWS.getValue() != VIDEO_PREVIEW_WITH_INTERNAL_PLAYER_CHECK_BOX.isSelected();
    }
}
