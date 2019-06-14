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
