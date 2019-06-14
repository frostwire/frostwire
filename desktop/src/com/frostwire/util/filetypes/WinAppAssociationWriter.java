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

import org.limewire.util.OSUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
//import org.jdesktop.jdic.filetypes.Association;
//import org.jdesktop.jdic.filetypes.RegisterFailedException;

/**
 * Concrete implementation of the AppAssociationWriter class for Windows platform.
 */
public class WinAppAssociationWriter implements AppAssociationWriter {
    /**
     * Restores association registration failure.
     *
     * @param backupAssoc backup association (not null)
     * @param regLevel    given regLevel
     */
    private void restoreAssociationRegistration(BackupAssociation backupAssoc, int regLevel) {
        try {
            String curMimeType = backupAssoc.getCurMimeType();
            String curFileExt = backupAssoc.getCurFileExt();
            if (!backupAssoc.getCurMimeTypeExisted()) {
                if (curMimeType != null) {
                    WinRegistryUtil.removeMimeType(curMimeType, regLevel);
                }
            } else {
                String backupFileExt = backupAssoc.getBackupFileExt();
                if (backupFileExt != null) {
                    WinRegistryUtil.setFileExtByMimeType(backupFileExt,
                            curMimeType, regLevel);
                }
            }
            if (!backupAssoc.getCurFileExtExisted()) {
                if (curFileExt != null) {
                    WinRegistryUtil.removeFileExt(curFileExt, regLevel);
                }
            } else {
                String backupMimeType = backupAssoc.getBackupMimeType();
                if (backupMimeType != null) {
                    WinRegistryUtil.setMimeTypeByFileExt(backupMimeType,
                            curFileExt, regLevel);
                }
                String backupClassID = backupAssoc.getBackupClassID();
                if (backupClassID != null) {
                    WinRegistryUtil.setClassIDByFileExt(curFileExt,
                            backupClassID, regLevel);
                }
            }
        } catch (RegisterFailedException e) {
        }
    }

    /**
     * Restores association unregistration failure.
     *
     * @param backupAssoc backup association
     * @param regLevel    given regLevel
     */
    private void restoreAssociationUnregistration(BackupAssociation backupAssoc, int regLevel) {
        try {
            String curMimeType = backupAssoc.getCurMimeType();
            String curFileExt = backupAssoc.getCurFileExt();
            if (backupAssoc.getCurMimeTypeExisted()) {
                WinRegistryUtil.addMimeType(curMimeType, regLevel);
                String backupFileExt = backupAssoc.getBackupFileExt();
                if (backupFileExt != null) {
                    WinRegistryUtil.setFileExtByMimeType(backupFileExt,
                            curMimeType, regLevel);
                }
            }
            if (backupAssoc.getCurFileExtExisted()) {
                WinRegistryUtil.addFileExt(curFileExt, regLevel);
                String backupMimeType = backupAssoc.getBackupMimeType();
                String backupClassID = backupAssoc.getBackupClassID();
                if (backupMimeType != null) {
                    WinRegistryUtil.setMimeTypeByFileExt(backupMimeType,
                            curFileExt, regLevel);
                }
                if (backupClassID != null) {
                    WinRegistryUtil.setClassIDByFileExt(curFileExt,
                            backupClassID, regLevel);
                }
            }
        } catch (RegisterFailedException e) {
        }
    }

