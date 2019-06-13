/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.bugs;

import com.limegroup.gnutella.LimeWireCore;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.settings.LimeProps;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.Setting;
import org.limewire.setting.SettingsFactory;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;
import org.limewire.util.VersionUtils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * This class encapsulates all of the data for an individual client machine
 * for an individual bug report.<p>
 * <p>
 * This class collects all of the data for the local machine and provides
 * access to that data in url-encoded form.
 */
public final class LocalClientInfo extends LocalAbstractInfo {
    /**
     * Creates information about this bug from the bug, thread, and detail.
     */
    public LocalClientInfo(Throwable bug, String threadName, String detail, boolean fatal) {
        //Store the basic information ...
        _limewireVersion = FrostWireUtils.getFrostWireVersion();
        _javaVersion = VersionUtils.getJavaVersion();
        _javaVendor = prop("java.vendor");
        _os = OSUtils.getOS();
        _osVersion = prop("os.version");
        _architecture = prop("os.arch");
        _freeMemory = "" + Runtime.getRuntime().freeMemory();
        _totalMemory = "" + Runtime.getRuntime().totalMemory();
        _peakThreads = "" + ManagementFactory.getThreadMXBean().getPeakThreadCount();
        _loadAverage = getLoadAvg();
        _pendingObjects = "" + ManagementFactory.getMemoryMXBean().getObjectPendingFinalizationCount();
        _heapUsage = "" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        _nonHeapUsage = "" + ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        _settingsFreeSpace = getFreeSpace(CommonUtils.getUserSettingsDir());
        _incompleteFreeSpace = "";//getFreeSpace(SharingSettings.INCOMPLETE_DIRECTORY.getValue());
        //_downloadFreeSpace = getFreeSpace(SharingSettings.getSaveDirectory());
        //Store information about the bug and the current thread.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        bug.printStackTrace(pw);
        pw.flush();
        _bug = sw.toString();
        _currentThread = threadName;
        _bugName = bug.getClass().getName();
        _fatalError = "" + fatal;
        //Store the properties.
        sw = new StringWriter();
        pw = new PrintWriter(sw);
        Properties props = new Properties();
        // Load the properties from SettingsFactory, excluding
        // FileSettings and FileArraySettings.
        SettingsFactory sf = LimeProps.instance().getFactory();
        for (Setting set : sf) {
            if (!set.isPrivate() && !set.isDefault())
                props.put(set.getKey(), set.getValueAsString());
        }
        // list the properties in the PrintWriter.
        props.list(pw);
        pw.flush();
        _props = sw.toString();
        //Store extra debugging information.
        if (GUIMediator.isConstructed() && LimeWireCore.instance() != null) {
            LimeWireCore.instance().getLifecycleManager().isLoaded();
        }
        //Store the detail, thread counts, and other information.
        _detail = detail;
        Thread[] allThreads = new Thread[Thread.activeCount()];
        int copied = Thread.enumerate(allThreads);
        _threadCount = "" + copied;
        Map<String, Integer> threads = new HashMap<>();
        for (int i = 0; i < copied; i++) {
            String name = allThreads[i].getName();
            Integer val = threads.get(name);
            if (val == null)
                threads.put(name, 1);
            else {
                int num = val + 1;
                threads.put(name, num);
            }
        }
        sw = new StringWriter();
        pw = new PrintWriter(sw);
        for (Map.Entry<String, Integer> info : threads.entrySet())
            pw.println(info.getKey() + ": " + info.getValue());
        pw.flush();
        _otherThreads = sw.toString();
    }

    /**
     * Uses reflection to retrieve the free space in the partition the file is located on.
     */
    private static String getFreeSpace(File f) {
        return invoke16Method(f, File.class, "getUsableSpace", Long.class);
    }

    /**
     * Uses reflection to get the load average of the computer.
     */
    private static String getLoadAvg() {
        return invoke16Method(ManagementFactory.getOperatingSystemMXBean(),
                OperatingSystemMXBean.class,
                "getSystemLoadAverage",
                Double.class);
    }

    /**
     * Attempts to run the given method if it exists running on Java 1.6 or above.
     * This can only be used for calling methods with no parameters.
     *
     * @param obj     The object to run the method on
     * @param type    The class the method can be found on
     * @param method  The name of the method to find
     * @param retType The expected return type of the method.
     * @return The result, is some predefined error parameters.
     */
    private static String invoke16Method(Object obj, Class<?> type, String method, Class<?> retType) {
        if (!VersionUtils.isJava16OrAbove())
            return "-1";
        try {
            Method m = type.getMethod(method);
            Object ret = m.invoke(obj);
            if (ret == null)
                return "-7";
            if (!(retType.isAssignableFrom(ret.getClass())))
                return "-5";
            return ret.toString();
        } catch (NoSuchMethodException bail) {
            return "-2";
        } catch (IllegalAccessException bail) {
            return "-3";
        } catch (InvocationTargetException bail) {
            return "-4";
        } catch (Throwable bad) {
            return "-6";
        }
    }

    /**
     * Returns the System property with the given name, or
     * "?" if it is unknown.
     */
    private String prop(String name) {
        String value = System.getProperty(name);
        if (value == null) return "?";
        else return value;
    }
}
