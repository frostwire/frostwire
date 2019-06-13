/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

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

package com.frostwire.search.filter;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SourceKey implements FilterKey {
    private final String source;
    private final int ordinal;
    private String display;

    public SourceKey(String source, int ordinal) {
        this.source = source;
        this.ordinal = ordinal;
        this.display = source;
    }

    public String source() {
        return source;
    }

    @Override
    public String display() {
        return display;
    }

    public void display(String value) {
        this.display = value;
    }

    @Override
    public int compareTo(FilterKey o) {
        if (!(o instanceof SourceKey)) {
            return -1;
        }
        int x = ordinal;
        int y = ((SourceKey) o).ordinal;
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }
}
