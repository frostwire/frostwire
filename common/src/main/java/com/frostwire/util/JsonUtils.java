/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014,, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Simple JSON utility class based on google-gson.
 * <p/>
 * Visit google-gson: {@link http://code.google.com/p/google-gson/} for more information.
 *
 * @author gubatron
 * @author aldenml
 */
public final class JsonUtils {

    private static final Gson gson = new GsonBuilder().create();
    private static final Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();

    private JsonUtils() {
    }

    /**
     * This method serializes the specified object into its equivalent Json
     * representation.
     * <p/>
     * This method should only be used when the specified object is not a generic type.
     *
     * @param obj the object for which Json representation is to be created
     * @return Json representation of obj
     */
    public static String toJson(Object obj, boolean pretty) {
        return pretty ? gsonPretty.toJson(obj) : gson.toJson(obj);
    }

    /**
     * This method serializes the specified object into its equivalent Json
     * representation.
     * <p/>
     * This method should only be used when the specified object is not a generic type.
     *
     * @param obj the object for which Json representation is to be created
     * @return Json representation of obj
     */
    public static String toJson(Object obj) {
        return toJson(obj, false);
    }

    /**
     * This method deserializes the specified Json into an object of the specified class.
     * <p/>
     * This method should not be used if the desired type is a generic type.
     *
     * @param <T>      the type of the desired object
     * @param json     the string from which the object is to be deserialized
     * @param classOfT the class of T
     * @return an object of type T from the string
     */
    public static <T> T toObject(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }
}
