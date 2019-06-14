/*
 * Created on Apr 16, 2004 Created by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package org.gudy.azureus2.core3.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class to retrieve current system time, and catch clock backward time
 * changes.
 */
public class SystemTime {
    static final long TIME_GRANULARITY_MILLIS = 25;    //internal update time ms
    private static final int STEPS_PER_SECOND = (int) (1000 / TIME_GRANULARITY_MILLIS);
    // can't do that without some safeguarding code.
    // monotime does guarantee that time neither goes backwards nor performs leaps into the future.
    // the HPC doesn't jump backward but can jump forward in time
    private static final boolean SOD_IT_LETS_USE_HPC = false;//	= Constants.isCVSVersion();
    private static final List<TickConsumer> systemTimeConsumers = new ArrayList<>();
    private static final List<TickConsumer> monotoneTimeConsumers = new ArrayList<>();
    private static final SystemTimeProvider instance = new SteppedProvider();
    private static volatile List<ChangeListener> clock_change_list = new ArrayList<>();

    /**
     * Note that this can this time can jump into the future or past due to
     * clock adjustments use getMonotonousTime() if you need steady increases
     *
     * @return current system time in millisecond since epoch
     */
    static long getCurrentTime() {
        return (instance.getTime());
    }

    /**
     * Time that is guaranteed to grow monotonously and also ignores larger
     * jumps into the future which might be caused by adjusting the system clock<br>
     * <br>
     *
     * <b>Do not mix times retrieved by this method with normal time!</b>
     *
     * @return the amount of real time passed since the program start in
     * milliseconds
     */
    public static long getMonotonousTime() {
        return instance.getMonoTime();
    }

    /**
     * Like getMonotonousTime but only updated at TIME_GRANULARITY_MILLIS intervals (not interpolated)
     * As such it is likely to be cheaper to obtain
     */
    static long getSteppedMonotonousTime() {
        return instance.getSteppedMonoTime();
    }

    public static long getOffsetTime(long offsetMS) {
        return instance.getTime() + offsetMS;
    }

    static void registerClockChangeListener(ChangeListener c) {
        synchronized (instance) {
            ArrayList<ChangeListener> new_list = new ArrayList<>(clock_change_list);
            new_list.add(c);
            clock_change_list = new_list;
        }
    }

