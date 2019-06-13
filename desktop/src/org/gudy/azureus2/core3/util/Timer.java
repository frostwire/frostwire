/*
 * File    : Timer.java
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

import java.util.*;

public class Timer
        extends AERunnable
        implements SystemTime.ChangeListener {
    private final ThreadPool thread_pool;
    private Set<TimerEvent> events = new TreeSet<>();
    private long unique_id_next = 0;
    private long current_when;

    public Timer(
            String name,
            int thread_pool_size) {
        this(name, thread_pool_size, Thread.NORM_PRIORITY);
    }

    private Timer(
            String name,
            int thread_pool_size,
            int thread_priority) {
        thread_pool = new ThreadPool(name, thread_pool_size);
        SystemTime.registerClockChangeListener(this);
        Thread t = new Thread(this, "Timer:" + name);
        t.setDaemon(true);
        t.setPriority(thread_priority);
        t.start();
    }

    void setWarnWhenFull() {
        thread_pool.setWarnWhenFull();
    }

    public void
    runSupport() {
        while (true) {
            try {
                TimerEvent event_to_run = null;
                synchronized (this) {
                    if (events.isEmpty()) {
                        // System.out.println( "waiting forever" );
                        try {
                            current_when = Integer.MAX_VALUE;
                            this.wait();
                        } finally {
                            current_when = 0;
                        }
                    } else {
                        long now = SystemTime.getCurrentTime();
                        TimerEvent next_event = events.iterator().next();
                        long when = next_event.getWhen();
                        long delay = when - now;
                        if (delay > 0) {
                            try {
                                current_when = when;
                                this.wait(delay);
                            } finally {
                                current_when = 0;
                            }
                        }
                    }
                    if (events.isEmpty()) {
                        continue;
                    }
                    long now = SystemTime.getCurrentTime();
                    Iterator<TimerEvent> it = events.iterator();
                    TimerEvent next_event = it.next();
                    long rem = next_event.getWhen() - now;
                    if (rem <= SystemTime.TIME_GRANULARITY_MILLIS) {
                        event_to_run = next_event;
                        it.remove();
                    }
                }
                if (event_to_run != null) {
                    event_to_run.setHasRun();
                    thread_pool.run(event_to_run.getRunnable());
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public void
    clockChangeDetected(
            long current_time,
            long offset) {
        if (Math.abs(offset) >= 60 * 1000) {
            // fix up the timers
            synchronized (this) {
                Iterator<TimerEvent> it = events.iterator();
                List<TimerEvent> updated_events = new ArrayList<>(events.size());
                while (it.hasNext()) {
                    TimerEvent event = it.next();
                    // absolute events don't have their timings fiddled with
                    if (!event.isAbsolute()) {
                        long old_when = event.getWhen();
                        long new_when = old_when + offset;
                        TimerEventPerformer performer = event.getPerformer();
                        // sanity check for periodic events
                        if (performer instanceof TimerEventPeriodic) {
                            TimerEventPeriodic periodic_event = (TimerEventPeriodic) performer;
                            long freq = periodic_event.getFrequency();
                            if (new_when > current_time + freq + 5000) {
                                new_when = current_time + freq;
                            }
                        }
                        event.setWhen(new_when);
                    }
                    updated_events.add(event);
                }
                // resort - we have to use an alternative list of events as input because if we just throw the
                // treeset in the constructor optimises things under the assumption that the original set
                // was correctly sorted...
                events = new TreeSet<>(updated_events);
            }
        }
    }

    public void
    clockChangeCompleted(
            long current_time,
            long offset) {
        if (Math.abs(offset) >= 60 * 1000) {
            // there's a chance that between the change being notified and completed an event was scheduled
            // using an un-modified current time. Nothing can be done for non-periodic events but for periodic
            // ones we can santitize them to at least be within the periodic time period of the current time
            // important for when clock goes back but not forward obviously
            synchronized (this) {
                Iterator<TimerEvent> it = events.iterator();
                boolean updated = false;
                while (it.hasNext()) {
                    TimerEvent event = it.next();
                    // absolute events don't have their timings fiddled with
                    if (!event.isAbsolute()) {
                        TimerEventPerformer performer = event.getPerformer();
                        // sanity check for periodic events
                        if (performer instanceof TimerEventPeriodic) {
                            TimerEventPeriodic periodic_event = (TimerEventPeriodic) performer;
                            long freq = periodic_event.getFrequency();
                            long old_when = event.getWhen();
                            if (old_when > current_time + freq + 5000) {
                                long adjusted_when = current_time + freq;
                                //Debug.outNoStack( periodic_event.getName() + ": clock change sanity check. Reduced schedule time from " + old_when + " to " +  adjusted_when );
                                event.setWhen(adjusted_when);
                                updated = true;
                            }
                        }
                    }
                }
                if (updated) {
                    events = new TreeSet<>(new ArrayList<>(events));
                }
                // must have this notify here as the scheduling code uses the current time to calculate
                // how long to sleep for and this needs to be guaranteed to be using the correct (new) time
                notify();
            }
        }
    }

    synchronized void
    addEvent(
            String name,
            long when,
            TimerEventPerformer performer) {
        addEvent(name, SystemTime.getCurrentTime(), when, performer);
    }

    synchronized TimerEvent
    addEvent(
            String name,
            long when,
            boolean absolute,
            TimerEventPerformer performer) {
        return (addEvent(name, SystemTime.getCurrentTime(), when, absolute, performer));
    }

    synchronized TimerEvent
    addEvent(
            long creation_time,
            long when,
            boolean absolute,
            TimerEventPerformer performer) {
        return (addEvent(null, creation_time, when, absolute, performer));
    }

    private synchronized void
    addEvent(
            String name,
            long creation_time,
            long when,
            TimerEventPerformer performer) {
        addEvent(name, creation_time, when, false, performer);
    }

    private synchronized TimerEvent
    addEvent(
            String name,
            @SuppressWarnings("unused") long creation_time,
            long when,
            boolean absolute,
            TimerEventPerformer performer) {
        TimerEvent event = new TimerEvent(unique_id_next++, when, absolute, performer);
        if (name != null) {
            event.setName(name);
        }
        events.add(event);
        if (current_when == Integer.MAX_VALUE || when < current_when) {
            notify();
        }
        return (event);
    }

    synchronized void
    addPeriodicEvent(
            String name,
            long frequency,
            TimerEventPerformer performer) {
        addPeriodicEvent(name, frequency, false, performer);
    }

    private synchronized void
    addPeriodicEvent(
            String name,
            long frequency,
            boolean absolute,
            TimerEventPerformer performer) {
        TimerEventPeriodic periodic_performer = new TimerEventPeriodic(this, frequency, absolute, performer);
        if (name != null) {
            periodic_performer.setName(name);
        }
    }
}
