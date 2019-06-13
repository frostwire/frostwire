/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, 2013, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.tables;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractActionsHolder {
    private final DataLine<?> dataLine;
    private boolean playing;

    public AbstractActionsHolder(DataLine<?> dataLine, boolean playing) {
        this.playing = playing;
        this.dataLine = dataLine;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public DataLine<?> getDataLine() {
        return dataLine;
    }

    public abstract boolean isPlayable();

    public abstract boolean isDownloadable();

    public abstract File getFile();
}