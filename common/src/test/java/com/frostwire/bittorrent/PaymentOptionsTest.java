/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.PaymentOptions.PaymentMethod;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The tip options a creator puts in a torrent (Create Torrent writes them with {@link
 * PaymentOptions#asMap()}) and the transfers list offers for it.
 */
class PaymentOptionsTest {

  @Test
  void bothOptionsAreWrittenUnderPaymentOptions() {
    PaymentOptions options =
        new PaymentOptions("bitcoin:13hbpRfDT1HKmK4jejHgh7MM9W1NCPFT8v", "https://example.org/tip");

    assertEquals(
        Map.of(
            "paymentOptions",
            Map.of(
                "bitcoin", "bitcoin:13hbpRfDT1HKmK4jejHgh7MM9W1NCPFT8v",
                "paypalUrl", "https://example.org/tip")),
        options.asMap());
    assertFalse(options.isEmpty());
  }

  @Test
  void onlyTheOptionsGivenAreWritten() {
    assertEquals(
        Map.of("paymentOptions", Map.of("paypalUrl", "https://example.org/tip")),
        new PaymentOptions("", "https://example.org/tip").asMap());
    assertEquals(
        Map.of("paymentOptions", Map.of("bitcoin", "bitcoin:1abc")),
        new PaymentOptions("bitcoin:1abc", null).asMap());
  }

  @Test
  void noOptionsWriteNothing() {
    PaymentOptions none = new PaymentOptions();

    assertTrue(none.asMap().isEmpty());
    assertTrue(none.isEmpty());
    assertNull(none.bitcoin);
    assertNull(none.paypalUrl);
  }

  @Test
  void writtenOptionsReadBackTheSame() {
    PaymentOptions written = new PaymentOptions("bitcoin:1abc", "https://example.org/tip");

    @SuppressWarnings({"unchecked", "rawtypes"})
    PaymentOptions read = new PaymentOptions((Map) written.asMap());

    assertEquals("bitcoin:1abc", read.bitcoin);
    assertEquals("https://example.org/tip", read.paypalUrl);
  }

  @Test
  void optionsThatAreNotADictionaryReadAsNone() {
    PaymentOptions read = new PaymentOptions(Map.of("paymentOptions", "bitcoin:1abc"));

    assertTrue(read.isEmpty());
    assertTrue(read.asMap().isEmpty());
  }

  @Test
  void anOptionThatIsNotTextReadsAsAbsent() {
    PaymentOptions read =
        new PaymentOptions(
            Map.of("paymentOptions", Map.of("bitcoin", 42L, "paypalUrl", "https://example.org")));

    assertNull(read.bitcoin);
    assertEquals("https://example.org", read.paypalUrl);
  }

  @Test
  void theItemNameIsWhatTheTipIsFor() {
    PaymentOptions options = new PaymentOptions();

    options.setItemName("Jazz Collection.flac");

    assertEquals("Jazz Collection.flac", options.getItemName());
  }

  @Test
  void tipsArePaidWithBitcoinOrPayPal() {
    assertEquals(
        java.util.List.of(PaymentMethod.BITCOIN, PaymentMethod.PAYPAL),
        java.util.List.of(PaymentMethod.values()));
  }
}
