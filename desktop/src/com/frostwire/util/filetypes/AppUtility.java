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

    
