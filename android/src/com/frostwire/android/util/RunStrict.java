/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.android.util;

import android.os.Build;
import android.os.StrictMode;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Logger;

import java.util.Locale;

public interface RunStrict<R> {

    Logger LOG = Logger.getLogger(RunStrict.class);

    R run();

    /**
     * Enable the most strict form of {@link StrictMode} possible,
     * with log and death as penalty. When {@code enable} is {@code false}, the
     * default more relaxed (LAX) policy is used.
     * <p>
     * This method only perform an actual action if the application is
     * in debug mode.
     *
     * @param enable {@code true} activate the most strict policy
     */
    static void setStrictPolicy(boolean enable) {
        LOG.info("RunStrict.setStrictPolicy(" + enable + ") Debug.isEnabled()=" + Debug.isEnabled());
        if (!Debug.isEnabled()) {
            LOG.info("StrictMode is disabled, this is a DEBUG build");
            return; // no debug mode, do nothing
        }

        // by default, the LAX policy
        StrictMode.ThreadPolicy threadPolicy = StrictMode.ThreadPolicy.LAX;
        StrictMode.VmPolicy vmPolicy = StrictMode.VmPolicy.LAX;

        if (enable) {
            threadPolicy = new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build();
            vmPolicy = new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .setClassInstanceLimit(Engine.class, 1)
                    .build();
        }

        StrictMode.setThreadPolicy(threadPolicy);
        StrictMode.setVmPolicy(vmPolicy);
    }

    /**
     * Runs the runnable code under strict policy but then sets relaxed policies back.
     * On the current thread where it's invoked.
     *
     * @param r the runnable to execute r.run()
     */
    static void runStrict(Runnable r) {
        try {
            setStrictPolicy(true);
            r.run();
        } finally {
            setStrictPolicy(false);
        }
    }

    /**
     * Runs the lambda code under strict policy.
     *
     * @param r   the lambda to run
     * @param <R> the type of return
     * @return the return value
     */
    static <R> R runStrict(RunStrict<R> r) {
        try {
            setStrictPolicy(true);
            return r.run();
        } finally {
            setStrictPolicy(false);
        }
    }

    /** @noinspection unused*/
    static void enableStrictModePolicies(boolean enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (enable) {
                LOG.info(String.format(Locale.US, "MainApplication::enableStrictModePolicies SDK VERSION: {%d}", Build.VERSION.SDK_INT));
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                        .detectAll()
                        .penaltyLog() //.penaltyDeath()
                        .permitUnbufferedIo() // Temporarily allow unbuffered IO
                        .build());
            } else {
                LOG.info(String.format(Locale.US,"MainApplication::disableStrictModePolicies SDK VERSION: {%d}", Build.VERSION.SDK_INT));
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                        .permitAll() // Allow everything
                        .build());
            }
        }
    }


    /** @noinspection unused*/
    static void disableStrictModePolicyForUnbufferedIO() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LOG.info(String.format(Locale.US, "MainApplication::disableStrictModePolicyForUnbufferedIO SDK VERSION: {%d}", Build.VERSION.SDK_INT));
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .permitUnbufferedIo() // Temporarily allow unbuffered IO
                    .build());
        }
    }
}
