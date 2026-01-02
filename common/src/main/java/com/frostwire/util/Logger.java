/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.util;

import java.util.logging.Level;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Logger {
    private final java.util.logging.Logger jul;
    private final String name;

    private static String contextPrefix;

    private static final Object lock = new Object();

    private Logger(java.util.logging.Logger jul) {
        this.jul = jul;
        this.name = jul.getName();
    }

    public static void setContextPrefix(String prefix) {
        synchronized (lock) {
            contextPrefix = prefix;
        }
    }

    public static void clearContextPrefix() {
        synchronized (lock) {
            contextPrefix = null;
        }
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(java.util.logging.Logger.getLogger(clazz.getSimpleName()));
    }

    private static String getCallingMethodInfo() {
        Thread currentThread = Thread.currentThread();
        StackTraceElement[] stackTrace = currentThread.getStackTrace();
        String caller = " - <Thread not scheduled yet>";
        if (stackTrace.length >= 5) {
            StackTraceElement stackElement = stackTrace[5];
            caller = " - Called from <" + stackElement.getFileName() + "::" + stackElement.getMethodName() + ":" + stackElement.getLineNumber() + " on thread:" + currentThread.getName() + ">";
        }
        if (stackTrace.length >= 6) {
            StackTraceElement stackElement = stackTrace[6];
            caller = "\n - invoked by  <" + stackElement.getFileName() + "::" + stackElement.getMethodName() + ":" + stackElement.getLineNumber() + " on thread:" + currentThread.getName() + ">";
        }
        return caller;
    }

    private static String appendCallingMethodInfo(String msg) {
        return msg + getCallingMethodInfo();
    }

    public String getName() {
        return name;
    }

    public void info(String msg, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(INFO)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(INFO, name, "", msg);
    }

    public void info(String msg) {
        info(msg, false);
    }

    public void info(String msg, Throwable e, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(INFO)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(Level.INFO, name, "", msg, e);
    }

    public void info(String msg, Throwable e) {
        info(msg, e, false);
    }

    public void warn(String msg, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(WARNING)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(WARNING, name, "", msg);
    }

    public void warn(String msg) {
        warn(msg, false);
    }

    public void warn(String msg, Throwable e, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(WARNING)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(WARNING, name, "", msg, e);
    }

    public void warn(String msg, Throwable e) {
        warn(msg, e, false);
    }

    public void error(String msg, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(Level.SEVERE)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(Level.SEVERE, name, "", msg);
    }

    public void error(String msg) {
        error(msg, false);
    }

    public void error(String msg, Throwable e, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(Level.SEVERE)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(Level.SEVERE, name, "", msg, e);
    }

    public void error(String msg, Throwable e) {
        error(msg, e, false);
    }

    public void debug(String msg, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(INFO)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(INFO, name, "", msg);
    }

    public void debug(String msg) {
        debug(msg, false);
    }

    public void debug(String msg, Throwable e, boolean showCallingMethodInfo) {
        if (!jul.isLoggable(INFO)) {
            return;
        }
        if (showCallingMethodInfo) {
            msg = appendCallingMethodInfo(msg);
        }
        if (contextPrefix != null) {
            msg = contextPrefix + msg;
        }
        jul.logp(Level.INFO, name, "", msg, e);
    }

    public void debug(String msg, Throwable e) {
        debug(msg, e, false);
    }
}
