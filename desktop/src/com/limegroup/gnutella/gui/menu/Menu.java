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

package com.limegroup.gnutella.gui.menu;

import javax.swing.*;

/**
 * Defines the minimal necessary methods for menus in the main application
 * menu bar.
 */
interface Menu {
    /**
     * Returns the <tt>JMenu</tt> instance for this <tt>Menu</tt>.
     *
     * @return the <tt>JMenu</tt> instance for this <tt>Menu</tt>
     */
    JMenu getMenu();
}
