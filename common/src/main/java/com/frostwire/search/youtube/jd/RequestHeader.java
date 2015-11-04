//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package com.frostwire.search.youtube.jd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class RequestHeader {

    /**
     * For more header fields see
     * 
     * @link(http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14).
     */

    // members
    private final List<String> keys;
    private final List<String> values;
    private boolean dominant = false;

    public RequestHeader() {
        this.keys = new ArrayList<String>();
        this.values = new ArrayList<String>();
    }

    public void clear() {
        this.keys.clear();
        this.values.clear();
    }

    @Override
    public RequestHeader clone() {
        final RequestHeader newObj = new RequestHeader();
        newObj.keys.addAll(this.keys);
        newObj.values.addAll(this.values);
        return newObj;
    }

    public boolean contains(final String string) {
        return this.keys.contains(string);
    }

    public String get(final String key) {
        final int index = this.keys.indexOf(key);
        return index >= 0 ? this.values.get(index) : null;
    }

    public String getKey(final int index) {
        return this.keys.get(index);
    }

    public String getValue(final int index) {
        return this.values.get(index);
    }

    public boolean isDominant() {
        return this.dominant;
    }

    public void put(final String key, final String value) {
        final int keysSize = this.keys.size();
        final String trim = key.trim();
        for (int i = 0; i < keysSize; i++) {
            if (this.keys.get(i).equalsIgnoreCase(trim)) {
                this.keys.set(i, key);
                this.values.set(i, value);
                return;
            }
        }
        this.keys.add(key);
        this.values.add(value);
    }

    public void putAll(final Map<String, String> properties) {
        for (final Entry<String, String> entry : properties.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if (value == null) {
                this.remove(key);
            } else {
                this.put(key, value);
            }
        }
    }

    public void putAll(final RequestHeader headers) {
        final int size = headers.size();
        for (int i = 0; i < size; i++) {
            final String key = headers.getKey(i);
            final String value = headers.getValue(i);
            if (value == null) {
                this.remove(key);
            } else {
                this.put(key, value);
            }
        }
    }

    public String remove(final String key) {
        final int index = this.keys.indexOf(key);

        if (index >= 0) {
            this.keys.remove(index);
            return this.values.remove(index);
        }

        return null;
    }

    public int size() {
        return this.keys.size();
    }
}
