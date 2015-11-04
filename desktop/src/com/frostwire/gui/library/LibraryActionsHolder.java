/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.library;

import com.frostwire.gui.player.MediaPlayer;
import com.limegroup.gnutella.gui.tables.AbstractActionsHolder;
import com.limegroup.gnutella.gui.tables.DataLine;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class LibraryActionsHolder extends AbstractActionsHolder {

    public LibraryActionsHolder(DataLine<?> dataLine, boolean playing) {
        super(dataLine, playing);
    }

    @Override
    public boolean isPlayable() {
        Object dl = getDataLine();
        if (dl instanceof LibraryFilesTableDataLine) {
            return MediaPlayer.isPlayableFile(((LibraryFilesTableDataLine) dl).getFile());
        } else if (dl instanceof LibraryPlaylistsTableDataLine) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isDownloadable() {
        return false;
    }
}
