/*
 * File    : TimerEvent.java
 * Created : 21-Nov-2003
 * By      : parg
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.gudy.azureus2.core3.util;

/**
 * @author parg
 */
public class
TimerEvent
        extends ThreadPoolTask
        implements Comparable<TimerEvent> {
    private final TimerEventPerformer performer;
    private final boolean absolute;
    private String name;
    private long when;
    private long unique_id = 1;

    TimerEvent(long _unique_id,
               long _when,
               boolean _absolute,
               TimerEventPerformer _performer) {
        unique_id = _unique_id;
        when = _when;
        absolute = _absolute;
        performer = _performer;
    }

    public String
    getName() {
        return (name);
    }

    public void
    setName(
            String _name) {
        name = _name;
    }

    public long
    getWhen() {
        return (when);
    }

    void
    setWhen(
            long new_when) {
        when = new_when;
    }

    AERunnable
    getRunnable() {
        return (this);
    }

    TimerEventPerformer
    getPerformer() {
        return (performer);
    }

    boolean
    isAbsolute() {
        return (absolute);
    }

    public void
    runSupport() {
        performer.perform(this);
    }

    void
    setHasRun() {
    }

    public int
    compareTo(
            TimerEvent other) {
        long res = when - other.when;
        if (res == 0) {
            res = unique_id - other.unique_id;
            if (res == 0) {
                return (0);
            }
        }
        return res < 0 ? -1 : 1;
    }
}
