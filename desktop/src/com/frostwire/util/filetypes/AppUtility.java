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

package com.frostwire.util.filetypes;

import java.net.URL;

/**
 * Utility class containing shared methods.
 */
class AppUtility {
    /**
     * Suppress default constructor for noninstantiability.
     */
    private AppUtility() {
    }

    /**
     * Returns the file extension from the file part of the URL.
     * The returned file extension include the leading '.' character.
     * <p>
     * For example: if the URL is http://www.sun.com/index.html, the
     * returned file extension is ".html".
     *
     * @param url the specified URL
     * @return the file extension of the file part of the URL.
     */
    public static String getFileExtensionByURL(URL url) {
        String trimFile = url.getFile().trim();
        if (trimFile == null || trimFile.equals("") || trimFile.equals("/")) {
            return null;
        }
        int strIndex = trimFile.lastIndexOf("/");
        String filePart = trimFile.substring(strIndex + 1);
        strIndex = filePart.lastIndexOf(".");
        if (strIndex == -1 || strIndex == filePart.length() - 1) {
            return null;
        } else {
            return filePart.substring(strIndex);
        }
    }

    /**
     * Adds one leading '.' character for the specified file extension.
     * If the leading '.' character already exists, it just returns.
     *
     * @param fileExt the specified file extension.
     * @return file extension with a leading '.' character.
     */
    public static String addDotToFileExtension(String fileExt) {
        String temFileExt = fileExt;
        if (fileExt.charAt(0) != '.') {
            String dotStr = ".";
            temFileExt = dotStr.concat(fileExt);
        }
        return temFileExt;
    }
}

    
