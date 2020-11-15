/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Products {

    private Products() {
    }

    // products SKUs
    public static final String INAPP_DISABLE_ADS_1_MONTH_SKU = getSKU("com.frostwire.inapp.disable_ads.1_month");
    public static final String INAPP_DISABLE_ADS_6_MONTHS_SKU = getSKU("com.frostwire.inapp.disable_ads.6_months");
    public static final String INAPP_DISABLE_ADS_1_YEAR_SKU = getSKU("com.frostwire.inapp.disable_ads.1_year");
    public static final String SUBS_DISABLE_ADS_1_MONTH_SKU = getSKU("com.frostwire.subs.disable_ads.1_month");
    public static final String SUBS_DISABLE_ADS_6_MONTHS_SKU = getSKU("com.frostwire.subs.disable_ads.6_months");
    public static final String SUBS_DISABLE_ADS_1_YEAR_SKU = getSKU("com.frostwire.subs.disable_ads.1_year");
    public static final String REWARDS_DISABLE_ADS_MINUTES_SKU = "com.frostwire.reward.disable_ads_minutes";

    // inapp/subs product duration in days
    private static final int DISABLE_ADS_1_MONTH_DAYS = 31;
    private static final int DISABLE_ADS_6_MONTHS_DAYS = 183;
    private static final int DISABLE_ADS_1_YEAR_DAYS = 365;

    // features codes
    public static final String DISABLE_ADS_FEATURE = "DISABLE_ADS_FEATURE";

    public static List<String> itemSkus() {
        return Arrays.asList(
                INAPP_DISABLE_ADS_1_MONTH_SKU,
                INAPP_DISABLE_ADS_6_MONTHS_SKU,
                INAPP_DISABLE_ADS_1_YEAR_SKU
        );
    }

    public static List<String> subsSkus() {
        return Arrays.asList(
                SUBS_DISABLE_ADS_1_MONTH_SKU,
                SUBS_DISABLE_ADS_6_MONTHS_SKU,
                SUBS_DISABLE_ADS_1_YEAR_SKU
        );
    }

    public static boolean disabledAds(Store store) {
        return store.enabled(DISABLE_ADS_FEATURE);
    }

    public static int toDays(String sku) {
        int result = -1;

        if (INAPP_DISABLE_ADS_1_MONTH_SKU.equals(sku) ||
                SUBS_DISABLE_ADS_1_MONTH_SKU.equals(sku)) {
            result = DISABLE_ADS_1_MONTH_DAYS;
        } else if (INAPP_DISABLE_ADS_6_MONTHS_SKU.equals(sku) ||
                SUBS_DISABLE_ADS_6_MONTHS_SKU.equals(sku)) {
            result = DISABLE_ADS_6_MONTHS_DAYS;
        } else if (INAPP_DISABLE_ADS_1_YEAR_SKU.equals(sku) ||
                SUBS_DISABLE_ADS_1_YEAR_SKU.equals(sku)) {
            result = DISABLE_ADS_1_YEAR_DAYS;
        }

        if (result < 0) {
            throw new IllegalArgumentException("SKU argument does not represent a product with duration");
        }

        return result;
    }

    /**
     * Returns a list of products that have been purchased and enabled to the user.
     * @param store
     * @param code
     * @return
     */
    public static List<Product> listEnabled(Store store, String code) {
        List<Product> list = new LinkedList<>();

        for (Product p : store.products().values()) {
            if (!p.available() && p.purchased() && p.enabled(code)) {
                list.add(p);
            }
        }

        return list;
    }

    private static String getSKU(String skuId) {
        return skuId;
    }

    /**
     * NOTE: public only to sub packages.
     */
    public static class ProductBase implements Product {

        private final String sku;
        private final boolean subscription;
        private final String title;
        private final String description;
        private final String price;
        private final String currency;
        private final boolean purchased;
        private final long purchaseTime;
        private final boolean available;

        public ProductBase(String sku, boolean subscription,
                           String title, String description,
                           String price, String currency,
                           boolean purchased, long purchaseTime,
                           boolean available) {
            this.sku = sku;
            this.subscription = subscription;
            this.title = title;
            this.description = description;
            this.price = price;
            this.currency = currency;
            this.purchased = purchased;
            this.purchaseTime = purchaseTime;
            this.available = available;
        }

        @Override
        public String sku() {
            return sku;
        }

        @Override
        public boolean subscription() {
            return subscription;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String price() {
            return price;
        }

        @Override
        public String currency() {
            return currency;
        }

        @Override
        public boolean purchased() {
            return purchased;
        }

        @Override
        public long purchaseTime() {
            return purchaseTime;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean enabled(String feature) {
            return false;
        }
    }
}
