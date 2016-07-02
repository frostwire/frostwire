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
import android.content.Context;
import android.content.Intent;
import com.android.vending.billing.*;
import com.frostwire.logging.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PlayStore extends StoreBase {

    private static final Logger LOG = Logger.getLogger(PlayStore.class);

    // Taken from: Google Play Developer Console -> Services & APIs
    // Base64-encoded RSA public key to include in your binary.
    private static final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn4zB2rCYz3oXs33iFIHagzwpca0AEvRYHyr2xOW9gGwBokU51LdIjzq5NOzj3++aa9vIvj/K9eFHCPxkXa5g2qjm1+lc+fJwIEA/hAnA4ZIee3KrD52kyTqfZfhEYGklzvarbo3WN2gcUzwvvsVP9e1UZqtoYgFDThttKaFUboqqt1424lp7C2da89WTgHNpUyykIwQ1zYR34YOQ23SFPesSx8Fmz/Nz2rAHBNuFy13OE2LWPK+kLfm8P+tUAOcDSlq0NuT/FkuGpvziPaOS5BVpvfiAjjnUNLfH7dEO5wh7RPAskcNhQH1ykp6RauZFryMJbbHUe6ydGRHzpRkRpwIDAQAB";

    private static final long REFRESH_RESOLUTION_MILLIS = 30 * 1000; // 30 seconds

    private static final int RC_NO_ADS_INAPP_REQUEST = 30123;
    private static final int RC_NO_ADS_SUBS_REQUEST = 30223;

    private static final String INAPP_TYPE = "inapp";
    private static final String SUBS_TYPE = "subs";

    private IabHelper helper;
    private IabHelper.QueryInventoryFinishedListener inventoryListener;
    private IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener;
    private IabHelper.OnConsumeFinishedListener consumeFinishedListener;

    private long lastRefreshTime;

    public PlayStore() {
        inventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                if (result.isFailure()) {
                    LOG.error("Failed to query inventory: " + result);
                    return;
                }

                if (helper == null) {
                    LOG.warn("Helper has been disposed");
                    return;
                }

                if (inventory == null) {
                    LOG.warn("Failed to get inventory, something wrong with the IabHelper");
                    return;
                }

                products = buildProducts(inventory);
            }
        };

        purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
            public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                if (result.isFailure()) {
                    LOG.error("Error purchasing: " + result);
                    return;
                }

                if (helper == null) {
                    LOG.warn("Helper has been disposed");
                    return;
                }

                String sku = purchase.getSku();
                LOG.info("Purchased sku " + sku);
            }
        };

        consumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
            public void onConsumeFinished(Purchase purchase, IabResult result) {
                if (result.isFailure()) {
                    LOG.error("Error consuming: " + result);
                    return;
                }

                if (helper == null) {
                    LOG.warn("Helper has been disposed");
                    return;
                }

                LOG.info("Consumption finished. Purchase: " + purchase + ", result: " + result);
            }
        };
    }

    private static class Loader {
        static final PlayStore INSTANCE = new PlayStore();
    }

    public static PlayStore getInstance() {
        return PlayStore.Loader.INSTANCE;
    }

    public void initialize(Context context) {
        if (helper != null) {
            LOG.warn("Already called this method, review your logic, just returning");
            return;
        }

        helper = new IabHelper(context, base64EncodedPublicKey);
        helper.enableDebugLogging(true, LOG.getName()); // toggle this value for development

        helper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    LOG.error("Problem setting up in-app billing: " + result);
                    return;
                }

                refresh();
            }
        });
    }

    @Override
    public void refresh() {
        long now = System.currentTimeMillis();
        if ((now - lastRefreshTime) < REFRESH_RESOLUTION_MILLIS) {
            return;
        }

        lastRefreshTime = now;

        if (helper == null) {
            LOG.warn("Helper has been disposed or not initialized");
            return;
        }

        try {
            helper.queryInventoryAsync(inventoryListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            LOG.error("Error querying inventory. Another async operation in progress.", e);
        }
    }

    @Override
    public void purchase(Activity activity, Product p) {
        if (helper == null) {
            LOG.warn("Helper has been disposed or not initialized");
            return;
        }

        if (!p.available()) {
            LOG.warn("Attempted to purchase an unavailable product, review your logic");
            return;
        }

        try {
            int codeRequest = p.subscription() ? RC_NO_ADS_SUBS_REQUEST : RC_NO_ADS_INAPP_REQUEST;
            helper.launchPurchaseFlow(activity, p.sku(), codeRequest, purchaseFinishedListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            LOG.error("Error launching purchase flow. Another async operation in progress.", e);
        }
    }

    public void dispose() {
        if (helper == null) {
            LOG.warn("Helper has been disposed or not initialized");
            return;
        }

        try {
            helper.disposeWhenFinished();
        } catch (Throwable e) {
            LOG.error("Error disposing the internal helper (review your logic)", e);
        }
        helper = null;
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (helper == null) {
            LOG.warn("Helper has been disposed or not initialized");
            return false;
        }

        return helper.handleActivityResult(requestCode, resultCode, data);
    }


    private Map<String, Product> buildProducts(Inventory inventory) {
        if (inventory == null) {
            LOG.warn("Inventory is null, review your logic");
            return Collections.emptyMap();
        }

        Map<String, Product> l = new HashMap<>();

        // build each product, one by one, not magic here intentionally
        Product product;

        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_MONTH_SKU, INAPP_TYPE, inventory, 31);
        if (product != null) {
            l.put(product.sku(), product);
        }
        product = buildDisableAds(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU, INAPP_TYPE, inventory, 183);
        if (product != null) {
            l.put(product.sku(), product);
        }
        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_YEAR_SKU, INAPP_TYPE, inventory, 365);
        if (product != null) {
            l.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_MONTH_SKU, INAPP_TYPE, inventory, 31);
        if (product != null) {
            l.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU, INAPP_TYPE, inventory, 183);
        if (product != null) {
            l.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_YEAR_SKU, INAPP_TYPE, inventory, 365);
        if (product != null) {
            l.put(product.sku(), product);
        }

        return l;
    }

    private Product buildDisableAds(final String sku, final String type, Inventory inventory, final int days) {
        final SkuDetails d = inventory.getSkuDetails(sku);
        Purchase p = inventory.getPurchase(sku);

        // see if product exists
        final boolean exists = d != null && d.getType() == type; // product exists in the play store

        // see if it the user has some conflicting sku purchase
        String[] disableAdsSku = new String[]{
                Products.INAPP_DISABLE_ADS_1_MONTH_SKU,
                Products.INAPP_DISABLE_ADS_6_MONTHS_SKU,
                Products.INAPP_DISABLE_ADS_1_YEAR_SKU,
                Products.SUBS_DISABLE_ADS_1_MONTH_SKU,
                Products.SUBS_DISABLE_ADS_6_MONTHS_SKU,
                Products.SUBS_DISABLE_ADS_1_YEAR_SKU
        };
        boolean conflict = false;
        for (int i = 0; !conflict && i < disableAdsSku.length; i++) {
            String s = disableAdsSku[i];
            if (s != sku && inventory.hasPurchase(s)) {
                conflict = true;
            }
        }

        // see if product is purchased
        boolean purchased = p != null; // already purchased
        // see if time expired, then consume it
        if (p != null && type == INAPP_TYPE) {
            long time = TimeUnit.DAYS.toMillis(days);
            long now = System.currentTimeMillis();
            if (now - p.getPurchaseTime() > time) {
                try {
                    helper.consumeAsync(p, consumeFinishedListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    LOG.error("Error consuming purchase. Another async operation in progress.", e);
                }
                purchased = false;
            }
        }

        final boolean available = exists && !conflict && !purchased;
        final long purchaseTime = purchased ? p.getPurchaseTime() : 0;

        return new Product() {
            @Override
            public String sku() {
                return sku;
            }

            @Override
            public boolean subscription() {
                return type == SUBS_TYPE;
            }

            @Override
            public String title() {
                return exists ? d.getTitle() : "NA";
            }

            @Override
            public String description() {
                return exists ? d.getDescription() : "NA";
            }

            @Override
            public String price() {
                return exists ? d.getPrice() : "NA";
            }

            @Override
            public boolean available() {
                return available;
            }

            @Override
            public boolean enable(String feature) {
                // only support disable ads feature
                if (feature != Products.DISABLE_ADS_FEATURE) {
                    return false;
                }

                // if available, then the user does not have it, then
                // the feature is not enable
                if (available) {
                    return false;
                }

                // at this point, the user have it, if it's a subscription
                // then it is enabled
                if (type == SUBS_TYPE) {
                    return true;
                }

                long time = TimeUnit.DAYS.toMillis(days);
                long now = System.currentTimeMillis();
                return now - purchaseTime <= time;
            }
        };
    }
}
