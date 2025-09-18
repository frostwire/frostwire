/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.offers;

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
     * Purchase time, this value only have meaning if the method
     * {@link #purchased()} returns true, and represents the number
     * of milliseconds since January 1, 1970 00:00:00 UTC.
     *
     * @return
     */
    long purchaseTime();

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
     * The product feature is enabled at the moment.
     *
     * @param feature
     * @return
     */
    boolean enabled(String feature);
}
