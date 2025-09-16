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

package com.limegroup.gnutella.gui.tables;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * Default implementation of IconAndNameHolder.
 * <p>
 * Stores an Icon and a String so that both can be displayed
 * in a single column.
 *
 * @author gubatron
 * @author aldenml
 */
final class ActionIconAndNameHolder implements Comparable<ActionIconAndNameHolder> {
    private final Icon _icon;
    private final ActionListener _action;
    private final String _name;

    public ActionIconAndNameHolder(Icon icon, ActionListener action, String name) {
        _icon = icon;
        _action = action;
        _name = name;
    }

    public int compareTo(ActionIconAndNameHolder o) {
        return AbstractTableMediator.compare(_name, o._name);
    }

    public Icon getIcon() {
        return _icon;
    }

    public String getName() {
        return _name;
    }

    public String toString() {
        return _name;
    }

    public ActionListener getAction() {
        return _action;
    }
}
