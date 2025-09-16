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

import com.frostwire.gui.LocaleLabel.LocaleString;

/**
 * @author gubatron
 * @author aldenml
 */
class PlayableCell implements Comparable<PlayableCell> {
    private final Object dataLine;
    private final Object wrappedObject;
    private final String strValue;
    private final LocaleString localeString;
    private boolean playing;
    private int column;

    public PlayableCell(Object dataLine, String strValue, boolean isPlaying, int columnIndex) {
        this(dataLine, strValue, strValue, isPlaying, columnIndex);
    }

    public PlayableCell(Object dataLine, Object wrapMe, String strValue, boolean isPlaying, int columnIndex) {
        this.dataLine = dataLine;
        this.wrappedObject = wrapMe;
        this.strValue = strValue;
        this.localeString = new LocaleString(strValue);
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public LocaleString getLocaleString() {
        return localeString;
    }

    @Override
    public String toString() {
        return strValue;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public int compareTo(PlayableCell o) {
        if (wrappedObject instanceof Comparable && wrappedObject != null && o.wrappedObject != null && wrappedObject.getClass().equals(o.wrappedObject.getClass())) {
            return ((Comparable) wrappedObject).compareTo(o.wrappedObject);
        }
        return toString().compareTo(o.toString());
    }

    public Object getDataLine() {
        return dataLine;
    }
}
