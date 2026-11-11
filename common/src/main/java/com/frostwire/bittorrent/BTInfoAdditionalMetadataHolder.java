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
    // Both sections are optional and written by whoever made the torrent: one that isn't a
    // dictionary reads as absent, and doesn't cost the other one.
    final Map<String, Object> licenseEntry = Bencode.asDictionary(info.get("license"));
    final Map<String, Object> paymentOptionsEntry =
        Bencode.asDictionary(info.get("paymentOptions"));
    boolean hasLicense = licenseEntry != null;
    boolean hasPaymentOptions = paymentOptionsEntry != null;
    if (hasLicense) {
      license = new CopyrightLicenseBroker(licenseEntry);
    } else {
      license = null;
    }
    if (hasPaymentOptions) {
      paymentOptions = new PaymentOptions(paymentOptionsEntry);
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
    // Decode the written torrent again: with libtorrent 2.1 the "info" of toEntry()'s result is not
    // a dictionary the swig layer can read (its type is undefined_t), while the bytes decode fine.
    final Map<String, Entry> info =
        getInfo(Entry.bdecode(tinfo.bencode()).dictionary().get("info").swig());
    final Map<String, Object> additional = new HashMap<>();
    for (String key : new String[] {"license", "paymentOptions"}) {
      final Entry value = info.get(key);
      if (value == null) {
        continue;
      }
      try {
        additional.put(key, Bencode.decode(value.bencode()));
      } catch (IllegalArgumentException notCanonical) {
        // libtorrent takes what Bencode refuses (a non-UTF-8 key, an over-limit string): a
        // section we can't read is as good as absent.
      }
    }
    return additional;
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
