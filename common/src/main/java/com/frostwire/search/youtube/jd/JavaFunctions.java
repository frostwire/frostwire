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

package com.frostwire.search.youtube.jd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 *
 */
final class JavaFunctions {

    private JavaFunctions() {
    }

    public static boolean isdigit(String str) {
        if (str.length() == 0) {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isalpha(String str) {
        if (str.length() == 0) {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!Character.isLetter(str.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static List<Object> list(Object obj) {
        Object[] r = new Object[len(obj)];

        if (obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            for (int i = 0; i < arr.length; i++) {
                r[i] = arr[i];
            }
            return new ArrayList<Object>(Arrays.asList(r));
        }

        if (obj instanceof String) {
            String str = (String) obj;
            for (int i = 0; i < str.length(); i++) {
                r[i] = str.charAt(i);
            }
            return new ArrayList<Object>(Arrays.asList(r));
        }

        throw new IllegalArgumentException("Not supported type");
    }

    public static String join(Object[] arr) {
        StringBuilder sb = new StringBuilder();

        for (Object obj : arr) {
            sb.append(obj.toString());
        }

        return sb.toString();
    }

    public static String join(Object[] arr, Object obj) {
        if (arr.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        sb.append(arr[0].toString());
        for (int i = 1; i < arr.length; i++) {
            sb.append(obj.toString());
            sb.append(arr[i].toString());
        }

        return sb.toString();
    }

    public static String join(List<Object> list, Object obj) {
        if (list.size() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        sb.append(list.get(0).toString());
        for (int i = 1; i < list.size(); i++) {
            sb.append(obj.toString());
            sb.append(list.get(i).toString());
        }

        return sb.toString();
    }

    public static Integer len(Object obj) {

        if (obj instanceof Object[]) {
            return ((Object[]) obj).length;
        }

        if (obj instanceof String) {
            return ((String) obj).length();
        }

        if (obj instanceof List<?>) {
            return ((List<?>) obj).size();
        }

        throw new IllegalArgumentException("Not supported type");
    }

    public static void reverse(Object obj) {

        if (obj instanceof List<?>) {
            Collections.reverse((List<?>) obj);
            return;
        }

        throw new IllegalArgumentException("Not supported type");
    }

    public static Object slice(Object obj, int fromIndex) {

        if (obj instanceof Object[]) {
            return Arrays.asList((Object[]) obj).subList(fromIndex, ((Object[]) obj).length).toArray();
        }

        if (obj instanceof String) {
            return ((String) obj).substring(fromIndex);
        }

        throw new IllegalArgumentException("Not supported type");
    }

    public static String escape(String s) {
        return Pattern.quote(s);
    }

    public static String[] mscpy(String[] arr) {
        String[] r = new String[arr.length];

        for (int i = 0; i < arr.length; i++) {
            r[i] = new String(arr[i].toCharArray());
        }

        return r;
    }

    public static Object json_loads(String js) {
        if (js.equals("\"\"")) {
            return "";
        }

        return null;
    }
}
