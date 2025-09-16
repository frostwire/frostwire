/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.frostwire.gui.library;

import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.mplayer.MediaPlaybackState;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
class LibraryIconList extends JList<Object> {
    private Image speaker;
    private Image loading;

    LibraryIconList(ListModel<Object> dataModel) {
        super(dataModel);
        loadIcons();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        MediaPlayer player = MediaPlayer.instance();
        if (player.getState() != MediaPlaybackState.Stopped &&
                player.getState() != MediaPlaybackState.Closed &&
                player.getState() != MediaPlaybackState.Failed) {
        }
    }

    private void loadIcons() {
        speaker = GUIMediator.getThemeImage("speaker").getImage();
        loading = GUIMediator.getThemeImage("indeterminate_small_progress").getImage();
    }

    private void paintIcon(Graphics g, Image image, int index) {
        Rectangle rect = getUI().getCellBounds(this, index, index);
        Dimension lsize = rect.getSize();
        Point llocation = rect.getLocation();
        g.drawImage(image, llocation.x + lsize.width - speaker.getWidth(null) - 4, llocation.y + (lsize.height - speaker.getHeight(null)) / 2, null);
    }


}
