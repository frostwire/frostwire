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

import android.app.Activity;

import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public interface Store {

    /**
     * List all products in the store.
     *
     * @return
     */
    Map<String, Product> products();

    Product product(String sku);

    /**
     * Determines if there is some product in the store with the
     * feature enabled.
     *
     * @param feature
     * @return
     */
    boolean enabled(String feature);

    /**
     * Synchronize with the actual server store data.
     */
    void refresh();

    /**
     * Launch the buy process, how it's handled the result is up to
     * every particular implementation.
     *
     * @param p
     */
    void purchase(Activity activity, Product p);
}
