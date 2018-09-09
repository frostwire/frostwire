/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.search.youtube;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.youtube.jd.JsFunction;

/**
 * @author gubatron
 * @author aldenml
 */
final class YouTubeSig {

    private final JsFunction<String> fn;

    public YouTubeSig(String jscode) {
        String funcname = find1(jscode);
        if (funcname == null) {
            funcname = find2(jscode);
        }
        if (funcname == null) {
            funcname = find3(jscode);
        }
        if (funcname == null) {
            funcname = find4(jscode);
        }
        if (funcname == null) {
            throw new IllegalArgumentException("Unable to find signature function name");
        }

        this.fn = new JsFunction<>(jscode, funcname);
    }

    public String calc(String sig) {
        return fn.eval(sig);
    }

    private static String find1(String jscode) {
        String pattern = "yt\\.akamaized\\.net/\\)\\s*\\|\\|\\s*.*?\\s*c\\s*&&\\s*d\\.set\\([^,]+\\s*,\\s*([$a-zA-Z0-9]+)\\(";
        Matcher m = Pattern.compile(pattern).matcher(jscode);
        return m.find() ? m.group(1) : null;
    }

    private static String find2(String jscode) {
        String pattern = "([$a-zA-Z0-9]+)\\s*=\\s*function\\(.*?\\)\\{\\s*[a-z]=[a-z]\\.split\\(\"\"\\)\\s*;";
        Matcher m = Pattern.compile(pattern).matcher(jscode);
        return m.find() ? m.group(1) : null;
    }

    private static String find3(String jscode) {
        // same as find1, but with a fallback to simply "c&&d.set(b,<sig>(c))"
        String pattern = "c\\s*&&\\s*d\\.set\\([^,]+\\s*,\\s*([$a-zA-Z0-9]+)\\(";
        Matcher m = Pattern.compile(pattern).matcher(jscode);
        return m.find() ? m.group(1) : null;
    }

    private static String find4(String jscode) {
        //The function is usually found in a block like this:
        /*
         * if (e.sig || e.s) {
         var f = e.sig || gr(e.s);
         e.url = xj(e.url, {
         signature: f
         })
         }
         >> Output: gr
         */
        Matcher m = Pattern.compile("\"signature\"," + JsFunction.WS + "?([$a-zA-Z0-9]+)\\(").matcher(jscode);
        return m.find() ? m.group(1) : null;
    }
}