    private static long
    getHighPrecisionCounter() {
        return (System.nanoTime());
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1; i++) {
            //final int f_i = i;
            new Thread(() -> {
                /*
                 * Average access_average = Average.getInstance( 1000, 10 );
                 *
                 * long last = SystemTime.getCurrentTime();
                 *
                 * int count = 0;
                 *
                 * while( true ){
                 *
                 * long now = SystemTime.getCurrentTime();
                 *
                 * long diff = now - last;
                 *
                 * System.out.println( "diff=" + diff );
                 *
                 * last = now;
                 *
                 * access_average.addValue( diff );
                 *
                 * count++;
                 *
                 * if ( count == 33 ){
                 *
                 * System.out.println( "AVERAGE " + f_i + " = " +
                 * access_average.getAverage());
                 *
                 * count = 0; }
                 *
                 * try{ Thread.sleep( 3 );
                 *
                 * }catch( Throwable e ){ } }
                 */
                long cstart = SystemTime.getCurrentTime();
                long mstart = SystemTime.getMonotonousTime();
                System.out.println("alter system clock to see differences between monotonous and current time");
                long cLastRound = cstart;
                long mLastRound = mstart;
                while (true) {
                    long mnow = SystemTime.getMonotonousTime();
                    long cnow = SystemTime.getCurrentTime();
                    System.out.println("current: " + (cnow - cstart) + " monotonous:" + (mnow - mstart) + " delta current:" + (cnow - cLastRound) + " delta monotonous:" + (mnow - mLastRound));
                    cLastRound = cnow;
                    mLastRound = mnow;
                    try {
                        Thread.sleep(15);
                    } catch (Throwable ignored) {
                    }
                }
            }).start();
        }
    }

    interface SystemTimeProvider {
        long getTime();

        long getMonoTime();

        long
        getSteppedMonoTime();
    }

    interface TickConsumer {
        void consume(long current_time);
    }

    public interface ChangeListener {
        /**
         * Called before the change becomes visible to getCurrentTime callers
         */
        void clockChangeDetected(long current_time, long change_millis);

        /**
         * Called after the change is visible to getCurrentTime callers
         */
        void clockChangeCompleted(long current_time, long change_millis);
    }

    private static class SteppedProvider implements SystemTimeProvider {
        private static final long HPC_START = getHighPrecisionCounter() / 1000000L;
        private final AtomicLong last_approximate_time = new AtomicLong();
        // System.out.println("SystemTime: using stepped time provider");
        private volatile long stepped_time = 0;
        private final Object stepped_time_lock = new Object();
        private volatile long currentTimeOffset = System.currentTimeMillis();
        //private volatile long		last_approximate_time;
        private volatile int access_count;
        private final Object access_count_lock = new Object();
        private volatile int slice_access_count;
        private final Object slice_access_count_lock = new Object();
        private volatile int access_average_per_slice;
        private volatile int drift_adjusted_granularity;
        private volatile long stepped_mono_time;

        private SteppedProvider() {
            // these averages rely on monotone time, thus won't be affected by system time changes
            /*
             * keep the monotone time in sync with the raw system
             * time, for this we need to know the offset of the
             * current time to the system time
             */
            /*
             * unless the system time jumps, then we just guess the
             * time that has passed and adjust the update, so that
             * the next round can be in sync with the system time
             * again
             */
            /*
             * jump occured, update monotone time offset, but
             * not the current time one, that only happens every
             * second
             */
            // time is good, keep it
            //Debug.outNoStack("Clock change of " + change + " ms detected, raw=" + rawTime );
            // averaging magic to estimate the amount of time that passes between each getTime invocation
            //System.out.println( "access count = " + access_count + ", average = " + access_average.getAverage() + ", per slice = " + access_average_per_slice + ", drift = " + drift +", average = " + drift_average.getAverage() + ", dag =" + drift_adjusted_granularity );
            //Debug.outNoStack("Clock change of " + change + " ms completed, curr=" + adjustedTime );
            // copy reference since we use unsynced COW semantics
            /*
             * notify consumers with the external offset, internal
             * offset is only meant for updates
             */
            Thread updater = new Thread("SystemTime") {
                public void run() {
                    long adjustedTimeOffset = currentTimeOffset;
                    // these averages rely on monotone time, thus won't be affected by system time changes
                    final Average access_average = Average.getInstance(1000, 10);
                    final Average drift_average = Average.getInstance(1000, 10);
                    long lastOffset = adjustedTimeOffset;
                    long lastSecond = -1000;
                    int tick_count = 0;
                    //noinspection InfiniteLoopStatement
                    while (true) {
                        final long rawTime = System.currentTimeMillis();
                        /*
                         * keep the monotone time in sync with the raw system
                         * time, for this we need to know the offset of the
                         * current time to the system time
                         */
                        long newMonotoneTime = rawTime - adjustedTimeOffset;
                        long delta = newMonotoneTime - stepped_time;
                        /*
                         * unless the system time jumps, then we just guess the
                         * time that has passed and adjust the update, so that
                         * the next round can be in sync with the system time
                         * again
                         */
                        if (delta < 0 || delta > 1000) {
                            /*
                             * jump occured, update monotone time offset, but
                             * not the current time one, that only happens every
                             * second
                             */
                            synchronized (stepped_time_lock) {
                                stepped_time += TIME_GRANULARITY_MILLIS;
                            }
                            adjustedTimeOffset = rawTime - stepped_time;
                        } else { // time is good, keep it
                            synchronized (stepped_time_lock) {
                                stepped_time = newMonotoneTime;
                            }
                        }
                        tick_count++;
                        long change;
                        if (tick_count == STEPS_PER_SECOND) {
                            change = adjustedTimeOffset - lastOffset;
                            if (change != 0) {
                                //Debug.outNoStack("Clock change of " + change + " ms detected, raw=" + rawTime );
                                for (ChangeListener changeListener : clock_change_list) {
                                    try {
                                        changeListener.clockChangeDetected(rawTime, change);
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                    }
                                }
                                lastOffset = adjustedTimeOffset;
                                currentTimeOffset = adjustedTimeOffset;
                            }
                            // averaging magic to estimate the amount of time that passes between each getTime invocation
                            long drift = stepped_time - lastSecond - 1000;
                            lastSecond = stepped_time;
                            drift_average.addValue(drift);
                            drift_adjusted_granularity = (int) (TIME_GRANULARITY_MILLIS + (drift_average.getAverage() / STEPS_PER_SECOND));
                            access_average.addValue(access_count);
                            access_average_per_slice = (int) (access_average.getAverage() / STEPS_PER_SECOND);
                            //System.out.println( "access count = " + access_count + ", average = " + access_average.getAverage() + ", per slice = " + access_average_per_slice + ", drift = " + drift +", average = " + drift_average.getAverage() + ", dag =" + drift_adjusted_granularity );
                            access_count = 0;
                            tick_count = 0;
                        } else {
                            change = 0;
                        }
                        slice_access_count = 0;
                        stepped_mono_time = stepped_time;
                        long adjustedTime = stepped_time + currentTimeOffset;
                        if (change != 0) {
                            //Debug.outNoStack("Clock change of " + change + " ms completed, curr=" + adjustedTime );
                            for (ChangeListener changeListener : clock_change_list) {
                                try {
                                    changeListener.clockChangeCompleted(adjustedTime, change);
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        // copy reference since we use unsynced COW semantics
                        List<TickConsumer> consumersRef = monotoneTimeConsumers;
                        for (TickConsumer cons : consumersRef) {
                            try {
                                cons.consume(stepped_time);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }

                        /*
                         * notify consumers with the external offset, internal
                         * offset is only meant for updates
                         */
                        consumersRef = systemTimeConsumers;
                        for (TickConsumer cons : consumersRef) {
                            try {
                                cons.consume(adjustedTime);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            Thread.sleep(TIME_GRANULARITY_MILLIS);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            updater.setDaemon(true);
            // we don't want this thread to lag much as it'll stuff up the upload/download rate mechanisms (for example)
            updater.setPriority(Thread.MAX_PRIORITY);
            updater.start();
        }

        public long getTime() {
            return getMonoTime() + currentTimeOffset;
        }

        public long getMonoTime() {
            if (SOD_IT_LETS_USE_HPC) {
                return ((getHighPrecisionCounter() / 1000000) - HPC_START);
            } else {
                long adjusted_time;
                long averageSliceStep = access_average_per_slice;
                if (averageSliceStep > 0) {
                    long sliceStep = (drift_adjusted_granularity * slice_access_count) / averageSliceStep;
                    if (sliceStep >= drift_adjusted_granularity) {
                        sliceStep = drift_adjusted_granularity - 1;
                    }
                    adjusted_time = sliceStep + stepped_time;
                } else
                    adjusted_time = stepped_time;
                synchronized (access_count_lock) {
                    access_count++;
                }
                synchronized (slice_access_count_lock) {
                    slice_access_count++;
                }
                // make sure we don't go backwards and our reference value for going backwards doesn't go backwards either
                long approxBuffered = last_approximate_time.get();
                if (adjusted_time < approxBuffered)
                    adjusted_time = approxBuffered;
                else
                    last_approximate_time.compareAndSet(approxBuffered, adjusted_time);
                return adjusted_time;
            }
        }

        public long getSteppedMonoTime() {
            if (SOD_IT_LETS_USE_HPC) {
                return (getHighPrecisionCounter() / 1000000);
            } else {
                return (stepped_mono_time);
            }
        }
    }
}
