/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class PaymentOptions implements Mappable<String, Map<String, String>> {
    /**
     * BitCoin URI, see BIP-0021 - https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki
     * bitcoinurn     = "bitcoin:" bitcoinaddress [ "?" bitcoinparams ]
     * bitcoinaddress = base58 *base58
     * Example: bitcoin:175tWpb8K1S7NmH4Zx6rewF9WQrcZv245W
     * <p>
     * To be serialized as dictionary in the .torrent as follows
     * paymentOptions: {
     * bitcoin: "bitcoin:13hbpRfDT1HKmK4jejHgh7MM9W1NCPFT8v",
     * paypalUrl: "http://frostwire.com/donate"
     * }
     */
    public final String bitcoin;
    /**
     * Simply a valid email address for creating a paypal payment form
     */
    public final String paypalUrl;
    private String itemName;
    public PaymentOptions() {
        bitcoin = null;
        paypalUrl = null;
    }

    public PaymentOptions(String bitcoin, String paypal) {
        this.bitcoin = bitcoin;
        this.paypalUrl = paypal;
    }

    public PaymentOptions(Map<String, Entry> paymentOptionsMap) {
        final Entry paymentOptions = paymentOptionsMap.get("paymentOptions");
        if (paymentOptions != null) {
            final Map<String, Entry> dictionary = paymentOptions.dictionary();
            if (dictionary.containsKey("bitcoin")) {
                this.bitcoin = dictionary.get("bitcoin").string();
            } else {
                this.bitcoin = null;
            }
            if (dictionary.containsKey("paypalUrl")) {
                this.paypalUrl = dictionary.get("paypalUrl").string();
            } else {
                this.paypalUrl = null;
            }
        } else {
            this.bitcoin = null;
            this.paypalUrl = null;
        }
    }

    public Map<String, Map<String, String>> asMap() {
        Map<String, String> innerMap = new HashMap<>();
        if (!StringUtils.isNullOrEmpty(bitcoin)) {
            innerMap.put("bitcoin", bitcoin);
        }
        if (!StringUtils.isNullOrEmpty(paypalUrl)) {
            innerMap.put("paypalUrl", paypalUrl);
        }
        Map<String, Map<String, String>> paymentOptions = new HashMap<>();
        if (!innerMap.isEmpty()) {
            paymentOptions.put("paymentOptions", innerMap);
        }
        return paymentOptions;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String name) {
        itemName = name;
    }

    public boolean isEmpty() {
        return bitcoin == null && paypalUrl == null;
    }

    public enum PaymentMethod {
        BITCOIN, PAYPAL
    }
}