//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Cookie {

    private String path;
    private String host;
    private String value;
    private String key;
    private String domain;

    private long   hostTime     = -1;
    private long   creationTime = System.currentTimeMillis();
    private long   expireTime   = -1;

    public Cookie() {
        this.host = "";
        this.key = "";
        this.value = "";
    }

    public Cookie(final String host, final String key, final String value) {
        this.host = host;
        this.key = key;
        this.value = value;
    }

    /* compares host and key ignoring case */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) { return true; }
        if (obj == null) { return false; }
        if (this.getClass() != obj.getClass()) { return false; }
        final Cookie other = (Cookie) obj;
        if (this.host == null) {
            if (other.host != null) { return false; }
        } else if (!this.host.equalsIgnoreCase(other.host)) { return false; }
        if (this.key == null) {
            if (other.key != null) { return false; }
        } else if (!this.key.equalsIgnoreCase(other.key)) { return false; }
        return true;
    }

    public long getCreationTime() {
        return this.creationTime;
    }

    public String getDomain() {
        return this.domain;
    }

    public long getExpireDate() {
        return this.expireTime;
    }

    public String getHost() {
        return this.host;
    }

    public long getHostTime() {
        return this.hostTime;
    }

    public String getKey() {
        return this.key;
    }

    public String getPath() {
        return this.path;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.host == null ? 0 : this.host.toUpperCase().hashCode());
        result = prime * result + (this.key == null ? 0 : this.key.toUpperCase().hashCode());
        return result;
    }

    public boolean isExpired() {
        if (this.expireTime == -1) {
            // System.out.println("isexpired: no expireDate found! " + this.host
            // + " " + this.key);
            return false;
        }
        if (this.hostTime == -1) {
            System.out.println("Cookie: no HostTime found! ExpireStatus cannot be checked " + this.host + " " + this.key);
            return false;
        } else {
            final long timediff = this.creationTime - this.hostTime;
            final long check = System.currentTimeMillis() - timediff;

            // System.out.println(this.host + " " + this.key + " " +
            // this.creationTime + " " + this.hostTime + " " + this.expireTime +
            // " " + check);
            // if (check > this.expireTime) {
            // // System.out.println("Expired: " + this.host + " " + this.key);
            // return true;
            // } else
            // return false;
            final boolean expired = check > this.expireTime;
            return expired;
        }
    }

    public void setCreationTime(final long time) {
        this.creationTime = time;
    }

    public void setDomain(final String domain) {
        this.domain = domain;
    }

    public void setExpireDate(final long time) {
        this.expireTime = time;
    }

    public void setExpires(final String expires) {
        if (expires == null) {
            this.expireTime = -1;
            // System.out.println("setExpire: Cookie: no expireDate found! " +
            // this.host + " " + this.key);
            return;
        }
        final Date expireDate = parseDateString(expires);
        if (expireDate != null) {
            this.expireTime = expireDate.getTime();
            return;
        }
        this.expireTime = -1;
        System.out.println("Cookie: no Format for " + expires + " found!");
        return;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public void setHostTime(final long time) {
        this.hostTime = time;
    }

    public void setHostTime(final String date) {
        if (date == null) {
            this.hostTime = -1;
            // System.out.println("Cookie: no HostTime found! " + this.host +
            // " " + this.key);
            return;
        }
        final Date responseDate = parseDateString(date);
        if (responseDate != null) {
            this.hostTime = responseDate.getTime();
            return;
        }
        this.hostTime = -1;
        System.out.println("Cookie: no Format for " + date + " found!");
        return;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.key + "=" + this.value + " @" + this.host;
    }

    // /* compares host and key */
    // public boolean equals(final Cookie cookie2) {
    // if (cookie2 == this) return true;
    // if (!cookie2.getHost().equalsIgnoreCase(this.getHost())) return false;
    // if (!cookie2.getKey().equalsIgnoreCase(this.getKey())) return false;
    // return true;
    // }

    public void update(final Cookie cookie2) {
        this.setCreationTime(cookie2.creationTime);
        this.setExpireDate(cookie2.expireTime);
        this.setValue(cookie2.value);
        this.setHostTime(cookie2.hostTime);
    }

    private static final java.util.List<SimpleDateFormat> dateformats  = new ArrayList<SimpleDateFormat>();
    static {
        try {
            SimpleDateFormat sdf;
            dateformats.add(sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy z", Locale.UK));
            sdf.setLenient(false);
            dateformats.add(sdf = new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss z", Locale.UK));
            sdf.setLenient(false);
            dateformats.add(sdf = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.UK));
            sdf.setLenient(false);
            dateformats.add(sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.UK));
            sdf.setLenient(false);
            dateformats.add(sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.UK));
            sdf.setLenient(false);
            dateformats.add(sdf = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.UK));
            sdf.setLenient(false);
            dateformats.add(sdf = new SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss z", Locale.UK));
            sdf.setLenient(false);
            dateformats.add(sdf = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.UK));
            sdf.setLenient(true);
        } catch (final Throwable e) {
            e.printStackTrace();
        }
    }

    public static Date parseDateString(final String date) {
        if (date == null) { return null; }
        Date expireDate = null;
        for (final SimpleDateFormat format : dateformats) {
            try {
                expireDate = format.parse(date);
                break;
            } catch (final Throwable e2) {
            }
        }
        if (expireDate == null) { return null; }
        return expireDate;
    }
}
