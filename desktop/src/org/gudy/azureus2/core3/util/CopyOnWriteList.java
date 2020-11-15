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

import java.util.*;

class CopyOnWriteList<T> implements Iterable<T> {
    private final boolean use_linked_list;
    private List<T> list = Collections.EMPTY_LIST;
    private boolean visible = false;

    CopyOnWriteList(boolean _use_linked_list) {
        use_linked_list = _use_linked_list;
    }

    void
    remove(
            T obj) {
        synchronized (this) {
            if (visible) {
                list = use_linked_list ? new LinkedList<>(list) : new ArrayList<>(list);
                visible = false;
            } else {
                list.remove(obj);
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
            if (last == null) {
                throw (new IllegalStateException("next has not been called!"));
            }
            CopyOnWriteList.this.remove(last);
        }
    }
}
