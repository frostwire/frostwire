/*
 * Copyright (C) 2004 Sun Microsystems, Inc. All rights reserved. Use is
 * subject to license terms.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package com.frostwire.util.filetypes;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete implementation of the AppAssociationReader class for Windows platform.
 */
public class WinAppAssociationReader implements AppAssociationReader {
    /**
     * Retrieves the description associated with the given mime type.
     *
     * @param mimeType given mime type (not null)
     * @return relevant description, or null if not available
     */
    public String getDescriptionByMimeType(String mimeType) {
        String temFileExt = WinRegistryUtil.getFileExtByMimeType(mimeType);
        if (temFileExt != null) {
            return getDescriptionByFileExt(temFileExt);
        } else {
            return null;
        }
    }

    /**
     * Retrieves the description associated with the given file extension.
     *
     * @param fileExt given file extension (not null)
     * @return relevant description for the given file extension
     */
    public String getDescriptionByFileExt(String fileExt) {
        return WinRegistryUtil.getDescriptionByFileExt(fileExt);
    }

    /**
     * Retrieves the mime type associated with the given URL,
     * by checking the content of the URL.
     *
     * @param url given URL (not null)
     * @return corresponding mime type
     */
    public String getMimeTypeByURL(URL url) {
        return WinRegistryUtil.getMimeTypeByURL(url);
    }

    /**
     * Retrieves the file extension list associated with the given mime type.
     *
     * @param mimeType given mime type (not null)
     * @return corresponding file list, or null if not available.
     */
    public List<String> getFileExtListByMimeType(String mimeType) {
        String fileExt = WinRegistryUtil.getFileExtByMimeType(mimeType);
        if (fileExt != null) {
            List<String> fileExtList = new ArrayList<>();
            fileExtList.add(fileExt);
            return fileExtList;
        }
        return null;
    }

    /**
     * Retrieves the mime type associated with the given file extension.
     *
     * @param fileExt given file extension (not null)
     * @return corresponding mime type
     */
    public String getMimeTypeByFileExt(String fileExt) {
        return WinRegistryUtil.getMimeTypeByFileExt(fileExt);
    }

    /**
     * Retrieves the icon file name associated with the given mime type.
     *
     * @param mimeType given mime type (not null)
     * @return corresponding icon file name, or null if no available
     */
    public String getIconFileNameByMimeType(String mimeType) {
        String temFileExt = WinRegistryUtil.getFileExtByMimeType(mimeType);
        if (temFileExt != null) {
            return getIconFileNameByFileExt(temFileExt);
        } else {
            return null;
        }
    }

    /**
     * Retrieves the icon file name associated with the given file extension.
     *
     * @param fileExt given file extension (not null)
     * @return corresponding icon file name
     */
    public String getIconFileNameByFileExt(String fileExt) {
        return WinRegistryUtil.getIconFileNameByFileExt(fileExt);
    }

    /**
     * Retrieves the action list associated with the given mime type.
     *
     * @param mimeType given mime type (not null)
     * @return corresponding action list, or null if not available
     */
    public List<Action> getActionListByMimeType(String mimeType) {
        String temFileExt = WinRegistryUtil.getFileExtByMimeType(mimeType);
        if (temFileExt != null) {
            return getActionListByFileExt(temFileExt);
        } else {
            return null;
        }
    }

    /**
     * Retrieves the action list associated with the given file extension.
     *
     * @param fileExt given file extension (not null)
     * @return corresponding action list
     */
    public List<Action> getActionListByFileExt(String fileExt) {
        return WinRegistryUtil.getActionListByFileExt(fileExt);
    }

    /**
     * Returns true if the mime type exists in Windows Registry.
     *
     * @param mimeType given mimeType
     * @return true if the mime type exists in the Registry
     */
    public boolean isMimeTypeExist(String mimeType) {
        return WinRegistryUtil.isMimeTypeExist(mimeType);
    }

    /**
     * Returns true if the file extension exists in Windows Registry.
     *
     * @param fileExt given file extension
     * @return true if the file extension exists in the Registry
     */
    public boolean isFileExtExist(String fileExt) {
        return WinRegistryUtil.isFileExtExist(fileExt);
    }
}
