/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 
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

package com.frostwire.logging;

import java.util.logging.Level;

import static java.util.logging.Level.INFO;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Logger {

    private final java.util.logging.Logger jul;
    private final String name;

    Logger(java.util.logging.Logger jul) {
        this.jul = jul;
        this.name = jul.getName();
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(java.util.logging.Logger.getLogger(clazz.getName()));
    }

    public void info(String msg) {
        jul.logp(INFO, name, "", msg);
    }

    public void info(String msg, Throwable e) {
        jul.logp(Level.INFO, name, "", msg, e);
    }

    public void warn(String msg) {
        jul.logp(INFO, name, "", msg);
    }

    public void warn(String msg, Throwable e) {
        jul.logp(Level.INFO, name, "", msg, e);
    }

    public void error(String msg) {
        jul.logp(INFO, name, "", msg);
    }

    public void error(String msg, Throwable e) {
        jul.logp(Level.INFO, name, "", msg, e);
    }

    public void debug(String msg) {
        jul.logp(INFO, name, "", msg);
    }

    public void debug(String msg, Throwable e) {
        jul.logp(Level.INFO, name, "", msg, e);
    }
}
