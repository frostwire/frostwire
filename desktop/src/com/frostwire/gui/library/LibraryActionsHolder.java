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
import com.limegroup.gnutella.gui.tables.AbstractActionsHolder;
import com.limegroup.gnutella.gui.tables.DataLine;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LibraryActionsHolder extends AbstractActionsHolder {
    LibraryActionsHolder(DataLine<?> dataLine, boolean playing) {
        super(dataLine, playing);
    }

    @Override
    public boolean isPlayable() {
        Object dl = getDataLine();
        if (dl instanceof LibraryFilesTableDataLine) {
            return MediaPlayer.isPlayableFile(((LibraryFilesTableDataLine) dl).getFile());
        }
        return false;
    }

    @Override
    public boolean isDownloadable() {
        return false;
    }

    @Override
    public File getFile() {
        AbstractLibraryTableDataLine<File> dl = (AbstractLibraryTableDataLine<File>) getDataLine();
        if (dl == null) {
            return null;
        }
        return dl.getFile();
    }
}
