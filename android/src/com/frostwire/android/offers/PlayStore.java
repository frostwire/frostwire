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

import com.android.vending.billing.IabHelper;
import com.android.vending.billing.Inventory;
import com.android.vending.billing.Purchase;
import com.android.vending.billing.SkuDetails;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.Debug;
import com.frostwire.util.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.frostwire.android.offers.Products.toDays;

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
    private final IabHelper.QueryInventoryFinishedListener inventoryListener;
    private final IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener;
    private final IabHelper.OnConsumeFinishedListener consumeFinishedListener;

    private Inventory inventory;
    private long lastRefreshTime;

    private String lastSkuPurchased;

    public PlayStore() {
        inventoryListener = (result, inventory) -> {
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

            PlayStore.this.inventory = inventory;
            products = buildProducts(inventory);
        };

        purchaseFinishedListener = (result, purchase) -> {
            if (result.isFailure()) {
                LOG.error("Error purchasing: " + result);
                return;
            }

            if (helper == null) {
                LOG.warn("Helper has been disposed");
                return;
            }

            if (purchase == null) {
                // could be the result of a call to endAsync
                return;
            }

            String sku = purchase.getSku();
            lastSkuPurchased = sku;
            LOG.info("Purchased sku " + sku);

            if (inventory != null) {
                try {
                    inventory.addPurchase(purchase);
                    products = buildProducts(inventory);
                    LOG.info("Inventory updated");
                } catch (Throwable e) {
                    LOG.error("Error updating internal inventory after purchase", e);
                }
            }
        };

        consumeFinishedListener = (purchase, result) -> {
            if (result.isFailure()) {
                LOG.error("Error consuming: " + result);
                return;
            }

            if (helper == null) {
                LOG.warn("Helper has been disposed");
                return;
            }

            LOG.info("Consumption finished. Purchase: " + purchase + ", result: " + result);
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
        helper.enableDebugLogging(Debug.isEnabled(), LOG.getName()); // toggle this value for development

        helper.startSetup(result -> {
            if (!result.isSuccess()) {
                LOG.error("Problem setting up in-app billing: " + result);
                return;
            }

            refresh();
        });
    }

    @Override
    public void refresh() {
        long now = System.currentTimeMillis();
        if ((now - lastRefreshTime) < REFRESH_RESOLUTION_MILLIS) {
            LOG.info("refresh() aborted, too early.");
            return;
        }

        lastRefreshTime = now;

        if (helper == null) {
            LOG.warn("Helper has been disposed or not initialized");
            return;
        }

        try {
            List<String> items = Products.itemSkus();
            List<String> subs = Products.subsSkus();
            helper.queryInventoryAsync(true, items, subs, inventoryListener);
            LOG.info("Refreshing inventory...");
        } catch (IabHelper.IabAsyncInProgressException e) {
            LOG.error("Error querying inventory. Another async operation in progress.", e);
        } catch (Throwable t) {
            LOG.error("Error querying inventory.", t);
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
        } catch (Throwable e) {
            LOG.error("Error launching purchase flow.", e);
        }

        if (BuildConfig.DEBUG) {
            UIUtils.showLongMessage(activity, "The purchase will be mocked");
            lastSkuPurchased = p.sku();
        }
    }

    @Override
    public boolean enabled(String code) {
        if (BuildConfig.DEBUG) {
            if (lastSkuPurchased != null) {
                return true;
            }
        }
        return super.enabled(code);
    }

    /**
     * This method is used only for internal tests.
     *
     * @param product
     */
    public final void consume(Product product) {
        if (product.subscription() || !product.purchased()) {
            throw new IllegalArgumentException("Only inapp purchases can be consumed");
        }
        try {
            final Purchase purchase = inventory.getPurchase(product.sku());
            helper.consumeAsync(purchase, consumeFinishedListener);
            LOG.info("product " + product.sku() + " consumed (async).");
        } catch (IabHelper.IabAsyncInProgressException e) {
            LOG.error("Error consuming purchase. Another async operation in progress.", e);
        } catch (Throwable e) {
            LOG.error("Error consuming purchase.", e);
        }
    }

    public void endAsync() {
        if (helper == null) {
            LOG.warn("Helper has been disposed or not initialized");
            return;
        }

        try {
            helper.handleActivityResult(RC_NO_ADS_SUBS_REQUEST, 0, null);
            helper.handleActivityResult(RC_NO_ADS_INAPP_REQUEST, 0, null);
        } catch (Throwable e) {
            LOG.error("Error ending async operation in the internal helper (review your logic)", e);
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

        Map<String, Product> m = new HashMap<>();

        // build each product, one by one, not magic here intentionally
        Product product;

        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_MONTH_SKU, INAPP_TYPE, inventory, toDays(Products.INAPP_DISABLE_ADS_1_MONTH_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU, INAPP_TYPE, inventory, toDays(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_YEAR_SKU, INAPP_TYPE, inventory, toDays(Products.INAPP_DISABLE_ADS_1_YEAR_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_MONTH_SKU, SUBS_TYPE, inventory, toDays(Products.SUBS_DISABLE_ADS_1_MONTH_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU, SUBS_TYPE, inventory, toDays(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_YEAR_SKU, SUBS_TYPE, inventory, toDays(Products.SUBS_DISABLE_ADS_1_YEAR_SKU));
        if (product != null) {
            m.put(product.sku(), product);
        }
        return m;
    }

    private Product buildDisableAds(final String sku, final String type, Inventory inventory, final int days) {
        final SkuDetails d = inventory.getSkuDetails(sku);
        Purchase p = inventory.getPurchase(sku);

        // see if product exists
        final boolean exists = d != null && d.getType().equals(type); // product exists in the play store

        final boolean subscription = type == SUBS_TYPE;
        final String title = exists ? d.getTitle() : "NA";
        final String description = exists ? d.getDescription() : "NA";
        final String price = exists ? d.getPrice() : "NA";
        final String currency = exists ? d.getPriceCurrencyCode() : "NA";

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
                } catch (Throwable e) {
                    LOG.error("Error consuming purchase", e);
                }
                purchased = false;
            }
        }

        final boolean available = exists && !conflict && !purchased;
        final long purchaseTime = purchased ? p.getPurchaseTime() : 0;

        return new Products.ProductBase(sku, subscription, title,
                description, price, currency, purchased, purchaseTime, available) {

            @Override
            public boolean enabled(String feature) {
                // only support disable ads feature
                if (feature != Products.DISABLE_ADS_FEATURE) {
                    return false;
                }

                // if available, then the user does not have it, then
                // the feature is not enabled
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
