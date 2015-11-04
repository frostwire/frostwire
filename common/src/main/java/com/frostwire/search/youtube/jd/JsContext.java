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

package com.frostwire.search.youtube.jd;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 *
 */
class JsContext {

    public JsContext(String jscode) {
        this.jscode = new StringBuilder(jscode);
        this.functions = new HashMap<String, LambdaN>();
        this.objects = new HashMap<String, JsObject>();
    }

    public final StringBuilder jscode;
    public final Map<String, LambdaN> functions;
    public final Map<String, JsObject> objects;

    public void free() {
        jscode.setLength(0);
        jscode.trimToSize();
    }
}
