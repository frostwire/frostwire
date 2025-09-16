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

/**
 * Abstract dataline class that
 * implements DataLine functions
 * that may not be absolutely necessary
 * in all DataLine instances
 */
public abstract class AbstractDataLine<T> implements DataLine<T> {
    /**
     * The object that initialized the dataline.
     */
    protected T initializer;

    /**
     * @implements DataLine interface
     */
    public void initialize(T o) {
        initializer = o;
    }

    /**
     * @implements DataLine interface
     */
    public T getInitializeObject() {
        return initializer;
    }

    /**
     * @implements DataLine interface
     */
    public void setInitializeObject(T o) {
        initializer = o;
    }

    /**
     * A blank implementation of setValueAt, because it is not necessary.
     *
     * @implements DataLine interface
     */
    public void setValueAt(Object o, int col) {
    }

    /**
     * A blank implementatino of cleanup, because it is not necessary.
     *
     * @implements DataLine interface
     */
    public void cleanup() {
    }

    /**
     * A blank implementation of update, because it is not necessary.
     *
     * @implements DataLine interface
     */
    public void update() {
    }

    /**
     * By default, DataLines will have no tooltip.
     */
    public String[] getToolTipArray(int col) {
        return null;
    }

    /**
     * By default, no tooltip is ever required.
     */
    public boolean isTooltipRequired(int col) {
        return false;
    }
}