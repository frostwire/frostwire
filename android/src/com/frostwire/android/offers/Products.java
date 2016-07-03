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

import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Products {

    // products SKUs
    public static final String INAPP_DISABLE_ADS_1_MONTH_SKU = "com.frostwire.inapp.disable_ads.1_month.test";
    public static final String INAPP_DISABLE_ADS_6_MONTHS_SKU = "com.frostwire.inapp.disable_ads.6_months.test";
    public static final String INAPP_DISABLE_ADS_1_YEAR_SKU = "com.frostwire.inapp.disable_ads.1_year.test";
    public static final String SUBS_DISABLE_ADS_1_MONTH_SKU = "com.frostwire.subs.disable_ads.1_month.test";
    public static final String SUBS_DISABLE_ADS_6_MONTHS_SKU = "com.frostwire.subs.disable_ads.6_months.test";
    public static final String SUBS_DISABLE_ADS_1_YEAR_SKU = "com.frostwire.subs.disable_ads.1_year.test";

    // features codes
    public static final String DISABLE_ADS_FEATURE = "DISABLE_ADS_FEATURE";

    public static boolean disableAds(Store store) {
        return store.enable(DISABLE_ADS_FEATURE);
    }

    static Map<String, Product> mockProducts() {
        Product p1 = new Product() {
            @Override
            public String sku() {
                return Products.INAPP_DISABLE_ADS_1_MONTH_SKU;
            }

            @Override
            public boolean subscription() {
                return false;
            }

            @Override
            public String title() {
                return Products.INAPP_DISABLE_ADS_1_MONTH_SKU;
            }

            @Override
            public String description() {
                return Products.INAPP_DISABLE_ADS_1_MONTH_SKU;
            }

            @Override
            public String price() {
                return "$0.99";
            }

            @Override
            public String currency() {
                return "USD";
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean enable(String feature) {
                return false;
            }
        };
        Product p2 = new Product() {
            @Override
            public String sku() {
                return Products.SUBS_DISABLE_ADS_1_MONTH_SKU;
            }

            @Override
            public boolean subscription() {
                return true;
            }

            @Override
            public String title() {
                return Products.SUBS_DISABLE_ADS_1_MONTH_SKU;
            }

            @Override
            public String description() {
                return Products.SUBS_DISABLE_ADS_1_MONTH_SKU;
            }

            @Override
            public String price() {
                return "$0.99";
            }

            @Override
            public String currency() {
                return "USD";
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean enable(String feature) {
                return false;
            }
        };

        Map<String, Product> m = new HashMap<>();

        m.put(p1.sku(), p1);
        m.put(p2.sku(), p2);

        return m;
    }
}
