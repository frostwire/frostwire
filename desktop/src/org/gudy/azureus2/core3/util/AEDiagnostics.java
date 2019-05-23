/*
 * Created on 22-Sep-2004
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

//import org.gudy.azureus2.core3.config.COConfigurationManager;
//import org.gudy.azureus2.core3.config.ParameterListener;
//import org.gudy.azureus2.platform.PlatformManager;
//import org.gudy.azureus2.platform.PlatformManagerCapabilities;
//import org.gudy.azureus2.platform.PlatformManagerFactory;


import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class
AEDiagnostics {
    // these can not be set true and have a usable AZ!
    private static final boolean ALWAYS_PASS_HASH_CHECKS = false;
    private static final boolean USE_DUMMY_FILE_DATA = false;
    private static final boolean CHECK_DUMMY_FILE_DATA = false;

    // these can safely be set true, things will work just slower
    static final boolean DEBUG_MONITOR_SEM_USAGE = false;

    private static final boolean TRACE_DIRECT_BYTE_BUFFERS = false;
    private static final boolean TRACE_DBB_POOL_USAGE = false;
    private static final boolean PRINT_DBB_POOL_USAGE = false;

    private static final boolean TRACE_TCP_TRANSPORT_STATS = false;


    static {
        if (ALWAYS_PASS_HASH_CHECKS) {
            System.out.println("**** Always passing hash checks ****");
        }
        if (USE_DUMMY_FILE_DATA) {
            System.out.println("**** Using dummy file data ****");
        }
        if (CHECK_DUMMY_FILE_DATA) {
            System.out.println("**** Checking dummy file data ****");
        }
        if (DEBUG_MONITOR_SEM_USAGE) {
            System.out.println("**** AEMonitor/AESemaphore debug on ****");
        }
        if (TRACE_DIRECT_BYTE_BUFFERS) {
            System.out.println("**** DirectByteBuffer tracing on ****");
        }
        if (TRACE_DBB_POOL_USAGE) {
            System.out.println("**** DirectByteBufferPool tracing on ****");
        }
        if (PRINT_DBB_POOL_USAGE) {
            System.out.println("**** DirectByteBufferPool printing on ****");
        }
        if (TRACE_TCP_TRANSPORT_STATS) {
            System.out.println("**** TCP_TRANSPORT_STATS tracing on ****");
        }

        int maxFileSize = 256 * 1024;
        try {
            String logSize = System.getProperty("diag.logsize", null);
            if (logSize != null) {
                if (logSize.toLowerCase().endsWith("m")) {
                    maxFileSize = Integer.parseInt(logSize.substring(0,
                            logSize.length() - 1)) * 1024 * 1024;
                } else {
                    maxFileSize = Integer.parseInt(logSize);
                }
            }
        } catch (Throwable ignored) {
        }
        MAX_FILE_SIZE = maxFileSize;
    }

    private static final int MAX_FILE_SIZE;    // get two of these per logger type

    private static final String CONFIG_KEY = "diagnostics.tidy_close";

    private static File debug_dir;

    private static boolean started_up;
    private static volatile boolean startup_complete;
    private static boolean enable_pending_writes;

    private static Map<String, AEDiagnosticsLogger> loggers = new HashMap<>();

    protected static boolean logging_enabled;
    protected static boolean loggers_enabled;

    private static List<AEDiagnosticsEvidenceGenerator> evidence_generators = new ArrayList<>();

    public static boolean
    isStartupComplete() {
        return (startup_complete);
    }

    public static synchronized AEDiagnosticsLogger
    getLogger(
            String name) {
        AEDiagnosticsLogger logger = loggers.get(name);

        if (logger == null) {

            logger = new AEDiagnosticsLogger(debug_dir, name, MAX_FILE_SIZE, !enable_pending_writes);

            loggers.put(name, logger);
        }

        return (logger);
    }

    public static void
    log(
            String logger_name,
            String str) {
        getLogger(logger_name).log(str);
    }

    public static boolean
    isDirty() {
        return false;
    }

    static void
    addEvidenceGenerator(
            AEDiagnosticsEvidenceGenerator gen) {
        synchronized (evidence_generators) {

            evidence_generators.add(gen);
        }
    }

}
