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
import java.util.Iterator;
import java.util.List;
//import org.jdesktop.jdic.filetypes.internal.AppAssociationWriter;
//import org.jdesktop.jdic.filetypes.internal.AppAssociationWriterFactory;
//import org.jdesktop.jdic.filetypes.internal.AppAssociationReader;
//import org.jdesktop.jdic.filetypes.internal.AppAssociationReaderFactory;
//import org.jdesktop.jdic.filetypes.internal.AppUtility;
//import org.jdesktop.jdic.init.JdicInitException;
//import org.jdesktop.jdic.init.JdicManager;


/**
 * The <code>AssociationService</code> class provides several methods to access
 * the file type associations. It includes methods to retrieve a particular
 * file type association, to register a new file type association into the system, 
 * and to unregister a registered file type association from the system. 
 * Each file type association is represented by an <code>Association</code> object.
 * <p>  
 * The file type information storage and access mechanism is platform-dependent: 
 * <ul>
 *   <li> For Microsoft Windows platforms, the file type information is stored 
 *        in the registry, which is a tree structure. 
 *   <li> For Gnome/UNIX platforms, the file type information is stored in the
 *        MIME type database, which consists of some plain text files 
 *        (.keys, .mime, .applications). Each MIME text file could contain one 
 *        or multiple file types. A file type could be registered in the system 
 *        by providing new MIME files, or being added to existing MIME files.
 * </ul>
 * 
 * @see Association
 * @see Action
 */
public class AssociationService {
    // A platform-dependent instance of AppAssociationReader.
    private AppAssociationReader appAssocReader;
    // A platform-dependent instance of AppAssociationWriter.
    private AppAssociationWriter appAssocWriter;
  
    // Add the initialization code from package org.jdesktop.jdic.init.
    // To set the environment variables or initialize the set up for 
    // native libraries and executable files.
    static {
//        try {
//            JdicManager jm = JdicManager.getManager();
//            jm.initShareNative();
//        } catch (JdicInitException e){
//            e.printStackTrace();
//        }
    }

    /**
     * Constructor of an <code>AssociationService</code> object.
     */
    public AssociationService() {
        appAssocReader = AppAssociationReaderFactory.newInstance();
        appAssocWriter = AppAssociationWriterFactory.newInstance();
    }
  
    /**
     * Returns the association representing the file type of the 
     * given MIME type.
     *
     * @param mimeType a given MIME type name.
     * @return the appropriate <code>Association</code> object; <code>null</code> 
     *         if the given MIME type is not found in the system.
     */
    public Association getMimeTypeAssociation(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("The specified mime type is null");
        }

        // Check whether the given mime type exists/is registered in the system.
        if (!appAssocReader.isMimeTypeExist(mimeType)) {
            return null;		
        }
        
        // Get the association associated with the mime type.
        Association assoc = new Association();
        List<String> fileExtList = appAssocReader.getFileExtListByMimeType(mimeType);
        String iconFileName = appAssocReader.getIconFileNameByMimeType(mimeType);
        String description = appAssocReader.getDescriptionByMimeType(mimeType);
        List<Action> actionList = appAssocReader.getActionListByMimeType(mimeType);
      
        assoc.setMimeType(mimeType);

        if (fileExtList != null) {
            Iterator<String> iter = fileExtList.iterator();

            if (iter != null) {
                while (iter.hasNext()) {
                    assoc.addFileExtension((String) iter.next());
                }
            }
        }
        
        if (iconFileName != null) {
            assoc.setIconFileName(iconFileName);
        }
        
        if (description != null) {
            assoc.setDescription(description);
        }
      
        if (actionList != null) {
            Iterator<Action> iter = actionList.iterator();

            if (iter != null) {
                while (iter.hasNext()) {
                    assoc.addAction((Action) iter.next());
                }
            }
        }
        
