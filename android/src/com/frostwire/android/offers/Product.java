/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.offers;

import java.util.Date;

/**
 * The product mirrors the stock in the store, this is intended
 * to have a static list of instances in the store (locally) to
 * query and aide with business logic.
 *
 * @author gubatron
 * @author aldenml
 */
public interface Product {

    /**
     * The code that represents this product in the store.
     *
     * @return
     */
    String sku();

    /**
     * @return
     */
    boolean subscription();

    String title();

    String description();

    /**
     * Formatted string
     *
     * @return
     */
    String price();

    String currency();

    /**
     * The product is currently owned by the user.
     *
     * @return
     */
    boolean purchased();

    /**
     * The product is available for purchase.
     * <p>
     * For example, a subscription is always available while a
     * consumable item could be available based on some rule.
     * Another example of an unavailable product is one than
     * is in conflict with a currently owned one.
     *
     * @return
     */
    boolean available();

    /**
     * The product feature is enable at the moment.
     *
     * @param feature
     * @return
     */
    boolean enable(String feature);
}
