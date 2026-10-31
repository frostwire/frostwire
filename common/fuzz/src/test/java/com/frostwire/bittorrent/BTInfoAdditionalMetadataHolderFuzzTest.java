/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.util.Bencode;
import java.util.Map;

/**
 * Fuzzes {@link BTInfoAdditionalMetadataHolder} reading the license ({@link
 * CopyrightLicenseBroker}) and payment options ({@link PaymentOptions}) out of a torrent's info
 * dictionary.
 *
 * <p>Who sends it: whoever made the .torrent. Desktop reads it for every transfer and for every
 * .torrent file shown in the library, and Android for every transfer; the torrent comes from a
 * search result, a magnet's peers or a file the user opened. In production libtorrent decodes the
 * .torrent and re-encodes the two keys, so they arrive as canonical bencode; the harness decodes
 * the input as the info dictionary with {@link Bencode#decodeDictionary} and skips what that
 * rejects (documented: {@code IllegalArgumentException}), then hands it to the pure-Java
 * constructor. No native code runs.
 *
 * <p>The constructor documents no exception: a torrent's license and payment options are optional.
 * A finding is anything thrown, an input taking seconds, or null payment options (the holder always
 * has them, empty when the torrent has none).
 */
class BTInfoAdditionalMetadataHolderFuzzTest {

  @FuzzTest
  void fromInfo(FuzzedDataProvider data) {
    Map<String, Object> info;
    try {
      info = Bencode.decodeDictionary(data.consumeRemainingAsBytes());
    } catch (IllegalArgumentException notCanonicalBencode) {
      return;
    }
    BTInfoAdditionalMetadataHolder holder = new BTInfoAdditionalMetadataHolder(info, "fuzz");
    if (holder.getPaymentOptions() == null) {
      throw new IllegalStateException("null payment options");
    }
  }
}
