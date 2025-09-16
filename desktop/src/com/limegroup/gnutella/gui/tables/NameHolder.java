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