/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gudy.azureus2.core3.util;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.security.AccessControlException;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;

/**
 *
 * @author Olivier
 * @author gubatron
 */
public class Constants {
    public static final String DEFAULT_ENCODING = "UTF8";
    static final Locale LOCALE_ENGLISH = new Locale("en", "");

    static {
        try {
            String timezone = System.getProperty("azureus.timezone", null);
            if (timezone != null) {
                TimeZone.setDefault(TimeZone.getTimeZone(timezone));
            }
        } catch (Throwable e) {
            // can happen in applet
            if (!(e instanceof AccessControlException)) {
                e.printStackTrace();
            }
        }
    }

    static final String INFINITY_STRING = "\u221E"; // "oo";pa
    static final int CRAPPY_INFINITY_AS_INT = 365 * 24 * 3600; // seconds (365days)
    static final long CRAPPY_INFINITE_AS_LONG = 10000 * 365 * 24 * 3600; // seconds (10k years)

    // keep the CVS style constant coz version checkers depend on it!
    // e.g. 2.0.8.3
    //      2.0.8.3_CVS
    //      2.0.8.3_Bnn       // incremental build

    static String APP_NAME = "Vuze";
    static final String AZUREUS_VERSION = "5.4.0.1_CVS";

    private static final boolean FORCE_NON_CVS = System.getProperty("az.force.noncvs", "0").equals("1");

    static final boolean IS_CVS_VERSION = isCVSVersion(AZUREUS_VERSION) && !FORCE_NON_CVS;

    private static final String OSName = System.getProperty("os.name");

    public static final boolean isOSX = OSName.toLowerCase().startsWith("mac os");
    public static final boolean isLinux = OSName.equalsIgnoreCase("Linux");
    public static final boolean isWindows = OSName.toLowerCase().startsWith("windows");
    // If it isn't windows or osx, it's most likely an unix flavor

    private static final boolean isWindowsVista;

    static {
        if (isWindows) {
            Float ver = null;
            try {
                ver = new Float(System.getProperty("os.version"));
            } catch (Throwable ignored) {
            }

            if (ver == null) {
                isWindowsVista = false;
            } else {
                float f_ver = ver;

                isWindowsVista = f_ver == 6;

                if (isWindowsVista) {

                    LineNumberReader lnr = null;

                    try {
                        Process p =
                                Runtime.getRuntime().exec(
                                        new String[]{
                                                "reg",
                                                "query",
                                                "HKLM\\Software\\Microsoft\\Windows NT\\CurrentVersion",
                                                "/v",
                                                "CSDVersion"});

                        lnr = new LineNumberReader(new InputStreamReader(p.getInputStream()));

                        while (true) {

                            String line = lnr.readLine();

                            if (line == null) {

                                break;
                            }

                            if (line.matches(".*CSDVersion.*")) {

                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (lnr != null) {
                            try {
                                lnr.close();
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }


        } else {

            isWindowsVista = false;
        }
    }

    private static final boolean isAndroid;

    static {
        String vm_name = System.getProperty("java.vm.name", "");
        isAndroid = vm_name.equalsIgnoreCase("Dalvik");
    }

    // Android is roughly 1.6 (reports as 0 for java.version)

    static final String JAVA_VERSION;

    static {
        String java_version = isAndroid ? "1.6" : System.getProperty("java.version");
        int api_level;

        if (isAndroid) {

            String sdk_int = System.getProperty("android.os.build.version.sdk_int", "0");

            try {
                api_level = Integer.parseInt(sdk_int);

                if (api_level > 0 && api_level <= 8) {

                    java_version = "1.5";
                }
            } catch (Throwable e) {
            }
        }

        JAVA_VERSION = java_version;
    }


    private static boolean
    isCVSVersion(
            String version) {
        return (version.indexOf("_") != -1);
    }

    /**
     * compare two version strings of form n.n.n.n (e.g. 1.2.3.4)
     *
     * @param version_1
     * @param version_2
     * @return -ve -> version_1 lower, 0 = same, +ve -> version_1 higher
     */

    private static int
    compareVersions(
            String version_1,
            String version_2) {
        try {
            version_1 = version_1.replaceAll("_CVS", "_B100");
            version_2 = version_2.replaceAll("_CVS", "_B100");

            if (version_1.startsWith(".")) {
                version_1 = "0" + version_1;
            }
            if (version_2.startsWith(".")) {
                version_2 = "0" + version_2;
            }

            version_1 = version_1.replaceAll("[^0-9.]", ".");
            version_2 = version_2.replaceAll("[^0-9.]", ".");

            StringTokenizer tok1 = new StringTokenizer(version_1, ".");
            StringTokenizer tok2 = new StringTokenizer(version_2, ".");

            while (true) {
                if (tok1.hasMoreTokens() && tok2.hasMoreTokens()) {

                    int i1 = Integer.parseInt(tok1.nextToken());
                    int i2 = Integer.parseInt(tok2.nextToken());

                    if (i1 != i2) {

                        return (i1 - i2);
                    }
                } else if (tok1.hasMoreTokens()) {

                    int i1 = Integer.parseInt(tok1.nextToken());

                    if (i1 != 0) {

                        return (1);
                    }
                } else if (tok2.hasMoreTokens()) {

                    int i2 = Integer.parseInt(tok2.nextToken());

                    if (i2 != 0) {

                        return (-1);
                    }
                } else {
                    return (0);
                }
            }
        } catch (Throwable e) {

            e.printStackTrace();

            return (0);
        }
    }

    public static void main(String[] args) {
        System.out.println(compareVersions("3.0.0.1", "3.0.0.0"));
        System.out.println(compareVersions("3.0.0.0_B1", "3.0.0.0"));
        System.out.println(compareVersions("3.0.0.0", "3.0.0.0_B1"));
        System.out.println(compareVersions("3.0.0.0_B1", "3.0.0.0_B4"));
        System.out.println(compareVersions("3.0.0.0..B1", "3.0.0.0_B4"));
    }
}