    /**
     * Checks whether the given assocation is valid for registration.
     * <PRE>
     * 1. The file extension list and mime type can't all be null
     * 2. If any of the fileds: description, iconFile, actionList is not null,
     * then fileExtensionList should not be empty
     * </PRE>
     *
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for registration.
     */
    public void checkAssociationValidForRegistration(Association assoc)
            throws IllegalArgumentException {
        boolean isActionListEmpty = true;
        boolean isFileExtensionEmpty = true;
        boolean isValid = false;
        // Check if actionlist is empty
        if (assoc.getActionList() != null) {
            isActionListEmpty = assoc.getActionList().isEmpty();
        }
        // Check if file extension list is empty
        if (assoc.getFileExtList() != null) {
            isFileExtensionEmpty = assoc.getFileExtList().isEmpty();
        }
        if (isFileExtensionEmpty && (assoc.getMimeType() == null)) {
            isValid = false;
        } else if ((assoc.getDescription() != null) || (assoc.getIconFileName() != null) ||
                (!isActionListEmpty)) {
            isValid = !isFileExtensionEmpty;
        } else {
            isValid = true;
        }
        if (!isValid) {
            throw new IllegalArgumentException("The given association is invalid. It should " +
                    "specify both the mimeType and fileExtensionList fields to perform this operation.");
        }
    }

    /**
     * Checks whether the given assocation is valid for unregistration.
     * If both the mimeType and fileExtensionList field is null, throw exception.
     *
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for unregistration.
     */
    public void checkAssociationValidForUnregistration(Association assoc)
            throws IllegalArgumentException {
        boolean isFileExtListEmpty = true;
        if (assoc.getFileExtList() != null) {
            isFileExtListEmpty = assoc.getFileExtList().isEmpty();
        }
        if ((assoc.getMimeType() == null) && isFileExtListEmpty) {
            throw new IllegalArgumentException("The given association is invalid. It should " +
                    "specify both the mimeType and fileExtensionList fields to perform this " +
                    "operation.");
        }
    }

    /**
     * Checks whether the given assocation already existed in Windows registry.
     * <PRE>
     * The evaluation will based on the following rule:
     * 1. mimetype == null && fileExt == null
     * return false
     * 2. mimetype == null && fileExt != null
     * return !(fileExt existed in the reg table)
     * 3. mimetype != null && fileExt == null
     * return !(mimetype existed in the reg table)
     * 4. mimetype != null && fileExt != null
     * return ( (mimetype existed in the reg table) &&
     * (fileExt existed in the reg table) &&
     * (getFileExtByMimeType(mimetype) == fileExt) &&
     * (getMimeTypeByFileExt(fileExt) == mimetype) )
     * </PRE>
     *
     * @param assoc    given association (not null)
     * @param regLevel given registry level
     * @return true if the given association existed
     */
    public boolean isAssociationExist(Association assoc, int regLevel) {
        String temFileExt = null;
        String temMimeType = assoc.getMimeType();
        Iterator<String> temFileExtIter;
        if (assoc.getFileExtList() != null) {
            temFileExtIter = assoc.getFileExtList().iterator();
        } else {
            temFileExtIter = null;
        }
        if (temFileExtIter != null) {
            if (temFileExtIter.hasNext()) {
                temFileExt = temFileExtIter.next();
            }
        }
        //Check if there is the same file extension define in Win2k
        //HKEY_CURRENT_USER\\software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts
        if (WinRegistryUtil.isWin2kUserDefinedFileExtExist(temFileExt)) {
            return true;
        }
        if ((temMimeType == null) && (temFileExt == null)) {
            return false;
        } else if ((temMimeType == null) && (temFileExt != null)) {
            return WinRegistryUtil.isFileExtExist(temFileExt, regLevel);
        } else if ((temMimeType != null) && (temFileExt == null)) {
            return WinRegistryUtil.isMimeTypeExist(temMimeType, regLevel);
        } else {
            String regMimeType = WinRegistryUtil.getMimeTypeByFileExt(temFileExt, regLevel);
            String regFileExt = WinRegistryUtil.getFileExtByMimeType(temMimeType, regLevel);
            return ((WinRegistryUtil.isMimeTypeExist(temMimeType, regLevel))
                    && (WinRegistryUtil.isFileExtExist(temFileExt, regLevel))
                    && (Objects.equals(temFileExt, regFileExt))
                    && (Objects.equals(temMimeType, regMimeType)));
        }
    }

