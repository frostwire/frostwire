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
import java.util.List;

/**
 * Containing funtions to retrieve association information
 */
public interface AppAssociationReader {
    /**
     * Returns the description associated with the given mime type.
     *
     * @param mimeType Given mime type
     * @return String
     */
    String getDescriptionByMimeType(String mimeType);

    /**
     * Returns the description associated with the given file extension.
     *
     * @param fileExt Given file extension
     * @return String
     */
    String getDescriptionByFileExt(String fileExt);

    /**
     * Returns the mime type associated with the given URL, by checking the content of
     * the URL.
     *
     * @param url The specified URL
     * @return String
     */
    String getMimeTypeByURL(URL url);

    /**
     * Returns the file extensione list associated with the given mime type.
     *
     * @param mimeType Given mime type
     * @return String
     */
    List<String> getFileExtListByMimeType(String mimeType);

    /**
     * Returns the mime type associated with the given file extension.
     *
     * @param fileExt Given file extension
     * @return String
     */
    String getMimeTypeByFileExt(String fileExt);

    /**
     * Returns the icon file name associated with the given mime type.
     *
     * @param mimeType Given mime type.
     * @return icon file name
     */
    String getIconFileNameByMimeType(String mimeType);

    /**
     * Returns the icon file name associated with the given file extension.
     *
     * @param fileExt Given file extension.
     * @return icon file name
     */
    String getIconFileNameByFileExt(String fileExt);

    /**
     * Returns the action list associated with the given file extension.
     *
     * @param fileExt Given file extension
     * @return the action list
     */
    List<Action> getActionListByFileExt(String fileExt);

    /**
     * Returns the action list associated with the given mime type.
     *
     * @param mimeType Given mime type
     * @return the action list
     */
    List<Action> getActionListByMimeType(String mimeType);

    /**
     * Returns true if the mime type exists in the system.
     *
     * @param mimeType given mimeType
     * @return true if the mime type exists in the system
     */
    boolean isMimeTypeExist(String mimeType);

    /**
     * Returns true if the file extension exists in the system.
     *
     * @param fileExt given file extension
     * @return true if the file extension exists in the system
     */
    boolean isFileExtExist(String fileExt);
}
