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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
abstract class StoreBase implements Store {

    protected Map<String, Product> products;

    protected StoreBase() {
        this.products = Collections.emptyMap();
    }

    @Override
    public Map<String, Product> products() {
        return Collections.unmodifiableMap(products);
    }

    @Override
    public Product product(String sku) {
        return products.get(sku);
    }

    @Override
    public boolean enable(String code) {
        boolean r = false;
        Iterator<Product> it = products.values().iterator();

        while (!r && it.hasNext()) {
            r = it.next().enable(code);
        }

        return r;
    }
}
