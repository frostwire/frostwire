/*
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