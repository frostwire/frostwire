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

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.frostwire.android.offers.Products.toDays;
import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PlayStore extends StoreBase {

    private static boolean AVAILABLE = false;
    private static final Logger LOG = Logger.getLogger(PlayStore.class);

    // Taken from: Google Play Developer Console -> Services & APIs
    // Base64-encoded RSA public key to include in your binary.
    private static final String BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn4zB2rCYz3oXs33iFIHagzwpca0AEvRYHyr2xOW9gGwBokU51LdIjzq5NOzj3++aa9vIvj/K9eFHCPxkXa5g2qjm1+lc+fJwIEA/hAnA4ZIee3KrD52kyTqfZfhEYGklzvarbo3WN2gcUzwvvsVP9e1UZqtoYgFDThttKaFUboqqt1424lp7C2da89WTgHNpUyykIwQ1zYR34YOQ23SFPesSx8Fmz/Nz2rAHBNuFy13OE2LWPK+kLfm8P+tUAOcDSlq0NuT/FkuGpvziPaOS5BVpvfiAjjnUNLfH7dEO5wh7RPAskcNhQH1ykp6RauZFryMJbbHUe6ydGRHzpRkRpwIDAQAB";

    private static final int BILLING_MANAGER_NOT_INITIALIZED = -1;

    private static final long REFRESH_RESOLUTION_MILLIS = 30 * 1000; // 30 seconds

    private Inventory inventory;
    private BillingClient billingClient;
    private boolean isServiceConnected;
    private int billingClientResponseCode = BILLING_MANAGER_NOT_INITIALIZED;

    private long lastRefreshTime;
    private String lastSkuPurchased;
    private Set<String> tokensToBeConsumed;

    private WeakReference<PurchasesUpdatedListener> globalPurchasesUpdatedListenerWeakRef;

    private static final Object lock = new Object();
    private static PlayStore instance;

    private final PurchasesUpdatedListener onPurchasesUpdatedListener = (billingResult, purchases) -> {
        if (inventory == null) {
            LOG.info("Inventory is null, review your logic");
            return;
        }
        int responseCode = billingResult.getResponseCode();
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases != null) {
                for (Purchase purchase : purchases) {
                    handlePurchase(purchase);
                }
                products = buildProducts(inventory);
            } else {
                LOG.info("Received no purchases");
            }

        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            LOG.info("onPurchasesUpdated() - user cancelled the purchase flow - skipping");
        } else {
            LOG.info("onPurchasesUpdated() got unknown resultCode: " + responseCode);
        }

        try {
            if (Ref.alive(globalPurchasesUpdatedListenerWeakRef)) {
                globalPurchasesUpdatedListenerWeakRef.get().onPurchasesUpdated(billingResult, purchases);
            }
        } catch (Throwable e) {
            LOG.error("Error calling global onPurchasesUpdated listener", e);
        }
    };

    @NonNull
    public static PlayStore getInstance(@NonNull Context context) {
        synchronized (lock) {
            if (instance == null) {
                instance = new PlayStore(context.getApplicationContext());
            }
            AVAILABLE = instance != null;
            return instance;
        }
    }

    public static boolean available() {
        return AVAILABLE;
    }

    private PlayStore(Context context) {
        inventory = new Inventory();
        billingClient = BillingClient
                .newBuilder(context)
                .enablePendingPurchases()
                .setListener(onPurchasesUpdatedListener)
                .build();

        LOG.info("Starting setup.");

        // Start setup. This is asynchronous and the specified listener will be called
        // once setup completes.
        // It also starts to report all the new purchases through onPurchasesUpdated() callback.
        startServiceConnection(() -> {
            // IAB is fully set up. Now, let's populate the inventory
            LOG.info("Setup successful. Querying inventory.");
            queryInventory();
        });
    }

    @Override
    public void refresh() {
        if (isClientNull()) {
            return;
        }

        if (isClientDisconnected()) {
            LOG.info("Attempted to refresh with no connected client");
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastRefreshTime) < REFRESH_RESOLUTION_MILLIS) {
            LOG.info("Call to refresh() aborted, too early.");
            return;
        }

        lastRefreshTime = now;

        queryInventory();
    }

    @Override
    public void purchase(Activity activity, Product p) {
        if (isClientNull()) {
            return;
        }

        if (isClientDisconnected()) {
            LOG.info("Attempted to purchase with no connected client");
            return;
        }

        if (!p.available()) {
            LOG.info("Attempted to purchase an unavailable product");
            return;
        }

        try {
            initiatePurchaseFlow(activity, p.sku(), p.subscription() ? BillingClient.SkuType.SUBS : BillingClient.SkuType.INAPP);
        } catch (Throwable e) {
            LOG.error("Error launching purchase flow", e);
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

    public void dispose() {
        LOG.info("Destroying the internal client");

        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
            billingClient = null;
        }
    }

    /**
     * This method is used only for internal tests.
     */
    public final void consume(Product product) {
        if (product.subscription() || !product.purchased()) {
            throw new IllegalArgumentException("Only inapp purchases can be consumed");
        }
        try {
            Purchase purchase = inventory.getPurchase(product.sku());
            consumeAsync(purchase.getPurchaseToken());
            LOG.info("Product " + product.sku() + " consumed (async).");
        } catch (Throwable e) {
            LOG.error("Error consuming purchase.", e);
        }
    }

    public void setGlobalPurchasesUpdatedListenerWeakRef(PurchasesUpdatedListener listener) {
        this.globalPurchasesUpdatedListenerWeakRef = Ref.weak(listener);
    }

    private boolean isClientNull() {
        if (billingClient == null) {
            LOG.info("Internal client is null, looks like dispose was called, review your logic");
            return true;
        } else {
            return false;
        }
    }

    private boolean isClientDisconnected() {
        if (billingClient == null || !isServiceConnected || billingClientResponseCode != BillingClient.BillingResponseCode.OK) {
            LOG.info("Internal client is disconnected");
            return true;
        } else {
            return false;
        }
    }

    private void queryInventory() {
        if (isClientDisconnected()) {
            return;
        }

        async(this, PlayStore::queryPurchases);
        querySkuDetails();
    }

    /**
     * This operation is async
     */
    private void startServiceConnection(final Runnable executeOnSuccess) {
        if (isClientNull()) {
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                int billingResponseCode = billingResult.getResponseCode();
                LOG.info("Setup finished. Response code: " + billingResponseCode);

                billingClientResponseCode = billingResponseCode;

                if (billingResponseCode == BillingClient.BillingResponseCode.OK) {
                    isServiceConnected = true;
                    if (executeOnSuccess != null) {
                        executeOnSuccess.run();
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                isServiceConnected = false;
            }
        });
    }

    /**
     * Query purchases across various use cases and deliver the result in a formalized way through
     * a listener
     */
    private void queryPurchases() {
        Runnable queryToExecute = () -> {
            if (isClientDisconnected()) {
                return;
            }

            try {
                long time = System.currentTimeMillis();
                Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
                LOG.info("Querying purchases elapsed time: " + (System.currentTimeMillis() - time) + "ms");
                // If there are subscriptions supported, we add subscription rows as well
                if (areSubscriptionsSupported()) {
                    Purchase.PurchasesResult subscriptionResult = billingClient.queryPurchases(BillingClient.SkuType.SUBS);
                    LOG.info("Querying purchases and subscriptions elapsed time: "
                            + (System.currentTimeMillis() - time) + "ms");
                    LOG.info("Querying subscriptions result code: "
                            + subscriptionResult.getResponseCode()
                            + " res: " + subscriptionResult.getPurchasesList().size());

                    if (subscriptionResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        purchasesResult.getPurchasesList().addAll(subscriptionResult.getPurchasesList());
                    } else {
                        LOG.info("Got an error response trying to query subscription purchases");
                    }
                } else if (purchasesResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    LOG.info("Skipped subscription purchases query since they are not supported");
                } else {
                    LOG.info("queryPurchases() got an error response code: " + purchasesResult.getResponseCode());
                }
                onQueryPurchasesFinished(purchasesResult);
            } catch (Throwable e) {
                LOG.error("Error in queryPurchases()", e);
            }
        };

        executeServiceRequest(queryToExecute);
    }

    private void querySkuDetailsAsync(@BillingClient.SkuType final String itemType, final List<String> skuList,
                                      final SkuDetailsResponseListener listener) {
        Runnable queryRequest = () -> {
            if (isClientDisconnected()) {
                return;
            }

            try {
                SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                params.setSkusList(skuList).setType(itemType);
                billingClient.querySkuDetailsAsync(params.build(), listener);
            } catch (Throwable e) {
                LOG.error("Error in querySkuDetailsAsync()", e);
            }
        };

        executeServiceRequest(queryRequest);
    }

    private void querySkuDetails() {
        SkuDetailsResponseListener listener = (billingResult, skuDetailsList) -> {
            if (inventory == null) {
                LOG.warn("Inventory is null, review your logic");
                return;
            }

            int responseCode = billingResult.getResponseCode();

            if (responseCode == BillingClient.BillingResponseCode.OK) {
                for (SkuDetails detail : skuDetailsList) {
                    inventory.addSkuDetails(detail);
                }
                products = buildProducts(inventory);
            } else {
                LOG.info("onSkuDetailsResponse() got unknown resultCode: " + responseCode);
            }
        };
        querySkuDetailsAsync(BillingClient.SkuType.INAPP, Products.itemSkus(), listener);
        querySkuDetailsAsync(BillingClient.SkuType.SUBS, Products.subsSkus(), listener);
    }

    /**
     * Start a purchase flow
     */
    private void initiatePurchaseFlow(final Activity activity, final String skuId,
                                      final @BillingClient.SkuType String billingType) {
        Runnable purchaseFlowRequest = () -> {
            if (isClientDisconnected()) {
                return;
            }

            try {
                LOG.info("Launching in-app purchase flow");
                List<String> skusList = new ArrayList<>();
                skusList.add(skuId);
                SkuDetailsParams skuDetailsParams = SkuDetailsParams.newBuilder().setSkusList(skusList).setType(billingType).build();
                billingClient.querySkuDetailsAsync(skuDetailsParams, (billingResult, skuDetailsList) -> {
                    try {
                        SkuDetails skuDetails = skuDetailsList.get(0);
                        LOG.info("Launching billing flow for SKU " + skuId);
                        BillingFlowParams purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build();
                        billingClient.launchBillingFlow(activity, purchaseParams);
                    } catch (Throwable t) {
                        LOG.error("Error in initiatePurchaseFlow::billingClient.querySkuDetailsAsync callback", t);
                    }
                });

            } catch (Throwable e) {
                LOG.error("Error in initiatePurchaseFlow()", e);
            }
        };

        executeServiceRequest(purchaseFlowRequest);
    }

    private void consumeAsync(final String purchaseToken) {
        // If we've already scheduled to consume this token - no action is needed (this could happen
        // if you received the token when querying purchases inside onReceive() and later from
        // onActivityResult()
        if (tokensToBeConsumed == null) {
            tokensToBeConsumed = new HashSet<>();
        } else if (tokensToBeConsumed.contains(purchaseToken)) {
            LOG.info("Token was already scheduled to be consumed - skipping...");
            return;
        }
        tokensToBeConsumed.add(purchaseToken);

        // Generating Consume Response listener
        final ConsumeResponseListener onConsumeListener = (responseCode, purchaseToken1) -> {
            // If billing service was disconnected, we try to reconnect 1 time
            // (feel free to introduce your retry policy here).
            //mBillingUpdatesListener.onConsumeFinished(purchaseToken, responseCode);
        };

        // Creating a runnable from the request to use it inside our connection retry policy below
        Runnable consumeRequest = () -> {
            if (isClientDisconnected()) {
                return;
            }
            try {
                ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build();
                billingClient.consumeAsync(consumeParams, onConsumeListener);
            } catch (Throwable e) {
                LOG.error("Error calling consumeAsync", e);
            }
        };

        executeServiceRequest(consumeRequest);
    }

    private void executeServiceRequest(Runnable runnable) {
        if (isServiceConnected) {
            runnable.run();
        } else {
            // If billing service was disconnected, we try to reconnect 1 time.
            // (feel free to introduce your retry policy here).
            startServiceConnection(runnable);
        }
    }

    /**
     * Checks if subscriptions are supported for current client
     * <p>Note: This method does not automatically retry for RESULT_SERVICE_DISCONNECTED.
     * It is only used in unit tests and after queryPurchases execution, which already has
     * a retry-mechanism implemented.
     * </p>
     */
    private boolean areSubscriptionsSupported() {
        int responseCode = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).getResponseCode();
        if (responseCode != BillingClient.BillingResponseCode.OK) {
            LOG.info("areSubscriptionsSupported() got an error response: " + responseCode);
        }
        return responseCode == BillingClient.BillingResponseCode.OK;
    }

    /**
     * Handle a result from querying of purchases and report an updated list to the listener
     */
    private void onQueryPurchasesFinished(Purchase.PurchasesResult result) {
        // Have we been disposed of in the meantime? If so, or bad result code, then quit
        if (billingClient == null || result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            LOG.info("Billing client was null or result code (" + result.getResponseCode()
                    + ") was bad - quitting");
            return;
        }

        LOG.info("Query inventory was successful.");
        BillingResult billingResult = BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build();
        onPurchasesUpdatedListener.onPurchasesUpdated(billingResult, result.getPurchasesList());
    }

    /**
     * Handles the purchase
     * <p>Note: Notice that for each purchase, we check if signature is valid on the client.
     * It's recommended to move this check into your backend.
     * See {@link Security#verifyPurchase(String, String, String)}
     * </p>
     *
     * @param purchase Purchase to be handled
     */
    private void handlePurchase(Purchase purchase) {
        if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
            LOG.info("Got a purchase: " + purchase + "; but signature is bad. Skipping...");
            return;
        }

        LOG.info("Got a verified purchase: " + purchase);

        inventory.addPurchase(purchase);
    }

    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     * <p>Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     * </p>
     */
    private boolean verifyValidSignature(String signedData, String signature) {
        try {
            return Security.verifyPurchase(BASE_64_ENCODED_PUBLIC_KEY, signedData, signature);
        } catch (Throwable e) {
            LOG.error("Got an exception trying to validate a purchase: " + e);
            return false;
        }
    }

    private Map<String, Product> buildProducts(Inventory inventory) {
        if (inventory == null) {
            LOG.warn("Inventory is null, review your logic");
            return Collections.emptyMap();
        }

        Map<String, Product> m = new HashMap<>();

        // build each product, one by one, not magic here intentionally
        Product product;

        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_MONTH_SKU, BillingClient.SkuType.INAPP, inventory, toDays(Products.INAPP_DISABLE_ADS_1_MONTH_SKU));
        m.put(product.sku(), product);

        product = buildDisableAds(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU, BillingClient.SkuType.INAPP, inventory, toDays(Products.INAPP_DISABLE_ADS_6_MONTHS_SKU));
        m.put(product.sku(), product);

        product = buildDisableAds(Products.INAPP_DISABLE_ADS_1_YEAR_SKU, BillingClient.SkuType.INAPP, inventory, toDays(Products.INAPP_DISABLE_ADS_1_YEAR_SKU));
        m.put(product.sku(), product);

        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_MONTH_SKU, BillingClient.SkuType.SUBS, inventory, toDays(Products.SUBS_DISABLE_ADS_1_MONTH_SKU));
        m.put(product.sku(), product);

        product = buildDisableAds(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU, BillingClient.SkuType.SUBS, inventory, toDays(Products.SUBS_DISABLE_ADS_6_MONTHS_SKU));
        m.put(product.sku(), product);

        product = buildDisableAds(Products.SUBS_DISABLE_ADS_1_YEAR_SKU, BillingClient.SkuType.SUBS, inventory, toDays(Products.SUBS_DISABLE_ADS_1_YEAR_SKU));
        m.put(product.sku(), product);

        return m;
    }

    @NonNull
    private Product buildDisableAds(final String sku, final String type, Inventory inventory, final int days) {
        final SkuDetails d = inventory.getSkuDetails(sku);
        Purchase p = inventory.getPurchase(sku);

        // see if product exists
        final boolean exists = d != null && d.getType().equals(type); // product exists in the play store

        final boolean subscription = type.equals(BillingClient.SkuType.SUBS);
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
            if (!s.equals(sku) && inventory.hasPurchase(s)) {
                conflict = true;
            }
        }

        // see if product is purchased
        boolean purchased = p != null; // already purchased
        // see if time expired, then consume it
        if (p != null && type.equals(BillingClient.SkuType.INAPP)) {
            long time = TimeUnit.DAYS.toMillis(days);
            long now = System.currentTimeMillis();
            if (now - p.getPurchaseTime() > time) {
                try {
                    consumeAsync(p.getPurchaseToken());
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
                if (feature == null) {
                    return false;
                }

                // only support disable ads feature
                if (!feature.equals(Products.DISABLE_ADS_FEATURE)) {
                    return false;
                }

                // if available, then the user does not have it, then
                // the feature is not enabled
                if (available) {
                    return false;
                }

                // at this point, the user have it, if it's a subscription
                // then it is enabled
                if (type.equals(BillingClient.SkuType.SUBS)) {
                    return true;
                }

                long time = TimeUnit.DAYS.toMillis(days);
                long now = System.currentTimeMillis();
                return now - purchaseTime <= time;
            }
        };
    }

    /**
     * Represents a block of information about in-app items.
     */
    private static final class Inventory {

        private final Map<String, SkuDetails> mSkuMap = new HashMap<>();
        private final Map<String, Purchase> mPurchaseMap = new HashMap<>();

        Inventory() {
        }

        /**
         * Returns the listing details for an in-app product.
         */
        SkuDetails getSkuDetails(String sku) {
            return mSkuMap.get(sku);
        }

        /**
         * Returns purchase information for a given product, or null if there is no purchase.
         */
        Purchase getPurchase(String sku) {
            return mPurchaseMap.get(sku);
        }

        /**
         * Returns whether or not there exists a purchase of the given product.
         */
        boolean hasPurchase(String sku) {
            return mPurchaseMap.containsKey(sku);
        }

        void addSkuDetails(SkuDetails d) {
            mSkuMap.put(d.getSku(), d);
        }

        // custom change for instant update
        // sync on each billing code update
        void addPurchase(Purchase p) {
            mPurchaseMap.put(p.getSku(), p);
        }
    }

    /**
     * Security-related methods. For a secure implementation, all of this code should be implemented on
     * a server that communicates with the application on the device.
     */
    private static final class Security {

        private static final String KEY_FACTORY_ALGORITHM = "RSA";
        private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

        /**
         * Verifies that the data was signed with the given signature, and returns the verified
         * purchase.
         *
         * @param base64PublicKey the base64-encoded public key to use for verifying.
         * @param signedData      the signed JSON string (signed, not encrypted)
         * @param signature       the signature for the data, signed with the private key
         * @throws IOException if encoding algorithm is not supported or key specification
         *                     is invalid
         */
        static boolean verifyPurchase(String base64PublicKey, String signedData,
                                      String signature) throws IOException {
            if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(base64PublicKey)
                    || TextUtils.isEmpty(signature)) {
                LOG.info("Purchase verification failed: missing data.");
                return false;
            }

            PublicKey key = generatePublicKey(base64PublicKey);
            return verify(key, signedData, signature);
        }

        /**
         * Generates a PublicKey instance from a string containing the Base64-encoded public key.
         *
         * @param encodedPublicKey Base64-encoded public key
         * @throws IOException if encoding algorithm is not supported or key specification
         *                     is invalid
         */
        static PublicKey generatePublicKey(String encodedPublicKey) throws IOException {
            try {
                byte[] decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT);
                KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
                return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
            } catch (NoSuchAlgorithmException e) {
                // "RSA" is guaranteed to be available.
                throw new RuntimeException(e);
            } catch (InvalidKeySpecException e) {
                String msg = "Invalid key specification: " + e;
                LOG.error(msg);
                throw new IOException(msg);
            }
        }

        /**
         * Verifies that the signature from the server matches the computed signature on the data.
         * Returns true if the data is correctly signed.
         *
         * @param publicKey  public key associated with the developer account
         * @param signedData signed data from server
         * @param signature  server signature
         * @return true if the data and signature match
         */
        static boolean verify(PublicKey publicKey, String signedData, String signature) {
            byte[] signatureBytes;
            try {
                signatureBytes = Base64.decode(signature, Base64.DEFAULT);
            } catch (IllegalArgumentException e) {
                LOG.error("Base64 decoding failed.");
                return false;
            }
            try {
                Signature signatureAlgorithm = Signature.getInstance(SIGNATURE_ALGORITHM);
                signatureAlgorithm.initVerify(publicKey);
                signatureAlgorithm.update(signedData.getBytes());
                if (!signatureAlgorithm.verify(signatureBytes)) {
                    LOG.info("Signature verification failed.");
                    return false;
                }
                return true;
            } catch (NoSuchAlgorithmException e) {
                // "RSA" is guaranteed to be available.
                throw new RuntimeException(e);
            } catch (InvalidKeyException e) {
                LOG.error("Invalid key specification.");
            } catch (SignatureException e) {
                LOG.error("Signature exception.");
            }
            return false;
        }
    }
}
