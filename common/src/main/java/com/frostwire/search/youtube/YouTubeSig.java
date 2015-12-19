/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.frostwire.search.youtube.jd.JsFunction;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class YouTubeSig {

	private final JsFunction<String> fn;

	public YouTubeSig(String jscode) {
		//The function is usually found in a block like this:
		/**
		 * if (e.sig || e.s) {
		 var f = e.sig || gr(e.s);
		 e.url = xj(e.url, {
		 signature: f
		 })
		 }
		 >> Output: gr
		 */
		Matcher m = Pattern.compile("\\.sig\\|\\|([$a-zA-Z0-9]+)\\(").matcher(jscode);
        m.find();
        String funcname = m.group(1);
        this.fn = new JsFunction<String>(jscode, funcname);
    }

	public String calc(String sig) {
		return fn.eval(sig);
	}
}