        return assoc;
    }        
  
    /**
     * Returns the association representing the file type of the given 
     * file extension.
     * <p>
     * The file extension list in the returned <code>Association</code> object 
     * contains only this given file extension.  
     *
     * @param fileExt a given file extension name.
     * @return the appropriate <code>Association</code> object; <code>null</code> 
     *         if the given file extension is not found in the system. 
     */
    public Association getFileExtensionAssociation(String fileExt) {
        if (fileExt == null) {
            throw new IllegalArgumentException("The specified file extension is null");
        }

        // Add the leading '.' character to the given file extension if not exists.    
        fileExt = AppUtility.addDotToFileExtension(fileExt);

        // Check whether the given file extension exists/is registered in the system.
        if (!appAssocReader.isFileExtExist(fileExt)) {
            return null;
        }
        
        // Get the association associated with the file extension.
        Association assoc = new Association();
        String mimeType = appAssocReader.getMimeTypeByFileExt(fileExt);        
        String iconFileName = appAssocReader.getIconFileNameByFileExt(fileExt);
        String description = appAssocReader.getDescriptionByFileExt(fileExt);
        List<Action> actionList = appAssocReader.getActionListByFileExt(fileExt);
      
        // Do not retrieve other file extensions.
        assoc.addFileExtension(fileExt);
        
        if (iconFileName != null) {
            assoc.setIconFileName(iconFileName);
        }
        
        if (mimeType != null) {
            assoc.setMimeType(mimeType);
        }
        
        if (description != null) {
            assoc.setDescription(description);
        }
      
        if (actionList != null) {
            Iterator<Action> iter = actionList.iterator();

            if (iter != null) {
                while (iter.hasNext()) {
                    assoc.addAction((Action) iter.next());
                }
            }
        }
        
        return assoc;
    }        
  
    /**
     * Returns the association representing the file type of the file the given 
     * URL points to.
     *     
     * @param url a given URL.
     * @return the appropriate <code>Association</code> object; <code>null</code> 
     *         if the file type of the file the given URL points to is not 
     *         found in the system. 
     */
    public Association getAssociationByContent(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("The specified URL is null");
        }
        
        Association assoc = null;
        String mimeType = appAssocReader.getMimeTypeByURL(url);

        if (mimeType != null) {
            // Get association by mime type.
            assoc = getMimeTypeAssociation(mimeType);
        }
        
        if (assoc == null) {
            // Get association by file extension.
            String fileExt = AppUtility.getFileExtensionByURL(url);

            if (fileExt != null) {
                assoc = getFileExtensionAssociation(fileExt);
            }
        }
            
        return assoc;
    }        
  
    /**
     * Registers the given association in the user specific level.
     * <p>
     * <ul>
     *   <li> For Microsoft Windows platforms: the file extension list and MIME 
     *        type can't both be null. If any of the description, icon file name, action 
     *        list fields is not null, the file extension list couldn't be empty.
     *        <p> 
     *        For Windows NT, Windows Me/98/95: the registration is always system 
     *        wide, since all users share the same association information.
     *        <p> 
     *        For Windows 2000 and Windows XP: the registration is only applied to 
     *        this specific user.
     * 
     *   <li> For Gnome/Unix platforms: both the name and MIME type fields need to 
     *        be specified to perform this operation.
     * </ul>
     * 
     * @param assoc a given <code>Association</code> object.
     * @throws IllegalArgumentException if the given association is not valid for 
     *         this operation.
     * @throws AssociationAlreadyRegisteredException if the given association 
     *         already exists in the system.
     * @throws RegisterFailedException if the given association fails to be 
     *         registered in the system.
     */
    public void registerUserAssociation(Association assoc) 
            throws AssociationAlreadyRegisteredException, RegisterFailedException {
        if (assoc == null) {
            throw new IllegalArgumentException("The specified association is null");
        }

        // Check whether the specified association is valid for registration.
        try {
            appAssocWriter.checkAssociationValidForRegistration(assoc); 
        } catch (IllegalArgumentException e) {
            throw e;
        }
        
        // Check whether the specified association already exists.
        if (appAssocWriter.isAssociationExist(assoc, AppAssociationWriter.USER_LEVEL)) {
            throw new AssociationAlreadyRegisteredException("Assocation already exists!");  
        }            

        // Perform registration.                
        appAssocWriter.registerAssociation(assoc, AppAssociationWriter.USER_LEVEL);
    }                

    /**
     * Unregisters the given association in the user specific level.
     * <p>
     * <ul>
     *   <li> For Microsoft Windows platforms: either the MIME type or the file extension
     *        list field needs to be specified to perform this operation.
     *        <p>
     *        For Windows NT, Windows Me/98/95: the unregistration is always system wide, 
     *        since all users share the same association information.
     *        <p>
     *        For Windows 2000 and Windows XP: the unregistration is only applied to 
     *        this specific user.
     *
     *   <li> For Gnome/Unix platforms: only the name field needs to be specified to 
     *        perform this operation.
     * </ul>
     * <P>
     * 
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for 
     *         this operation.
     * @throws AssociationNotRegisteredException if the given association doesn't 
     *         exist in the system.
     * @throws RegisterFailedException if the given association fails to be 
     *         unregistered in the system.   
     */
    public void unregisterUserAssociation(Association assoc) 
            throws AssociationNotRegisteredException, RegisterFailedException {
        if (assoc == null) {
            throw new IllegalArgumentException("The specified association is null");
        }

        // Check whether the specified association is valid for unregistration.
        try {
            appAssocWriter.checkAssociationValidForUnregistration(assoc);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        
        // Check whether the specified association not exists.
        if (!appAssocWriter.isAssociationExist(assoc, AppAssociationWriter.USER_LEVEL)) {
            throw new AssociationNotRegisteredException("Assocation not exists!");  
        }            

        // Perform unregistration.
        appAssocWriter.unregisterAssociation(assoc, AppAssociationWriter.USER_LEVEL);
    }

    /**
     * Registers the given association in the system level.
     * <p>
     * <ul>
     *   <li> For Microsoft Windows platforms: the file extension list and MIME 
     *        type can't all be null. If any of the description, icon file name, action
     *        list fields is not null, the file extension list couldn't be empty.
     *        <p> 
     *        For Windows XP: the user needs the administrator permission to 
     *        access the system association information in the registry.
     * 
     *  <li>  For Gnome/Unix platforms: both the name and MIME type fields need to 
     *        be specified to perform this operation.
     * </ul>
     * 
     * @param assoc a given <code>Association</code> object.
     * @throws IllegalArgumentException if the given association is not valid for 
     *         this operation.
     * @throws AssociationAlreadyRegisteredException if the given association 
     *         already exists in the system.
     * @throws RegisterFailedException if the given association fails to be 
     *         registered in the system.
     */
    public void registerSystemAssociation(Association assoc) 
            throws AssociationAlreadyRegisteredException, RegisterFailedException {
        if (assoc == null) {
            throw new IllegalArgumentException("The specified association is null");
        }

        // Check whether the specified association is valid for registration.
        try {
            appAssocWriter.checkAssociationValidForRegistration(assoc);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        
        // Check whether the specified association already exists.
        if (appAssocWriter.isAssociationExist(assoc, AppAssociationWriter.SYSTEM_LEVEL)) {
            throw new AssociationAlreadyRegisteredException("Assocation already exists!");  
        }            

        // Perform registration.
        appAssocWriter.registerAssociation(assoc, AppAssociationWriter.SYSTEM_LEVEL);
    }

    /**
     * Unregisters the given association in the system level.
     * <p>
     * <ul>
     *   <li> For Microsoft Windows platforms: either the MIME type or the file extension
     *        list field needs to be specified to perform this operation.
     *        <p>
     *        For Windows XP: the user needs the administrator permission to access the 
     *        system association information in the registry.
     *
     *   <li> For Gnome/Unix platforms: only the name field needs to be specified to 
     *        perform this operation.
     * </ul>
     * <P>
     * 
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for 
     *         this operation.
     * @throws AssociationNotRegisteredException if the given association doesn't 
     *         exist in the system.
     * @throws RegisterFailedException if the given association fails to be 
     *         unregistered in the system.   
     */
    public void unregisterSystemAssociation(Association assoc)
        throws AssociationNotRegisteredException, RegisterFailedException {
        if (assoc == null) {
            throw new IllegalArgumentException("The specified association is null");
        }

        // Check whether the specified association is valid for unregistration.
        try {
            appAssocWriter.checkAssociationValidForUnregistration(assoc); 
        } catch (IllegalArgumentException e) {
            throw e;
        }
        
        // Check whether the specified association not exists.
        if (!appAssocWriter.isAssociationExist(assoc, AppAssociationWriter.SYSTEM_LEVEL)) {
            throw new AssociationNotRegisteredException("Assocation not existed!");  
        }            

        appAssocWriter.unregisterAssociation(assoc, AppAssociationWriter.SYSTEM_LEVEL);
    }
}
