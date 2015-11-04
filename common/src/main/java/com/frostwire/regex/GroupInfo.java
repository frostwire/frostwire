/**
 * Copyright (C) 2012-2013 The named-regexp Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.frostwire.regex;

/**
 * Contains the position and group index of capture groups
 * from a named pattern
 */
public class GroupInfo {
    private int pos;
    private int groupIndex;

    /**
     * Constructs a {@code GroupInfo} with a group index and string
     * position
     *
     * @param groupIndex the group index
     * @param pos the position
     */
    public GroupInfo(int groupIndex, int pos) {
        this.groupIndex = groupIndex;
        this.pos = pos;
    }

    /**
     * Gets the string position of the group within a named pattern
     *
     * @return the position
     */
    public int pos() {
        return pos;
    }

    /**
     * Gets the group index of the named capture group
     *
     * @return the group index
     */
    public int groupIndex() {
        return groupIndex;
    }
}
