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

package com.frostwire.licenses;

import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public final class CreativeCommonsLicense extends License {
    private final String acronym;

    /**
     * To use with raw input
     *
     * @param name
     * @param url
     * @param acronym
     */
    CreativeCommonsLicense(String name, String url, String acronym) {
        super(name, url);
        this.acronym = acronym;
    }

    static CreativeCommonsLicense standard(String name, String acronym, String version) {
        String fullName = "Creative Commons " + name + " " + version;
        String url = "http://creativecommons.org/licenses/" + acronym.toLowerCase(Locale.US) + "/" + version + "/";
        String fullAcronym = "CC " + acronym.toUpperCase(Locale.US) + " " + version;
        return new CreativeCommonsLicense(fullName, url, fullAcronym);
    }

    public String acronym() {
        return acronym;
    }
}
