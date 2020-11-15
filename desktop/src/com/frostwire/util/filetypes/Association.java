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

import java.util.*;
//import org.jdesktop.jdic.filetypes.internal.AppUtility;

/**
 * This class represents a file type association.
 * <p>
 * A file type association contains a description, the MIME type, file extensions,
 * icon file, actions which are represented by <code>Action</code> objects, and
 * stored MIME file name for Gnome/UNIX platforms.
 * <p>
 * An association could be registered into or unregistered from the system using
 * certain methods of <code>AssociationService</code>. An association for a particular
 * file type could be returned by certain methods of <code>AssociationService</code>.
 * <p>
 *
 * @see Action
 * @see AssociationService
 */
public class Association {
    /**
     * The name of the generated or removed MIME files on Gnome while
     * registering/unregistering an association.
     */
    private String name;
    /**
     * Description of the association.
     */
    private String description;
    /**
     * Mime type of the association.
     */
    private String mimeType;
    /**
     * File extension list of the association.
     * <p>
     * For Gnome/Unix platforms, all the file extentions in the list will be used.
     * For Microsoft Windows platforms, only the first file extension in the list is used.
     */
    private List<String> fileExtensionList;
    /**
     * Icon file name of the association.
     */
    private String iconFileName;
    /**
     * Action list of the association.
     */
    private List<Action> actionList;
    /**
     * Hashcode for this association
     */
    private int hashcode;

