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