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

package com.limegroup.gnutella.gui.shell;

/**
 * A registration in the platform shell that sets a program as the default viewer for a protocol link or file type.
 */
interface ShellAssociation {
    /**
     * @return true if we are currently handling this association
     */
    boolean isRegistered();

    /**
     * @return true if nobody is handling this association
     */
    boolean isAvailable();

    /**
     * Associates this running program with this protocol or file type in the shell.
     */
    void register();

    /**
     * Clears this shell association, leaving no program registered.
     */
    void unregister();
}