    /**
     * Registers the given association info in the specified level.
     * <PRE>
     * <p>
     * The register process will following the rules below
     * 1. mimetype == null && fileExt != null
     * Since fileExt does not exist in this case
     * 1.1 adds the fileExt
     * 1.2 adds description, icon file and action list for this fileExt
     * 2. mimetype != null && fileExt == null
     * Since mimetype does not exist in this case
     * just adds the mimetype in the reg table
     * 3. mimetype != null && fileExt != null
     * 3.1 Adds the mime type into the reg table if necessary
     * 3.2 Adds the file extension into the reg table if necessary
     * 3.3 Adds the description, icon file and action list for the fileExt
     * 3.4 Sets the mutual reference of the mime type and file extension
     * </PRE>
     *
     * @param assoc    given association (not null)
     * @param regLevel given registry level
     * @throws RegisterFailedException if the operation fails.
     */
    public void registerAssociation(Association assoc, int regLevel)
            throws RegisterFailedException {
        //LOCAL_MACHINE. Association, in this case, should only be written into LOCAL_MACHINE rather
        //than the CURRENT_USER.
        if (!OSUtils.isGoodWindows()) {
            regLevel = AppConstants.SYSTEM_LEVEL;
        }
        BackupAssociation backupAssoc = new BackupAssociation(assoc, regLevel);
        String curMimeType = backupAssoc.getCurMimeType();
        String curFileExt = backupAssoc.getCurFileExt();
        String curDescription = assoc.getDescription();
        String curIconFileName = assoc.getIconFileName();
        List<Action> curActionList = assoc.getActionList();
        boolean curMimeTypeExisted = backupAssoc.getCurMimeTypeExisted();
        boolean curFileExtExisted = backupAssoc.getCurFileExtExisted();
        try {
            if ((curMimeType == null) && (curFileExt != null)) {
                WinRegistryUtil.addFileExt(curFileExt, regLevel);
                if (curDescription != null) {
                    WinRegistryUtil.setDescriptionByFileExt(curDescription,
                            curFileExt, regLevel);
                }
                if (curIconFileName != null) {
                    WinRegistryUtil.setIconFileNameByFileExt(curIconFileName,
                            curFileExt, regLevel);
                }
                if (curActionList != null) {
                    WinRegistryUtil.setActionListByFileExt(curActionList,
                            curFileExt, regLevel);
                }
                //Mark the classID generator field
                WinRegistryUtil.markGeneratorByFileExt(curFileExt, regLevel);
            } else if ((curMimeType != null) && (curFileExt == null)) {
                WinRegistryUtil.addMimeType(curMimeType, regLevel);
            } else if ((curMimeType != null) && (curFileExt != null)) {
                if (!curMimeTypeExisted) {
                    WinRegistryUtil.addMimeType(curMimeType, regLevel);
                }
                if (!curFileExtExisted) {
                    WinRegistryUtil.addFileExt(curFileExt, regLevel);
                }
                if (curDescription != null) {
                    WinRegistryUtil.setDescriptionByFileExt(curDescription,
                            curFileExt, regLevel);
                }
                if (curIconFileName != null) {
                    WinRegistryUtil.setIconFileNameByFileExt(curIconFileName,
                            curFileExt, regLevel);
                }
                if (curActionList != null) {
                    WinRegistryUtil.setActionListByFileExt(curActionList,
                            curFileExt, regLevel);
                }
                //Mark the classID generator field.
                WinRegistryUtil.markGeneratorByFileExt(curFileExt, regLevel);
                WinRegistryUtil.setMutualRef(curFileExt, curMimeType, regLevel);
            }
        } catch (RegisterFailedException e) {
            restoreAssociationRegistration(backupAssoc, regLevel);
            throw e;
        }
    }

