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

package com.frostwire.util.filetypes;

/**
 * The <code>AssociationException</code> class and its subclasses are thrown by
 * certain methods of <code>AssociationService</code> to indicate the operation
 * fails.
 *
 * @see AssociationService
 */
public class AssociationException extends Exception {
    private static final long serialVersionUID = 2585950054034209174L;

    /**
     * Constructs an <code>AssociationException</code> object with no detail message.
     */
    AssociationException() {
        super();
    }

    /**
     * Constructs an <code>AssociationException</code> object with the specified
     * detail message.
     *
     * @param msg the detail message pertaining to this exception.
     */
    AssociationException(String msg) {
        super(msg);
    }
}
