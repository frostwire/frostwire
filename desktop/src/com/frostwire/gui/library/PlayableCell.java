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
