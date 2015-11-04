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

//import org.jdesktop.jdic.filetypes.Association;
//import org.jdesktop.jdic.filetypes.AssociationAlreadyRegisteredException;
//import org.jdesktop.jdic.filetypes.AssociationNotRegisteredException;
//import org.jdesktop.jdic.filetypes.RegisterFailedException;


/**
 * Containing funtions to modify the association information
 */
public interface AppAssociationWriter {

    /** 
     * Constants for the registration/unregistration level.
     */
    public final static int USER_LEVEL = AppConstants.USER_LEVEL;
    public final static int SYSTEM_LEVEL = AppConstants.SYSTEM_LEVEL;
    public final static int DEFAULT_LEVEL = AppConstants.DEFAULT_LEVEL;

    /**
     * Checks whether the given assocation is valid for registration according to 
     * platform-specific logic.
     * 
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for registration. 
     */
    public void checkAssociationValidForRegistration(Association assoc) 
        throws IllegalArgumentException;  

    /**
     * Checks whether the given assocation is valid for unregistration according to 
     * platform-specific logic.
     * 
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for unregistration.
     */
    public void checkAssociationValidForUnregistration(Association assoc) 
        throws IllegalArgumentException;
    
    /**
     * Checks whether the given assocation exists in the system
     * 
     * @param assoc a given Association object.
     * @param level a given MIME database level.
     * @return true if the given Association already exists in the specified MIME database.
     */
    public boolean isAssociationExist(Association assoc, int level);

    /**
     * Registers the given association within specified level. 
     * 
     * @param assoc a given Association object.
     * @param level a given registration level
     * @throws AssociationAlreadyRegisteredException if the given association has
     *         been registered in the system.
     * @throws RegisterFailedException if the given association fails to be registered.
     */
    public void registerAssociation(Association assoc, int level) 
            throws AssociationAlreadyRegisteredException, RegisterFailedException;

    /**
     * Unregisters the given association in specified level.
     * 
     * @param assoc a given Association object.
     * @param level a given registration level
     * @throws AssociationNotRegisteredException if the given association has not been 
     *         registered before.
     * @throws RegisterFailedException if the given association fails to be unregistered.   
     */
    public void unregisterAssociation(Association assoc, int level) 
            throws AssociationNotRegisteredException, RegisterFailedException;
}
