/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
