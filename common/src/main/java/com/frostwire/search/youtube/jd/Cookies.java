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

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.StringTokenizer;

public class Cookies {

    public static Cookies parseCookies(final String cookieString, final String host, final String serverTime) {
        final Cookies cookies = new Cookies();

        final String header = cookieString;

        String path = null;
        String expires = null;
        String domain = null;
        final LinkedHashMap<String, String> tmp = new LinkedHashMap<String, String>();
        /* Cookie individual elements */
        final StringTokenizer st = new StringTokenizer(header, ";");

        while (true) {
            String key = null;
            String value = null;
            String cookieelement = null;
            if (st.hasMoreTokens()) {
                cookieelement = st.nextToken().trim();
            } else {
                break;
            }
            /* Key and Value */
            final String st2[] = new Regex(cookieelement, "(.*?)=(.*)").getRow(0);
            if (st2 == null || st2.length == 0) {
                key = null;
            } else if (st2.length == 1) {
                key = st2[0].trim();
            } else if (st2.length == 2) {
                key = st2[0].trim();
                value = st2[1].trim();

            }

            if (key != null) {
                if (key.equalsIgnoreCase("path")) {
                    path = value;
                } else if (key.equalsIgnoreCase("expires")) {
                    expires = value;
                } else if (key.equalsIgnoreCase("domain")) {
                    domain = value;
                } else {
                    tmp.put(key, value);
                }
            } else {
                break;
            }
        }

        for (final Entry<String, String> next : tmp.entrySet()) {
            /*
             * no cookies are cookies without a value
             */
            if (next.getValue() != null) {
                final Cookie cookie = new Cookie();
                cookies.add(cookie);
                cookie.setHost(host);
                cookie.setPath(path);
                cookie.setDomain(domain);
                cookie.setExpires(expires);
                cookie.setValue(next.getValue());
                cookie.setKey(next.getKey());
                cookie.setHostTime(serverTime);
            }
        }

        return cookies;

    }

    private final LinkedList<Cookie> cookies = new LinkedList<Cookie>();

    public Cookies() {
    }

    public void add(final Cookie cookie) {
        synchronized (this.cookies) {
            for (final Cookie cookie2 : this.cookies) {
                if (cookie2.equals(cookie)) {
                    cookie2.update(cookie);
                    return;
                }
            }
            this.cookies.add(cookie);
        }
    }

    public void add(final Cookies newcookies) {
        synchronized (this.cookies) {
            for (final Cookie cookie : newcookies.getCookies()) {
                this.add(cookie);
            }
        }
    }

    public void clear() {
        synchronized (this.cookies) {
            this.cookies.clear();
        }
    }

    public Cookie get(final String key) {
        if (key == null) { return null; }
        synchronized (this.cookies) {
            for (final Cookie cookie : this.cookies) {
                if (cookie.getKey().equalsIgnoreCase(key)) { return cookie; }
            }
            return null;
        }
    }

    public LinkedList<Cookie> getCookies() {
        return this.cookies;
    }

    public boolean isEmpty() {
        return this.cookies.isEmpty();
    }

    public void remove(final Cookie cookie) {
        synchronized (this.cookies) {
            if (!this.cookies.remove(cookie)) {
                Cookie del = null;
                for (final Cookie cookie2 : this.cookies) {
                    if (cookie2.equals(cookie)) {
                        del = cookie2;
                        break;
                    }
                }
                if (del != null) {
                    this.cookies.remove(del);
                }
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder ret = new StringBuilder();
        for (final Cookie el : this.cookies) {
            ret.append(el.toString() + "\r\n");
        }
        return ret.toString();
    }
}