    /**
     * Returns the name of the MIME files the association is stored in
     * for Gnome/Unix platforms.
     *
     * @param name a given name value.
     */
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("The given MIME file name is null.");
        }
        this.name = name;
    }

    /**
     * Returns the description string of this <code>Association</code>.
     *
     * @return the description of this association.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description string of this <code>Association</code>.
     *
     * @param description a given description value.
     */
    public void setDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("The given description is null.");
        }
        this.description = description;
    }

    /**
     * Returns the MIME type of this <code>Association</code>.
     *
     * @return the MIME type.
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets the MIME type of this <code>Association</code>.
     *
     * @param mimeType a given MIME type.
     */
    public void setMimeType(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("The given MIME type is null.");
        }
        this.mimeType = mimeType;
    }

    /**
     * Adds one file extension to the file extension list of this <code>Association</code>.
     * If the given file extension already exists in the file extension
     * list, no changes are made to the file extension list.
     * <p>
     * The specified file extension could have a leading '.' character or not.
     * <p>
     * For Microsoft Windows platforms, only the first file extension is used during
     * registeration.
     *
     * @param fileExt a given file extension.
     */
    public void addFileExtension(String fileExt) {
        if (fileExt == null) {
            throw new IllegalArgumentException("The given file extension is null.");
        }
        // Add the leading '.' character to the given file extension if not exists.
        fileExt = AppUtility.addDotToFileExtension(fileExt);
        if (fileExtensionList == null) {
            fileExtensionList = new ArrayList<>();
        }
        fileExtensionList.add(fileExt);
    }

    /**
     * Returns the file extension list of this <code>Association</code>.
     *
     * @return the file extension list of the association.
     */
    List<String> getFileExtList() {
        // Make defensive copy
        if (fileExtensionList == null) {
            return null;
        } else {
            return new ArrayList<>(fileExtensionList);
        }
    }

    /**
     * Returns the icon file name representing this <code>Association</code>.
     *
     * @return the icon file name for this association.
     */
    String getIconFileName() {
        return iconFileName;
    }

    /**
     * Sets the icon file name representing this <code>Association</code>.
     * <p>
     * For Microsoft Windows platforms, the given icon file will be registered
     * only if the given file extension list is not empty.
     *
     * @param fileName a given icon file name.
     */
    public void setIconFileName(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("The given icon file name is null.");
        }
        this.iconFileName = fileName;
    }

    /**
     * Adds a given action to the action list of this <code>Association</code>.
     * If the given action already exists in the action list, no changes are
     * made to the action list.
     * <p>
     * A valid action should not have null verb or command field.
     * <p>
     * For Microsoft Windows platforms, an association with non-empty action list
     * would be valid for registration when the file extension list is not empty.
     *
     * @param action a given action.
     */
    public void addAction(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("The given action is null.");
        }
        // Check the specified action object has no null verb and command field.
        if (action.getVerb() == null) {
            throw new IllegalArgumentException("the given action object has null verb field.");
        } else if (action.getCommand() == null) {
            throw new IllegalArgumentException("the given action object has null command field.");
        }
        if (actionList == null) {
            actionList = new ArrayList<>();
        }
        actionList.add(new Action(action.getVerb(), action.getCommand(),
                action.getDescription()));
    }

    /**
     * Returns the action list of this <code>Association</code>.
     *
     * @return the action list of the association.
     */
    List<Action> getActionList() {
        // Make defensive copy
        if (actionList == null || actionList.isEmpty()) {
            return Collections.emptyList();
        } else {
            return new ArrayList<>(actionList);
        }
    }

    /**
     * Returns the action, whose verb field is the same with the specified verb.
     *
     * @param verb the specified verb.
     * @return the action with the specified verb; <code>null</code> if no
     * appropriate action is found.
     */
    public Action getActionByVerb(String verb) {
        Iterator<Action> iter;
        if (actionList != null) {
            iter = actionList.iterator();
            if (iter != null) {
                while (iter.hasNext()) {
                    Action temAction = iter.next();
                    String temVerb = temAction.getVerb();
                    if (temVerb.equalsIgnoreCase(verb)) {
                        return temAction;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Overrides the same method of <code>java.lang.Object</code>.
     * <p>
     * Determines whether or not two associations are equal. Two instances
     * of <code>Association</code> are equal if the values of all the fields
     * are the same.
     *
     * @param other an object to be compared with this <code>Association</code>.
     * @return <code>true</code> if the object to be compared is an instance of
     * <code>Association</code> and has the same values;
     * <code>false</code> otherwise.
     */
    public boolean equals(Object other) {
        if (!(other instanceof Association)) {
            return false;
        }
        Association otherAssoc = (Association) other;

        /*
         * Compares if the basic part of the association (description, icon file, mime type)
         * equals
         */
        boolean isBasicEquals, isActionListEquals, isFileExtListEquals;
        String otherDesc = otherAssoc.getDescription();
        String otherIconFileName = otherAssoc.getIconFileName();
        String otherMimeType = otherAssoc.getMimeType();
        isBasicEquals = ((Objects.equals(description, otherDesc))
                && (Objects.equals(iconFileName, otherIconFileName))
                && (Objects.equals(mimeType, otherMimeType)));
        if (!isBasicEquals) {
            return false;
        }
        //Compare if the file extension list equals
        List<String> otherFileExtList = otherAssoc.getFileExtList();
        isFileExtListEquals = false;
        //fileExtlistEqulas when
        //1. both file extension lists are null
        //2. neither file extension lists is null and they have same elements
        if ((fileExtensionList == null) && (otherFileExtList == null)) {
            isFileExtListEquals = true;
        } else if ((fileExtensionList != null) && (otherFileExtList != null)) {
            if ((fileExtensionList.containsAll(otherFileExtList)) &&
                    (otherFileExtList.containsAll(fileExtensionList))) {
                isFileExtListEquals = true;
            }
        }
        if (!isFileExtListEquals) {
            return false;
        }
        //Compare if the action list equals
        List<Action> otherActionList = otherAssoc.getActionList();
        isActionListEquals = false;
        //action list Equlas when
        //1. both action lists are null
        //2. neither action lists is null and they have same elements
        if ((actionList == null) && (otherActionList != null)) {
            isActionListEquals = true;
        } else if ((actionList != null) && (otherActionList != null)) {
            if ((actionList.containsAll(otherActionList)) &&
                    (otherActionList.containsAll(actionList))) {
                isActionListEquals = true;
            }
        }
        return isActionListEquals;
    }

    /**
     * Overrides the same method of <code>java.lang.Object</code>.
     * <p>
     * Returns the hashcode for this <code>Association</code>.
     *
     * @return a hash code for this <code>Association<code>.
     */
    public int hashCode() {
        if (hashcode != 0) {
            int result = 17;
            if (this.name != null) {
                result = result * 37 + this.name.hashCode();
            }
            if (this.description != null) {
                result = result * 37 + this.description.hashCode();
            }
            if (this.mimeType != null) {
                result = result * 37 + this.mimeType.hashCode();
            }
            if (this.iconFileName != null) {
                result = result * 37 + this.iconFileName.hashCode();
            }
            if (this.fileExtensionList != null) {
                result = result * 37 + this.fileExtensionList.hashCode();
            }
            if (this.actionList != null) {
                result = result * 37 + this.actionList.hashCode();
            }
            hashcode = result;
        }
        return hashcode;
    }

    /**
     * Overrides the same method of <code>java.lang.Object</code>.
     * <p>
     * Returns a <code>String</code> that represents the value of this
     * <code>Association</code>.
     *
     * <PRE>
     * The output of this object as a string would be like:
     * MIME File Name:
     * Description:
     * MIME Type:
     * Icon File:
     * File Extension:
     * Action List:
     * Description:
     * Verb:
     * Command:
     * </PRE>
     *
     * @return a string representation of this <code>Association</code>.
     */
    public String toString() {
        String crlfString = "\r\n";
        String content = "";
        Iterator<?> temIter;
        content = content.concat("MIME File Name: ");
        if (this.name != null) {
            content = content.concat(name);
        }
        content = content.concat(crlfString);
        content = content.concat("Description: ");
        if (this.description != null) {
            content = content.concat(description);
        }
        content = content.concat(crlfString);
        content = content.concat("MIME Type: ");
        if (this.mimeType != null) {
            content = content.concat(mimeType);
        }
        content = content.concat(crlfString);
        content = content.concat("Icon File: ");
        if (this.iconFileName != null) {
            content = content.concat(iconFileName);
        }
        content = content.concat(crlfString);
        content = content.concat("File Extension: ");
        if (fileExtensionList != null) {
            temIter = fileExtensionList.iterator();
            if (temIter != null) {
                while (temIter.hasNext()) {
                    content = content.concat((String) temIter.next());
                    if (temIter.hasNext()) {
                        content = content.concat(" ");
                    }
                }
            }
        }
        content = content.concat(crlfString);
        content = content.concat("Action List: ");
        if (actionList != null) {
            temIter = actionList.iterator();
            if (temIter != null) {
                content = content.concat(crlfString);
                while (temIter.hasNext()) {
                    Action temAction = (Action) temIter.next();
                    content = content.concat(temAction.toString());
                }
            }
        }
        content = content.concat(crlfString);
        return content;
    }
}
