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

package com.frostwire.content;

import com.limegroup.gnutella.settings.ApplicationSettings;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public class Context {
    public Context() {
    }

    /**
     * Returns the absolute path on the filesystem where a database created with
     *  is stored.
     *
     * @param name The name of the database for which you would like to get
     *             its path.
     * @return Returns an absolute path to the given database.
     */
    public File getDatabasePath(String name) {
        return new File(ApplicationSettings.APP_DATABASES_PATH.getValue(), name);
    }
}
