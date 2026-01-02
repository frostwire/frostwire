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

package com.frostwire.search.frostclick;

import com.frostwire.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
public class UserAgent {
    /**
     * Should have both the name and vesion number of the Operating System
     */
    public final static String OS_KEY = "OS";
    public final static String FW_VERSION_KEY = "FWversion";
    public final static String BUILD_KEY = "FWbuild";
    public final Pattern osPattern = Pattern.compile("REL_(v.*?)_([0-9\\.]+)_([0-9]+)");
    private final Map<String, String> headerMap;
    private final String uuid;

    public UserAgent(String operatingSystem, String fwVersion, String buildNumber) {
        headerMap = initHeadersMap(normalizeUnavailableString(operatingSystem), normalizeUnavailableString(fwVersion), normalizeUnavailableString(buildNumber));
        uuid = UUID.randomUUID().toString();
    }

    public UserAgent(String frostwireUserAgentString, String uuid) {
        String[] split = frostwireUserAgentString.split("-");
        //frostwire-1.2.2-build-117-REL_vL2R-0_4.2.2_17
        //0 - "frostwire"
        //1 - FW_VERSION_KEY
        //2 - "build"
        //3 - BUILD_KEY
        //4 - OS_KEY
        //5 - OS Version
        String osVersion = split[4];
        if (split.length == 6) {
            osVersion += "-" + split[5];
        }
        osVersion = normalizeOsVersionString(osVersion);
        headerMap = initHeadersMap(osVersion, split[1], split[3]);
        this.uuid = uuid;
    }

    public Map<String, String> getHeadersMap() {
        return headerMap;
    }

    public String toString() {
        return "frostwire-" + headerMap.get(FW_VERSION_KEY) + "-build-" + headerMap.get(BUILD_KEY) + "-" + headerMap.get(OS_KEY);
    }

    public String getUUID() {
        return uuid;
    }

    /**
     * getOSVersionMap().get("<b>RELEASE</b>") gives you the android version, e.g. 4.2.2<br/>
     * getOSVersionMap().get("<b>SDK_INT</b>") gives you the SDK number, e.g. 17<br/>
     * <br/>
     * headerMap[OS_KEY] string is built as follows: <br/>
     * Build.VERSION.CODENAME + "_" + <br/>
     * Build.VERSION.INCREMENTAL + "_" + <br/>
     * Build.VERSION.RELEASE + "_" + <br/>
     * Build.VERSION.SDK_INT;<br/>
     * <br/>
     * e.g. "REL_vL2R-0_4.2.2_17"<br/>
     * For those values, the output of this function would be the following:<br/>
     * {<br/>
     * "CODENAME"    : "REL",<br/>
     * "INCREMENTAL" : "vL2R-0"<br/>
     * "RELEASE"     : "4.2.2"<br/>
     * "SDK_INT"     : "17"<br/>
     * }<br/>
     *
     * @return A map that breaks down the android version information out of the OS_KEY bucket value.
     * Keys are "CODENAME", "INCREMENTAL", "RELEASE" and "SDK_INT", being the last 2 the most interesting ones.
     */
    public Map<String, String> getOSVersionMap() {
        String v = headerMap.get(OS_KEY);
        Map<String, String> osVersion = Collections.emptyMap();
        if (!StringUtils.isNullOrEmpty(v)) {
            String[] split = v.split("_");
            try {
                osVersion = new HashMap<String, String>();
                osVersion.put("CODENAME", split[0]);
                osVersion.put("INCREMENTAL", split[1]);
                osVersion.put("RELEASE", split[2]);
                osVersion.put("SDK_INT", split[3]);
            } catch (Throwable t) {
                osVersion = Collections.emptyMap();
            }
        }
        return osVersion;
    }

    private String normalizeOsVersionString(String osVersion) {
        Matcher matcher = osPattern.matcher(osVersion);
        if (matcher.find()) {
            //1 => INCREMENTAL (e.g. "vr3-rQ", "vL2R-0"), but sometimes comes like "vr3_rQ" so we replace _ for -
            //2 => RELEASE  (e.g. "4.2", "4.3.3")
            //3 => SDK_INT  (e.g. "18")
            osVersion = "REL_" + matcher.group(1).replace('_', '-') + "_" + matcher.group(2) + "_" + matcher.group(3);
        }
        return osVersion;
    }

    private Map<String, String> initHeadersMap(String operatingSystem, String fwVersion, String buildNumber) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(OS_KEY, operatingSystem);
        map.put(FW_VERSION_KEY, fwVersion);
        map.put(BUILD_KEY, buildNumber);
        return map;
    }

    private String normalizeUnavailableString(String str) {
        if (StringUtils.isNullOrEmpty(str)) {
            str = "NA";
        } else {
            str = normalizeOsVersionString(str);
        }
        return str;
    }
}
