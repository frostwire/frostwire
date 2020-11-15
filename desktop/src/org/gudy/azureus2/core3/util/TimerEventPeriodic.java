/*
 * File    : TimerEventPeriodic.java
 * Created : 07-Dec-2003
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

import com.frostwire.util.Logger;

/**
 * @author parg
 */
public class
TimerEventPeriodic
        implements TimerEventPerformer {
    private static final Logger LOG = Logger.getLogger(TimerEventPeriodic.class);
    private final Timer timer;
    private final long frequency;
    private final boolean absolute;
    private final TimerEventPerformer performer;
    private String name;
    private TimerEvent current_event;

    TimerEventPeriodic(
            Timer _timer,
            long _frequency,
            boolean _absolute,
            TimerEventPerformer _performer) {
        timer = _timer;
        frequency = _frequency;
        absolute = _absolute;
        performer = _performer;
        long now = SystemTime.getCurrentTime();
        current_event = timer.addEvent(now, now + frequency, absolute, this);
    }

    public void
    setName(
            String _name) {
        name = _name;
        synchronized (this) {
            if (current_event != null) {
                current_event.setName(name);
            }
        }
    }

    long
    getFrequency() {
        return (frequency);
    }

    public void
    perform(
            TimerEvent event) {
        try {
            performer.perform(event);
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
        synchronized (this) {
            long now = SystemTime.getCurrentTime();
            current_event = timer.addEvent(name, now + frequency, absolute, this);
        }
    }
}
