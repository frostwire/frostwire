/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Simple JSON utility class based on google-gson.
 * <p/>
 * Visit google-gson: {@link https://code.google.com/p/google-gson/} for more information.
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
