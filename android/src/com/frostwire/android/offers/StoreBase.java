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

import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
abstract class StoreBase implements Store {

    protected List<Product> products;

    protected StoreBase() {
        this.products = Collections.emptyList();
    }

    @Override
    public List<Product> available() {
        List<Product> l = new LinkedList<>();

        for (Product p : products) {
            if (p.available()) {
                l.add(p);
            }
        }

        return Collections.unmodifiableList(l);
    }

    @Override
    public boolean enable(String code) {
        boolean r = false;
        Iterator<Product> it = products.iterator();

        while (!r && it.hasNext()) {
            r = it.next().enable(code);
        }

        return r;
    }
}
