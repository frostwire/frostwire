/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public final class TestUtil {
    public static File getTestResource(String fileName) throws IOException {
        URL resource = TestUtil.class.getResource(fileName);
        if (resource == null) {
            throw new IOException("TestUtil::getTestResource: resource not found: " + fileName);
        }
        return new File(resource.getPath());
    }
}
