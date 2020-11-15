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

package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.LocaleLabel.LocaleString;

/**
 * @author gubatron
 * @author aldenml
 */
public class NameHolder implements Comparable<NameHolder> {
    private final String displayName;
    private final LocaleString localeString;

    public NameHolder(String str) {
        this.displayName = str;
        this.localeString = new LocaleString(str);
    }

    public int compareTo(NameHolder o) {
        return AbstractTableMediator.compare(displayName, o.displayName);
    }

    public LocaleString getLocaleString() {
        return localeString;
    }

    public String toString() {
        return displayName;
    }
}