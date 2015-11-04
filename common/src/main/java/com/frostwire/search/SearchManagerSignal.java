/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.

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

package com.frostwire.search;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchManagerSignal {

    public SearchManagerSignal(long token) {
        this.token = token;
    }

    public final long token;

    public static final class Results extends SearchManagerSignal {

        public Results(long token, List<? extends SearchResult> elements) {
            super(token);
            this.elements = elements;
        }
        
        public final List<? extends SearchResult> elements;
    }

    public static final class End extends SearchManagerSignal {

        End(long token) {
            super(token);
        }
    }
}
