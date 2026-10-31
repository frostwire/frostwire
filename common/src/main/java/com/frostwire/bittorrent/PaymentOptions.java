/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.bittorrent;

import com.frostwire.util.Bencode;
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
   * bitcoinurn = "bitcoin:" bitcoinaddress [ "?" bitcoinparams ] bitcoinaddress = base58 *base58
   * Example: bitcoin:175tWpb8K1S7NmH4Zx6rewF9WQrcZv245W
   *
   * <p>To be serialized as dictionary in the .torrent as follows paymentOptions: { bitcoin:
   * "bitcoin:13hbpRfDT1HKmK4jejHgh7MM9W1NCPFT8v", paypalUrl: "http://frostwire.com/donate" }
   */
  public final String bitcoin;

  /** Simply a valid email address for creating a paypal payment form */
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

  /**
   * @param paymentOptionsMap a torrent's {@code paymentOptions} dictionary, in the form {@link
   *     Bencode#decode} returns
   */
  public PaymentOptions(Map<String, Object> paymentOptionsMap) {
    final Object paymentOptions = paymentOptionsMap.get("paymentOptions");
    if (paymentOptions != null) {
      final Map<String, Object> dictionary =
          BTInfoAdditionalMetadataHolder.dictionary(paymentOptions);
      if (dictionary.containsKey("bitcoin")) {
        this.bitcoin = BTInfoAdditionalMetadataHolder.string(dictionary.get("bitcoin"));
      } else {
        this.bitcoin = null;
      }
      if (dictionary.containsKey("paypalUrl")) {
        this.paypalUrl = BTInfoAdditionalMetadataHolder.string(dictionary.get("paypalUrl"));
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
    BITCOIN,
    PAYPAL
  }
}
