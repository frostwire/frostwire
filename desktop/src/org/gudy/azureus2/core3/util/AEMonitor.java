/*
 * Created on 18-Sep-2004
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package org.gudy.azureus2.core3.util;

class
AEMonitor
        extends AEMonSem {
    private int dont_wait = 1;
    private int nests = 0;
    private int total_reserve = 0;
    private int total_release = 1;

    Thread owner;
    Thread last_waiter;

    AEMonitor(
            String _name) {
        super(_name, true);
    }

    public void
    enter() {
        if (DEBUG) {

            debugEntry();
        }

        Thread current_thread = Thread.currentThread();

        synchronized (this) {

            entry_count++;

            if (owner == current_thread) {

                nests++;

            } else {

                if (dont_wait == 0) {

                    try {
                        waiting++;

                        last_waiter = current_thread;

                        // we can get spurious wakeups (see Object javadoc) so we need to guard against
                        // their possibility

                        int spurious_count = 0;

                        while (true) {

                            wait();

                            if (total_reserve == total_release) {

                                spurious_count++;

                                if (spurious_count > 1024) {

                                    waiting--;

                                    Debug.out("AEMonitor: spurious wakeup limit exceeded");

                                    throw (new Throwable("die die die"));

                                }
                            } else {

                                break;
                            }
                        }

                        total_reserve++;

                    } catch (Throwable e) {

                        // we know here that someone's got a finally clause to do the
                        // balanced 'exit'. hence we should make it look as if we own it...

                        waiting--;

                        owner = current_thread;

                        Debug.out("**** monitor interrupted ****");

                        throw (new RuntimeException("AEMonitor:interrupted"));

                    } finally {

                        last_waiter = null;
                    }
                } else {

                    total_reserve++;

                    dont_wait--;
                }

                owner = current_thread;
            }
        }
    }


    public void
    exit() {
        try {
            synchronized (this) {

                if (nests > 0) {

                    if (DEBUG) {

                        if (owner != Thread.currentThread()) {

                            Debug.out("nested exit but current thread not owner");
                        }
                    }

                    nests--;

                } else {

                    owner = null;

                    total_release++;

                    if (waiting != 0) {

                        waiting--;

                        notify();

                    } else {

                        dont_wait++;

                        if (dont_wait > 1) {

                            Debug.out("**** AEMonitor '" + name + "': multiple exit detected");
                        }
                    }
                }
            }

        } finally {

            if (DEBUG) {

                debugExit();
            }
        }
    }

}