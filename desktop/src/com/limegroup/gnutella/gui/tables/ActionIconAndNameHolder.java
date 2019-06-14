/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
