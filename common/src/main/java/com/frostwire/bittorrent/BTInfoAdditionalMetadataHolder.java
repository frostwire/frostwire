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

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.util.Bencode;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Here to factor out the initialization of additional metadata objects found inside the info map of
 * a torrent download manager.
 *
 * @author gubatron
 */
public class BTInfoAdditionalMetadataHolder {
  private final CopyrightLicenseBroker license;
  private final PaymentOptions paymentOptions;

  public BTInfoAdditionalMetadataHolder(byte[] torrentBytes, String paymentOptionsDisplayName) {
    this(TorrentInfo.bdecode(torrentBytes), paymentOptionsDisplayName);
  }

  public BTInfoAdditionalMetadataHolder(File torrent, String paymentOptionsDisplayName) {
    this(new TorrentInfo(torrent), paymentOptionsDisplayName);
  }

  public BTInfoAdditionalMetadataHolder(TorrentInfo tinfo, String paymentOptionsDisplayName) {
    this(additionalInfo(tinfo), paymentOptionsDisplayName);
  }

  /**
   * Reads the license and payment options FrostWire writes into a torrent's info dictionary.
   *
   * @param info the info dictionary in the form {@link Bencode#decode} returns; only its {@code
   *     license} and {@code paymentOptions} keys are read
   */
  public BTInfoAdditionalMetadataHolder(
      Map<String, Object> info, String paymentOptionsDisplayName) {
    final Object licenseEntry = info.get("license");
    final Object paymentOptionsEntry = info.get("paymentOptions");
    boolean hasLicense = licenseEntry != null;
    boolean hasPaymentOptions = paymentOptionsEntry != null;
    if (hasLicense) {
      license = new CopyrightLicenseBroker(dictionary(licenseEntry));
    } else {
      license = null;
    }
    if (hasPaymentOptions) {
      paymentOptions = new PaymentOptions(dictionary(paymentOptionsEntry));
    } else {
      paymentOptions = new PaymentOptions(null, null);
    }
    paymentOptions.setItemName(paymentOptionsDisplayName);
  }

  /**
   * The info dictionary's {@code license} and {@code paymentOptions} values, re-encoded by
   * libtorrent (so in canonical bencode) and decoded in pure Java, so the parsing above never
   * touches native code.
   */
  private static Map<String, Object> additionalInfo(TorrentInfo tinfo) {
    final Map<String, Entry> info = getInfo(tinfo.toEntry().dictionary().get("info").swig());
    final Map<String, Object> additional = new HashMap<>();
    for (String key : new String[] {"license", "paymentOptions"}) {
      final Entry value = info.get(key);
      if (value != null) {
        additional.put(key, Bencode.decode(value.bencode()));
      }
    }
    return additional;
  }

  /**
   * The value as a dictionary, failing as jlibtorrent's {@code Entry.dictionary()} does: a missing
   * value throws {@link NullPointerException}, any other type {@link IllegalArgumentException}.
   */
  static Map<String, Object> dictionary(Object value) {
    Map<String, Object> dict = Bencode.asDictionary(Objects.requireNonNull(value));
    if (dict == null) {
      throw new IllegalArgumentException("not a dictionary");
    }
    return dict;
  }

  /**
   * A byte string's text, failing as jlibtorrent's {@code Entry.string()} does: a missing value
   * throws {@link NullPointerException}, any other type {@link IllegalArgumentException}.
   */
  static String string(Object value) {
    String s = Bencode.asString(Objects.requireNonNull(value));
    if (s == null) {
      throw new IllegalArgumentException("not a byte string");
    }
    return s;
  }

  private static Map<String, Entry> getInfo(entry e) {
    entry.data_type type = e.type();
    if (type == entry.data_type.dictionary_t) {
      return new Entry(e).dictionary();
    } else if (type == entry.data_type.preformatted_t) {
      byte_vector v = e.preformatted_bytes();
      e = entry.bdecode(v);
      return new Entry(e).dictionary();
    }
    return null;
  }

  public CopyrightLicenseBroker getLicenseBroker() {
    return license;
  }

  public PaymentOptions getPaymentOptions() {
    return paymentOptions;
  }
}