    /**
     * Unregisters the given association in the specified level.
     *
     * @param assoc    given association (not null)
     * @param regLevel given registry level
     * @throws RegisterFailedException if the operation fails.
     */
    public void unregisterAssociation(Association assoc, int regLevel)
            throws RegisterFailedException {
        //Note: Windows 98, Windows ME & Windows NT will only take care of registry information from
        //LOCAL_MACHINE. Association, in this case, should only be unregistered from LOCAL_MACHINE rather
        //than the CURRENT_USER.
        if (!OSUtils.isGoodWindows()) {
            regLevel = AppConstants.SYSTEM_LEVEL;
        }
        BackupAssociation backupAssoc = new BackupAssociation(assoc, regLevel);
        String curMimeType = backupAssoc.getCurMimeType();
        String curFileExt = backupAssoc.getCurFileExt();
        boolean curMimeTypeExisted = backupAssoc.getCurMimeTypeExisted();
        boolean curFileExtExisted = backupAssoc.getCurFileExtExisted();
        try {
            if (curMimeTypeExisted) {
                WinRegistryUtil.removeMimeType(curMimeType, regLevel);
            }
            if (curFileExtExisted) {
                WinRegistryUtil.removeFileExt(curFileExt, regLevel);
            }
        } catch (RegisterFailedException e) {
            restoreAssociationUnregistration(backupAssoc, regLevel);
            throw e;
        }
    }

    /**
     * Inline class for restoreing association registration/unregistration failure.
     */
    class BackupAssociation {
        // Mime type retrieved from the specified association object.
        private final String curMimeType;
        // File extension retrieved from the specified association object.
        private String curFileExt;
        private final boolean curMimeTypeExisted;
        private final boolean curFileExtExisted;
        private String backupMimeType;
        private String backupClassID;
        private String backupFileExt;
        /*
          Suppresses default constructor for noninstantiability.
         */
        //private BackupAssociation() {}

        /**
         * Constructor for class BackupAssociation
         *
         * @param assoc    the given Association (not null).
         * @param regLevel the given registeration level.
         */
        BackupAssociation(Association assoc, int regLevel) {
            curMimeType = assoc.getMimeType();
            Iterator<String> iter = null;
            List<String> temFileExtList = assoc.getFileExtList();
            if (temFileExtList != null) {
                iter = temFileExtList.iterator();
            }
            if (iter != null) {
                if (iter.hasNext()) {
                    curFileExt = iter.next();
                }
            }
            if (curMimeType != null) {
                curMimeTypeExisted = WinRegistryUtil.isMimeTypeExist(curMimeType, regLevel);
            } else {
                curMimeTypeExisted = false;
            }
            if (curFileExt != null) {
                curFileExtExisted = WinRegistryUtil.isFileExtExist(curFileExt, regLevel);
            } else {
                curFileExtExisted = false;
            }
            if (curMimeTypeExisted) {
                backupFileExt = WinRegistryUtil.getFileExtByMimeType(curMimeType, regLevel);
            }
            if (curFileExtExisted) {
                backupClassID = WinRegistryUtil.getClassIDByFileExt(curFileExt, regLevel);
                backupMimeType = WinRegistryUtil.getMimeTypeByFileExt(curFileExt, regLevel);
            }
        }

        /**
         * Retrieve mime type
         *
         * @return mime type
         */
        String getCurMimeType() {
            return curMimeType;
        }

        /**
         * Retrieves file extension
         *
         * @return file extension
         */
        String getCurFileExt() {
            return curFileExt;
        }

        /**
         * Return true if the mime type existed in the Windows registry table
         *
         * @return true if the mime type existed
         */
        boolean getCurMimeTypeExisted() {
            return curMimeTypeExisted;
        }

        /**
         * Returns true if the file extension existed in the Windows registry table
         *
         * @return true if the file extension existed
         */
        boolean getCurFileExtExisted() {
            return curFileExtExisted;
        }

        /**
         * Returns the backup mime type information
         *
         * @return backup mime type information
         */
        String getBackupMimeType() {
            return backupMimeType;
        }

        /**
         * Returns the backup class ID
         *
         * @return backup class ID information
         */
        String getBackupClassID() {
            return backupClassID;
        }

        /**
         * Returns backup file extension
         *
         * @return backup file extensio
         */
        String getBackupFileExt() {
            return backupFileExt;
        }
    }
}
