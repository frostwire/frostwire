/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.licenses.Licenses;
import com.frostwire.util.Bencode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reads the license and payment options FrostWire writes into a torrent's info dictionary ({@code
 * CreateTorrentDialog}) back through libtorrent, as transfers and the library do.
 *
 * <p>The malformed cases were found by BTInfoAdditionalMetadataHolderFuzzTest: a license or payment
 * section of the wrong shape (not a dictionary, a missing or non-string field) threw, so the holder
 * lost both the license and the payment options and desktop logged an error on every retry. Both
 * sections are optional: a malformed one reads as absent, a missing or non-string field as null,
 * and the other section is still read.
 */
class BTInfoAdditionalMetadataHolderTest {

  @Test
  void readsCreativeCommonsLicenseAndPaymentOptions() {
    Map<String, Object> info = info();
    info.put(
        "license",
        Map.of(
            "creative-commons",
            Map.of(
                "licenseUrl", Licenses.CC_BY_SA_4.getUrl(),
                "attributionTitle", "Jazz Collection",
                "attributionAuthor", "Some Artist",
                "attributionUrl", "https://example.org/jazz")));
    info.put(
        "paymentOptions",
        Map.of(
            "paymentOptions",
            Map.of(
                "bitcoin", "bitcoin:13hbpRfDT1HKmK4jejHgh7MM9W1NCPFT8v",
                "paypalUrl", "http://frostwire.com/donate")));

    BTInfoAdditionalMetadataHolder holder = read(info);

    CopyrightLicenseBroker license = holder.getLicenseBroker();
    assertNotNull(license);
    assertSame(Licenses.CC_BY_SA_4, license.license);
    Map<String, String> fields = license.asMap().get("creative-commons");
    assertEquals("Jazz Collection", fields.get("attributionTitle"));
    assertEquals("Some Artist", fields.get("attributionAuthor"));
    assertEquals("https://example.org/jazz", fields.get("attributionUrl"));
    PaymentOptions payment = holder.getPaymentOptions();
    assertEquals("bitcoin:13hbpRfDT1HKmK4jejHgh7MM9W1NCPFT8v", payment.bitcoin);
    assertEquals("http://frostwire.com/donate", payment.paypalUrl);
    assertEquals("Jazz Collection.flac", payment.getItemName());
  }

  @Test
  void readsOpenSourceLicenseWithoutPaymentOptions() {
    Map<String, Object> info = info();
    info.put(
        "license",
        Map.of(
            "open-source",
            Map.of(
                "licenseUrl", Licenses.GPL3.getUrl(),
                "attributionTitle", "FrostWire",
                "attributionAuthor", "FrostWire Team",
                "attributionUrl", "https://www.frostwire.com")));

    BTInfoAdditionalMetadataHolder holder = read(info);

    assertSame(Licenses.GPL3, holder.getLicenseBroker().license);
    assertEquals(
        "FrostWire Team",
        holder.getLicenseBroker().asMap().get("open-source").get("attributionAuthor"));
    assertTrue(holder.getPaymentOptions().isEmpty());
    assertEquals("Jazz Collection.flac", holder.getPaymentOptions().getItemName());
  }

  @Test
  void unknownLicenseUrlHasNoLicenseName() {
    Map<String, Object> info = info();
    info.put(
        "license",
        Map.of(
            "public-domain",
            Map.of(
                "licenseUrl", "http://example.org/unknown",
                "attributionTitle", "",
                "attributionAuthor", "",
                "attributionUrl", "")));

    BTInfoAdditionalMetadataHolder holder = read(info);

    assertNotNull(holder.getLicenseBroker());
    assertNull(holder.getLicenseBroker().getLicenseName());
  }

  @Test
  void torrentWithoutExtraMetadataHasEmptyPaymentOptions() {
    BTInfoAdditionalMetadataHolder holder = read(info());

    assertNull(holder.getLicenseBroker());
    assertTrue(holder.getPaymentOptions().isEmpty());
    assertEquals("Jazz Collection.flac", holder.getPaymentOptions().getItemName());
  }

  @Test
  void licenseCategoryWithoutFieldsHasNoLicense() {
    // Minimized from the fuzzer's input: info = {license: {public-domain: {}}}.
    Map<String, Object> info =
        Bencode.decodeDictionary(
            "d7:licensed13:public-domaindeee".getBytes(StandardCharsets.US_ASCII));

    BTInfoAdditionalMetadataHolder holder = new BTInfoAdditionalMetadataHolder(info, "name");

    assertNull(holder.getLicenseBroker().getLicenseName());
    assertTrue(holder.getPaymentOptions().isEmpty());
  }

