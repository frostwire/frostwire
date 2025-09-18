/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.mplayer;

import java.util.Locale;

public class Language {
    private final String id;
    private final LanguageSource source;
    private String name;
    private Locale language;

    public Language(LanguageSource source, String id) {
        this.source = source;
        this.id = id;
    }

    public Locale getLanguage() {
        return language;
    }

    public void setLanguage(String isoCode) {
        language = ISO639.getLocaleFromISO639_2(isoCode);
    }

    public void setLanguage(Locale locale) {
        language = locale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public LanguageSource getSource() {
        return source;
    }

    public void setSourceInfo(String sourceInfo) {
    }
}
