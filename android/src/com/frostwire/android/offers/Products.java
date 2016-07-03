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

import java.util.Arrays;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Products {

    private Products() {
    }

    // products SKUs
    public static final String INAPP_DISABLE_ADS_1_MONTH_SKU = "com.frostwire.inapp.disable_ads.1_month.test";
    public static final String INAPP_DISABLE_ADS_6_MONTHS_SKU = "com.frostwire.inapp.disable_ads.6_months.test";
    public static final String INAPP_DISABLE_ADS_1_YEAR_SKU = "com.frostwire.inapp.disable_ads.1_year.test";
    public static final String SUBS_DISABLE_ADS_1_MONTH_SKU = "com.frostwire.subs.disable_ads.1_month.test";
    public static final String SUBS_DISABLE_ADS_6_MONTHS_SKU = "com.frostwire.subs.disable_ads.6_months.test";
    public static final String SUBS_DISABLE_ADS_1_YEAR_SKU = "com.frostwire.subs.disable_ads.1_year.test";

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

    public static boolean disableAds(Store store) {
        return store.enable(DISABLE_ADS_FEATURE);
    }
}
