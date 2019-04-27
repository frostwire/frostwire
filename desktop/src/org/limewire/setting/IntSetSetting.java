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


package org.limewire.setting;

import java.util.*;

public class IntSetSetting extends AbstractSetting {

    private Set<Integer> value;

    /**
     * Creates a new <tt>IntegerSetSetting</tt> instance with the specified
     * key and default value.
     *
     * @param key the constant key to use for the setting
     * @param defaultValue the default value to use for the setting
     */
    IntSetSetting(Properties defaultProps, Properties props, String key, Integer[] defaultValue) {
        super(defaultProps, props, key, decode(new HashSet<>(Arrays.asList(defaultValue))));
        setPrivate(true);
    }

    /**
     * Returns the value of this setting.
     * 
     * @return the value of this setting
     */
    public Set<Integer> getValue() {
        return value;
    }

    /** Gets the value as an array. */
    public synchronized Integer[] getValueAsArray() {
        return value.toArray(new Integer[value.size()]);
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    public void setValue(Set<? extends Integer> value) {
        setValueInternal(decode(value));
    }

    /**
     * Mutator for this setting.
     * @param i file to add to the array.
     */
    public synchronized void add(Integer i) {
        value.add(i);
        setValue(value);
    }

    /**
     * Mutator for this setting.
     *
     * @param i Remove file from the array, if it exists.
     * @return false when the array does not contain the file or when the
     * file is <code>null</code> 
     */
    public synchronized boolean remove(Integer i) {
        if (value.remove(i)) {
            setValue(value);
            return true;
        } else {
            return false;
        }
    }

    /** Returns true if the given file is contained in this array. */
    public synchronized boolean contains(Integer i) {
        return value.contains(i);
    }

    /** Returns the length of the array. */
    public synchronized int length() {
        return value.size();
    }

    /** Load value from property string value
     * @param sValue property string value
     *
     */
    protected synchronized void loadValue(String sValue) {
        value = encode(sValue);
    }

    /** Splits the string into a Set    */
    private static Set<Integer> encode(String src) {
        if (src == null || src.length() == 0)
            return new HashSet<>();

        StringTokenizer tokenizer = new StringTokenizer(src, ";");
        int size = tokenizer.countTokens();
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < size; i++)
            set.add(Integer.valueOf(tokenizer.nextToken()));
        return set;
    }

    /** Separates each field of the array by a semicolon     */
    private static String decode(Set<? extends Integer> src) {
        if (src == null || src.isEmpty())
            return "";

        StringBuilder buffer = new StringBuilder();
        for (Integer i : src) {
            buffer.append(i.toString()).append(';');
        }

        if (buffer.length() > 0) {
            buffer.setLength(buffer.length() - 1);
        }
        return buffer.toString();
    }
}