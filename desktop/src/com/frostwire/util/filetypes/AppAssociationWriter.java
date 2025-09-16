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
    int USER_LEVEL = AppConstants.USER_LEVEL;
    int SYSTEM_LEVEL = AppConstants.SYSTEM_LEVEL;

    /**
     * Checks whether the given assocation is valid for registration according to
     * platform-specific logic.
     *
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for registration.
     */
    void checkAssociationValidForRegistration(Association assoc)
            throws IllegalArgumentException;

    /**
     * Checks whether the given assocation is valid for unregistration according to
     * platform-specific logic.
     *
     * @param assoc a given Association object.
     * @throws IllegalArgumentException if the given association is not valid for unregistration.
     */
    void checkAssociationValidForUnregistration(Association assoc)
            throws IllegalArgumentException;

    /**
     * Checks whether the given assocation exists in the system
     *
     * @param assoc a given Association object.
     * @param level a given MIME database level.
     * @return true if the given Association already exists in the specified MIME database.
     */
    boolean isAssociationExist(Association assoc, int level);

    /**
     * Registers the given association within specified level.
     *
     * @param assoc a given Association object.
     * @param level a given registration level
     * @throws RegisterFailedException               if the given association fails to be registered.
     */
    void registerAssociation(Association assoc, int level)
            throws RegisterFailedException;

    /**
     * Unregisters the given association in specified level.
     *
     * @param assoc a given Association object.
     * @param level a given registration level
     * @throws RegisterFailedException           if the given association fails to be unregistered.
     */
    void unregisterAssociation(Association assoc, int level)
            throws RegisterFailedException;
}
