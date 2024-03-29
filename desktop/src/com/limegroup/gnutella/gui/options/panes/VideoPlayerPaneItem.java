/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.gui.SizedTextField;
import com.limegroup.gnutella.settings.URLHandlerSettings;

import javax.swing.*;

/**
 * This class defines the panel in the options
 * window that allows the user to select the
 * default video behavior.
 */
public class VideoPlayerPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Video Options");
    private final static String LABEL = I18n.tr("You can choose which video player to use.");
    /**
     * Handle to the <tt>JTextField</tt> that displays the player name
     */
    private final JTextField _playerField;

    /**
     * Creates new VideoPlayerOptionsPaneItem
     *
     */
    public VideoPlayerPaneItem() {
        super(TITLE, LABEL);
        _playerField = new SizedTextField(25, SizePolicy.RESTRICT_HEIGHT);
        /*
          Constant for the key of the locale-specific <code>String</code> for the
          label on the component that allows to user to change the setting for
          this <tt>PaneItem</tt>.
         */
        String OPTION_LABEL = I18n.tr("Video Player");
        LabeledComponent comp = new LabeledComponent(OPTION_LABEL, _playerField);
        add(comp.getComponent());
    }

    /**
     * Applies the options currently set in this <tt>PaneItem</tt>.
     *
     */
    public boolean applyOptions() {
        URLHandlerSettings.VIDEO_PLAYER.setValue(_playerField.getText());
        return false;
    }

    /**
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        _playerField.setText(URLHandlerSettings.VIDEO_PLAYER.getValue());
    }

    public boolean isDirty() {
        return !URLHandlerSettings.VIDEO_PLAYER.getValue().equals(_playerField.getText());
    }
}
