/*
 * Created on 15-Mar-2006
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

import java.lang.ref.WeakReference;
import java.util.*;

public class
CopyOnWriteList<T>
        implements Iterable<T> {
    private static final boolean LOG_STATS = false;

    private List list = Collections.EMPTY_LIST;

    private final boolean use_linked_list;

    private boolean visible = false;

    private int initialCapacity;

    private static final CopyOnWriteList stats = new CopyOnWriteList(10);

    static {
        if (LOG_STATS) {
            AEDiagnostics.addEvidenceGenerator(writer -> {
                writer.println("COWList Info");
                writer.indent();
                try {
                    long count = 0;
                    long size = 0;
                    for (Object stat : stats) {
                        WeakReference wf = (WeakReference) stat;
                        CopyOnWriteList cowList = (CopyOnWriteList) wf.get();
                        if (cowList != null) {
                            count++;
                            size += cowList.size();
                        }
                    }
                    writer.println(count + " lists with " + size + " total entries");
                    if (count > 0) {
                        writer.println((size / count) + " avg size");
                    }
                } catch (Throwable ignored) {
                } finally {
                    writer.exdent();
                }
            });
        }
    }

    /**
     *
     */
    private CopyOnWriteList(int initialCapacity) {
        this.initialCapacity = initialCapacity;
        use_linked_list = false;
        if (LOG_STATS) {
            stats.add(new WeakReference(this));
        }
    }

    CopyOnWriteList(boolean _use_linked_list) {
        this.initialCapacity = 1;
        use_linked_list = _use_linked_list;
        if (LOG_STATS) {
            stats.add(new WeakReference(this));
        }
    }

    public void
    add(
            T obj) {
        synchronized (this) {

            if (visible) {

                List<T> new_list = use_linked_list ? new LinkedList<>(list) : new ArrayList<>(list);

                //mutated();

                new_list.add(obj);

                list = new_list;

                visible = false;

            } else {
                if (list == Collections.EMPTY_LIST) {
                    list = use_linked_list ? new LinkedList<>() : new ArrayList<>(initialCapacity);
                }

                list.add(obj);
            }
        }
    }


    public boolean
    remove(
            T obj) {
        synchronized (this) {

            if (visible) {

                List<T> new_list = use_linked_list ? new LinkedList<>(list) : new ArrayList<>(list);

                //mutated();

                boolean result = new_list.remove(obj);

                list = new_list;

                visible = false;

                return (result);

            } else {

                return (list.remove(obj));
            }
        }
    }


    public Iterator<T>
    iterator() {
        synchronized (this) {

            visible = true;

            return (new CopyOnWriteListIterator(list.iterator()));
        }
    }

    public int
    size() {
        synchronized (this) {

            return (list.size());
        }
    }

    private class
    CopyOnWriteListIterator
            implements Iterator<T> {
        private final Iterator<T> it;
        private T last;

        CopyOnWriteListIterator(
                Iterator<T> _it) {
            it = _it;
        }

        public boolean
        hasNext() {
            return (it.hasNext());
        }

        public T
        next() {
            last = it.next();

            return (last);
        }

        public void
        remove() {
            // don't actually remove it from the iterator. can't go backwards with this iterator so this is
            // not a problem

            if (last == null) {

                throw (new IllegalStateException("next has not been called!"));
            }

            CopyOnWriteList.this.remove(last);
        }
    }
}
