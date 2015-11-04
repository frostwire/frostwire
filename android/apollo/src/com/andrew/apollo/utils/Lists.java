/*
 * Copyright 2012 Google Inc. Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Provides static methods for creating {@code List} instances easily, and other
 * utility methods for working with lists.
 */
public final class Lists {

    /** This class is never instantiated */
    public Lists() {
    }

    /**
     * Creates an empty {@code ArrayList} instance.
     * <p>
     * <b>Note:</b> if you only need an <i>immutable</i> empty List, use
     * {@link Collections#emptyList} instead.
     * 
     * @return a newly-created, initially-empty {@code ArrayList}
     */
    public static final <E> ArrayList<E> newArrayList() {
        return new ArrayList<E>();
    }

    /**
     * Creates an empty {@code LinkedList} instance.
     * <p>
     * <b>Note:</b> if you only need an <i>immutable</i> empty List, use
     * {@link Collections#emptyList} instead.
     * 
     * @return a newly-created, initially-empty {@code LinkedList}
     */
    public static final <E> LinkedList<E> newLinkedList() {
        return new LinkedList<E>();
    }

}
