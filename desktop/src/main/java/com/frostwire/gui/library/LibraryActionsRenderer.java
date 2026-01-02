/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.library;

import com.frostwire.util.MediaSource;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.tables.AbstractActionsRenderer;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LibraryActionsRenderer extends AbstractActionsRenderer {
    @Override
    protected void onPlayAction() {
        if (actionsHolder != null && actionsHolder.getDataLine() != null) {
            MediaSource mediaSource = null;
            List<MediaSource> filesView = null;
            Object dataLine = actionsHolder.getDataLine();
            if (dataLine instanceof LibraryFilesTableDataLine) {
                mediaSource = new MediaSource(((LibraryFilesTableDataLine) dataLine).getFile());
                filesView = LibraryFilesTableMediator.instance().getFilesView();
            }
            if (mediaSource != null && !actionsHolder.isPlaying()) {
                GUIMediator.instance().playInOS(mediaSource);
            }
        }
    }

    @Override
    protected void onDownloadAction() {
    }
}
