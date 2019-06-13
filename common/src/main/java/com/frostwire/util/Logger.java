/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.util;

import java.util.logging.Level;

import static java.util.logging.Level.INFO;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Logger {
    private final java.util.logging.Logger jul;
    private final String name;

    private Logger(java.util.logging.Logger jul) {
        this.jul = jul;
        this.name = jul.getName();
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
            caller = " - Called from <" + stackElement.getFileName() + "::" + stackElement.getMethodName() + ":" + stackElement.getLineNumber() + " on thread:" + currentThread.getName() + "(tid=" + currentThread.getId() + ")>";
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
        jul.logp(INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg));
    }

    public void info(String msg) {
        info(msg, false);
    }

    public void info(String msg, Throwable e, boolean showCallingMethodInfo) {
        jul.logp(Level.INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg), e);
    }

    public void info(String msg, Throwable e) {
        info(msg, e, false);
    }

    public void warn(String msg, boolean showCallingMethodInfo) {
        jul.logp(INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg));
    }

    public void warn(String msg) {
        warn(msg, false);
    }

    public void warn(String msg, Throwable e, boolean showCallingMethodInfo) {
        jul.logp(Level.INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg), e);
    }

    public void warn(String msg, Throwable e) {
        warn(msg, e, false);
    }

    public void error(String msg, boolean showCallingMethodInfo) {
        jul.logp(INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg));
    }

    public void error(String msg) {
        error(msg, false);
    }

    public void error(String msg, Throwable e, boolean showCallingMethodInfo) {
        jul.logp(Level.INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg), e);
    }

    public void error(String msg, Throwable e) {
        error(msg, e, false);
    }

    public void debug(String msg, boolean showCallingMethodInfo) {
        jul.logp(INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg));
    }

    public void debug(String msg) {
        debug(msg, false);
    }

    public void debug(String msg, Throwable e, boolean showCallingMethodInfo) {
        jul.logp(Level.INFO, name, "", ((showCallingMethodInfo) ? appendCallingMethodInfo(msg) : msg), e);
    }

    public void debug(String msg, Throwable e) {
        debug(msg, e, false);
    }
}