  @Test
  void licenseWithoutAttributionStillReadsItsLicense() {
    Map<String, Object> info = info();
    info.put(
        "license", Map.of("creative-commons", Map.of("licenseUrl", Licenses.CC_BY_4.getUrl())));
    info.put("paymentOptions", Map.of("paymentOptions", Map.of("paypalUrl", "http://pay.example")));

    BTInfoAdditionalMetadataHolder holder = read(info);

    assertSame(Licenses.CC_BY_4, holder.getLicenseBroker().license);
    assertNull(holder.getLicenseBroker().asMap().get("creative-commons").get("attributionTitle"));
    assertEquals("http://pay.example", holder.getPaymentOptions().paypalUrl);
  }

  @Test
  void malformedLicenseKeepsPaymentOptions() {
    for (Object license :
        new Object[] {
          0L, "cc", Map.of("open-source", 1L), Map.of("creative-commons", Map.of("licenseUrl", 7L))
        }) {
      Map<String, Object> info = info();
      info.put("license", license);
      info.put("paymentOptions", Map.of("paymentOptions", Map.of("bitcoin", "bitcoin:1abc")));

      BTInfoAdditionalMetadataHolder holder = read(info);

      if (holder.getLicenseBroker() != null) {
        assertNull(holder.getLicenseBroker().getLicenseName(), String.valueOf(license));
      }
      assertEquals("bitcoin:1abc", holder.getPaymentOptions().bitcoin, String.valueOf(license));
    }
  }

  @Test
  void malformedPaymentOptionsKeepTheLicense() {
    for (Object payment :
        new Object[] {
          "paypal", Map.of("paymentOptions", 5L), Map.of("paymentOptions", Map.of("bitcoin", 1L))
        }) {
      Map<String, Object> info = info();
      info.put(
          "license",
          Map.of(
              "public-domain",
              Map.of(
                  "licenseUrl", Licenses.PUBLIC_DOMAIN_CC0.getUrl(),
                  "attributionTitle", "t",
                  "attributionAuthor", "a",
                  "attributionUrl", "u")));
      info.put("paymentOptions", payment);

      BTInfoAdditionalMetadataHolder holder = read(info);

      assertSame(Licenses.PUBLIC_DOMAIN_CC0, holder.getLicenseBroker().license);
      assertTrue(holder.getPaymentOptions().isEmpty(), String.valueOf(payment));
      assertEquals("Jazz Collection.flac", holder.getPaymentOptions().getItemName());
    }
  }

  @Test
  void licenseWithANonUtf8KeyKeepsPaymentOptions() {
    // libtorrent accepts any byte string as a key; the pure-Java decoder refuses non-UTF-8 keys.
    Map<String, Object> info = info();
    info.put("license", Map.of("~", "x"));
    info.put("paymentOptions", Map.of("paymentOptions", Map.of("bitcoin", "bitcoin:1abc")));
    byte[] torrent = Bencode.encode(Map.of("info", info));
    replace(torrent, "1:~".getBytes(StandardCharsets.US_ASCII), new byte[] {'1', ':', (byte) 0xff});

    BTInfoAdditionalMetadataHolder holder =
        new BTInfoAdditionalMetadataHolder(torrent, "Jazz Collection.flac");

    assertNull(holder.getLicenseBroker());
    assertEquals("bitcoin:1abc", holder.getPaymentOptions().bitcoin);
  }

  /** A minimal single-file info dictionary libtorrent accepts. */
  private static Map<String, Object> info() {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("length", 1L);
    info.put("name", "Jazz Collection.flac");
    info.put("piece length", 16384L);
    // Any non-zero SHA-1: libtorrent refuses to write an all-zero piece hash back out.
    info.put("pieces", "0123456789abcdefghij".getBytes(StandardCharsets.US_ASCII));
    return info;
  }

  /** Bencodes the torrent and reads it back through libtorrent, as a transfer does. */
  private static BTInfoAdditionalMetadataHolder read(Map<String, Object> info) {
    byte[] torrent = Bencode.encode(Map.of("info", info));
    return new BTInfoAdditionalMetadataHolder(torrent, "Jazz Collection.flac");
  }

  /** Overwrites the only occurrence of {@code from} in {@code bytes} with {@code to}. */
  private static void replace(byte[] bytes, byte[] from, byte[] to) {
    for (int i = 0; i + from.length <= bytes.length; i++) {
      if (Arrays.equals(bytes, i, i + from.length, from, 0, from.length)) {
        System.arraycopy(to, 0, bytes, i, to.length);
        return;
      }
    }
    throw new IllegalArgumentException("not found");
  }
}
