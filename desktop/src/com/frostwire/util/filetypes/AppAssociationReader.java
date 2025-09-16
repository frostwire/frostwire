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
