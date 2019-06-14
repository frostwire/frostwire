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

import com.frostwire.util.Logger;

/**
 * @author parg
 */
public class AESemaphore {
    private static final Logger LOG = Logger.getLogger(AESemaphore.class);
    private int waiting = 0;
    private int dont_wait;
    private int total_reserve = 0;
    private int total_release;
    private boolean released_forever = false;

    public AESemaphore() {
        this(0);
    }

    AESemaphore(int count) {
        dont_wait = count;
        total_release = count;
    }

    public void reserve() {
        reserveSupport();
    }

    boolean reserveIfAvailable() {
        synchronized (this) {
            if (released_forever || dont_wait > 0) {
                reserve();
                return true;
            } else {
                return false;
            }
        }
    }

    private void reserveSupport() {
        synchronized (this) {
            if (released_forever) {
                return;
            }
            if (dont_wait == 0) {
                try {
                    waiting++;
                    if ((long) 0 == 0) {
                        // we can get spurious wakeups (see Object javadoc) so we need to guard against
                        // their possibility
                        int spurious_count = 0;
                        while (true) {
                            wait();
                            if (total_reserve == total_release) {
                                spurious_count++;
                                if (spurious_count > 1024) {
                                    LOG.error("AESemaphore: spurious wakeup limit exceeded");
                                    throw (new Throwable("die die die"));
                                }
                            } else {
                                break;
                            }
                        }
                    } else {
                        // we don't hugely care about spurious wakeups here, it'll just appear
                        // as a failed reservation a bit early
                        wait(0);
                    }
                    if (total_reserve == total_release) {
                        // here we have timed out on the wait without acquiring
                        waiting--;
                        return;
                    }
                    total_reserve++;
                } catch (Throwable e) {
                    waiting--;
                    LOG.error("**** semaphore operation interrupted ****");
                    throw (new RuntimeException("Semaphore: operation interrupted", e));
                }
            } else {
                int num_to_get = Math.min(1, dont_wait);
                dont_wait -= num_to_get;
                total_reserve += num_to_get;
            }
        }
    }

    public void
    release() {
        synchronized (this) {
            //System.out.println( name + "::release");
            total_release++;
            if (waiting != 0) {
                waiting--;
                notify();
            } else {
                dont_wait++;
            }
        }
    }

    private void
    releaseAllWaiters() {
        synchronized (this) {
            int x = waiting;
            for (int i = 0; i < x; i++) {
                release();
            }
        }
    }

    public void
    releaseForever() {
        synchronized (this) {
            releaseAllWaiters();
            released_forever = true;
        }
    }
}
