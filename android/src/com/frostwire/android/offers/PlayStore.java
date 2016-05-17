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

import android.content.Context;
import com.android.vending.billing.IabHelper;
import com.android.vending.billing.IabResult;
import com.android.vending.billing.Inventory;
import com.android.vending.billing.Purchase;
import com.frostwire.logging.Logger;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PlayStore {

    private static final Logger LOG = Logger.getLogger(PlayStore.class);

    // Taken from: Google Play Developer Console -> Services & APIs
    // Base64-encoded RSA public key to include in your binary.
    private static final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn4zB2rCYz3oXs33iFIHagzwpca0AEvRYHyr2xOW9gGwBokU51LdIjzq5NOzj3++aa9vIvj/K9eFHCPxkXa5g2qjm1+lc+fJwIEA/hAnA4ZIee3KrD52kyTqfZfhEYGklzvarbo3WN2gcUzwvvsVP9e1UZqtoYgFDThttKaFUboqqt1424lp7C2da89WTgHNpUyykIwQ1zYR34YOQ23SFPesSx8Fmz/Nz2rAHBNuFy13OE2LWPK+kLfm8P+tUAOcDSlq0NuT/FkuGpvziPaOS5BVpvfiAjjnUNLfH7dEO5wh7RPAskcNhQH1ykp6RauZFryMJbbHUe6ydGRHzpRkRpwIDAQAB";

    private static final String SKU_NO_ADS_TEST = "frostwire.no_ads.test1";

    private IabHelper helper;
    private IabHelper.QueryInventoryFinishedListener inventoryListener;
    private Inventory inventory;

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

                PlayStore.this.inventory = inventory;
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

    public void refresh() {
        if (helper == null) {
            LOG.warn("Helper has been disposed");
            return;
        }

        try {
            helper.queryInventoryAsync(inventoryListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            LOG.error("Error querying inventory. Another async operation in progress.", e);
        }
    }

    public void dispose() {
        if (helper == null) {
            LOG.warn("Helper has been disposed");
            return;
        }

        helper.disposeWhenFinished();
        helper = null;
    }

    public boolean showAds() {
        if (inventory == null) {
            LOG.warn("Inventory not loaded, review your logic");
            return true;
        }

        Purchase p = inventory.getPurchase(SKU_NO_ADS_TEST);
        if (p == null) {
            return true;
        }

        // more verifications of the potential subscription here

        return false;
    }
}
