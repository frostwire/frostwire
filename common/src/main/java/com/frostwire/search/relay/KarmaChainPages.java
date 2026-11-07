/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Bencode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * The published form of a karma chain since frostwire/frostwire#1416 (manifest version 2): the
 * newest entries split into pages of at most {@link #MAX_PAGE_BYTES}, each a BEP 46 mutable item
 * under the owner's key. Page 0, the head, is under {@link KarmaConstants#BEP46_SALT_KARMA} as the
 * version 1 manifest was; older page {@code n} is under {@code frostwire-karma-v1/<n>}.
 *
 * <p>Page (bencoded; byte strings are raw bytes, not hex or base64):
 *
 * <pre>
 * { "v": 2, "seq": seq of the page's first entry, "ph": prevHash of that entry (32 bytes),
 *   "e": [ entry, ... ],
 *   head only: "len": chain length, "head": entryHash of the last entry (32), "pg": older pages }
 * entry: { "k": "EC" | "EN", "bh": block height, "bk": block hash (32), "s": signature (64),
 *          EC: "en": energy ("%.2f", as signed), "fv": 2 (absent in format 1),
 *          EN: "pp": endorsed peer (32), "ih": info hash (20), "sd": delta (absent when 1) }
 * </pre>
 *
 * <p>Everything a version 1 entry repeated is derived instead: the owner is the key the item is
 * stored under, each entry's seq and prevHash follow from the page's and the previous entry's, and
 * an epoch is its height's. The signatures are over the same canonical form as before, so this is
 * only a new encoding of the same signed entries. A version 1 manifest held about one commitment
 * and one endorsement; a page holds three or four endorsements (about 196 bytes each) or about six
 * commitments (about 147 bytes each).
 *
 * <p>The head holds the longest run of newest entries that fits, starting at an epoch commitment
 * when one is in that run, so it verifies on its own. Older pages hold the entries before it, up to
 * {@link #MAX_OLDER_PAGES}. Page boundaries move as the chain grows; every page is published again
 * each time, and a reader that gets pages from two publications stops at the first one that doesn't
 * link to the page after it.
 */
public final class KarmaChainPages {

  /** The manifest version of this form; version 1 is {@link KarmaChain#parseManifest}'s. */
  public static final int VERSION = 2;

  /** BEP 44 mutable item value size limit (conservative), per page. */
  public static final int MAX_PAGE_BYTES = 950;

  /** Older pages published besides the head, and the most a reader fetches. */
  public static final int MAX_OLDER_PAGES = 7;

  /** Most entries a reader accepts in one page; a page that fits has far fewer. */
  static final int MAX_PAGE_ENTRIES = 32;

  private static final byte[] NO_HASH = new byte[32];

  private KarmaChainPages() {}

  /** The salt of page {@code page}: the head is 0, older pages count back from 1. */
  public static byte[] salt(int page) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    String salt =
        page == 0 ? KarmaConstants.BEP46_SALT_KARMA : KarmaConstants.BEP46_SALT_KARMA + "/" + page;
    return salt.getBytes(StandardCharsets.US_ASCII);
  }

  /**
   * The bencoded pages of {@code chain}, head first, then older pages back to {@link
   * #MAX_OLDER_PAGES} or the chain's start. Empty for an empty chain.
   */
  public static List<byte[]> paginate(List<KarmaChainEntry> chain) {
    if (chain == null || chain.isEmpty()) {
      return Collections.emptyList();
    }
    int end = chain.size();
    int start = longestFit(chain, end, true);
    if (start < 0) {
      return Collections.emptyList();
    }
    // A head that starts at a commitment (or the genesis) verifies without the older pages.
    if (start > 0 && chain.get(start).kind() != KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
      for (int i = start + 1; i < end; i++) {
        if (chain.get(i).kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
          start = i;
          break;
        }
      }
    }
    List<byte[]> older = new ArrayList<>();
    int olderEnd = start;
    while (olderEnd > 0 && older.size() < MAX_OLDER_PAGES) {
      int olderStart = longestFit(chain, olderEnd, false);
      if (olderStart < 0) {
        break;
      }
      older.add(Bencode.encode(page(chain, olderStart, olderEnd, -1)));
      olderEnd = olderStart;
    }
    List<byte[]> pages = new ArrayList<>(older.size() + 1);
    pages.add(Bencode.encode(page(chain, start, end, older.size())));
    pages.addAll(older);
    return pages;
  }

  /**
   * Reads a published chain from its head item: a version 2 head, with its older pages from {@code
   * olderPage} (page number to the item's bencoded value, or null when it couldn't be fetched), or
   * a version 1 manifest ({@link KarmaChain#parseManifest(byte[], byte[], BlockHeaderSource)}).
   *
   * <p>Version 2: the head's {@code len} and {@code head} must describe its last entry. Older pages
   * are added while they link to the page after them; the entries are then verified as a tail from
   * their first epoch commitment (or as the whole chain, from the genesis) with {@link
   * KarmaChain#verifyTail(List, byte[], long, BlockHeaderSource)}, and none may name a block below
   * {@link KarmaConstants#FIRST_KARMA_BLOCK_HEIGHT}.
   *
   * @param head the head item's bencoded value (untrusted)
   * @param owner the 32-byte key the items were fetched under
   * @param olderPage fetches older page {@code n}; called on the caller's thread, newest first
   * @param blocks Bitcoin block source for the block checks, or null for offline checks only
   * @return the verified entries (unmodifiable), or null if nothing in them verifies
   * @throws IllegalArgumentException if {@code head} is not canonical bencode, or a version 1
   *     manifest's hex or base64 field is malformed
   */
  public static List<KarmaChainEntry> read(
      byte[] head, byte[] owner, IntFunction<byte[]> olderPage, BlockHeaderSource blocks) {
    if (head == null
        || head.length > KarmaChain.MAX_REMOTE_MANIFEST_BYTES
        || owner == null
        || owner.length != 32) {
      return null;
    }
    Map<String, Object> dict = Bencode.asDictionary(Bencode.decode(head));
    if (dict == null) {
      return null;
    }
    Long version = Bencode.asLong(dict.get("v"));
    if (version == null || version != VERSION) {
      return KarmaChain.parseManifest(dict, owner, blocks);
    }
    Page headPage = decodePage(dict, owner);
    if (headPage == null || !describesItsLastEntry(dict, headPage)) {
      return null;
    }
    Long declaredOlder = Bencode.asLong(dict.get("pg"));
    long olderPages = declaredOlder == null ? 0 : Math.min(declaredOlder, MAX_OLDER_PAGES);
    if (olderPages < 0) {
      return null;
    }
    Deque<Page> pages = new ArrayDeque<>();
    pages.add(headPage);
    for (int n = 1; n <= olderPages && olderPage != null; n++) {
      Page page = olderPage(olderPage.apply(n), owner);
      if (page == null || !page.linksTo(pages.peekFirst())) {
        break;
      }
      pages.addFirst(page);
    }
    List<KarmaChainEntry> entries = new ArrayList<>();
    for (Page page : pages) {
      entries.addAll(page.entries);
    }
    List<KarmaChainEntry> verified = verifiedTail(entries, blocks);
    // Older pages the owner got wrong don't cost the head's own entries.
    return verified != null || pages.size() == 1
        ? verified
        : verifiedTail(headPage.entries, blocks);
  }

  /**
   * {@code entries} from their first epoch commitment (or all of them, from the genesis), if they
   * verify as a tail; otherwise null.
   */
  private static List<KarmaChainEntry> verifiedTail(
      List<KarmaChainEntry> entries, BlockHeaderSource blocks) {
    int start = 0;
    if (entries.get(0).seq() != 0) {
      while (start < entries.size()
          && entries.get(start).kind() != KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        start++;
      }
      if (start == entries.size()) {
        return null;
      }
    }
    List<KarmaChainEntry> tail = new ArrayList<>(entries.subList(start, entries.size()));
    KarmaChainEntry first = tail.get(0);
    if (!KarmaChain.verifyTail(tail, first.prevHash(), first.seq(), blocks)) {
      return null;
    }
    return Collections.unmodifiableList(tail);
  }

  private static Page olderPage(byte[] bencoded, byte[] owner) {
    if (bencoded == null || bencoded.length > MAX_PAGE_BYTES * 2) {
      return null;
    }
    try {
      Map<String, Object> dict = Bencode.asDictionary(Bencode.decode(bencoded));
      Long version = dict == null ? null : Bencode.asLong(dict.get("v"));
      return version != null && version == VERSION ? decodePage(dict, owner) : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static boolean describesItsLastEntry(Map<String, Object> dict, Page page) {
    Long len = Bencode.asLong(dict.get("len"));
    byte[] headHash = bytes(dict.get("head"), 32);
    KarmaChainEntry last = page.entries.get(page.entries.size() - 1);
    return len != null && len == last.seq() + 1 && Arrays.equals(headHash, last.entryHash());
  }

  /** Start of the longest run ending at {@code end} whose page fits, or -1 if none does. */
  private static int longestFit(List<KarmaChainEntry> chain, int end, boolean head) {
    int start = -1;
    for (int s = end - 1; s >= 0; s--) {
      Map<String, Object> page = page(chain, s, end, head ? MAX_OLDER_PAGES : -1);
      if (Bencode.encode(page).length > MAX_PAGE_BYTES) {
        break;
      }
      start = s;
    }
    return start;
  }

  /** The page of {@code chain[from, to)}; a head when {@code olderPages >= 0}. */
  private static Map<String, Object> page(
      List<KarmaChainEntry> chain, int from, int to, int olderPages) {
    KarmaChainEntry first = chain.get(from);
    List<Object> entries = new ArrayList<>(to - from);
    for (int i = from; i < to; i++) {
      entries.add(entry(chain.get(i)));
    }
    Map<String, Object> page = new TreeMap<>();
    page.put("v", (long) VERSION);
    page.put("seq", first.seq());
    page.put("ph", first.prevHash());
    page.put("e", entries);
    if (olderPages >= 0) {
      KarmaChainEntry last = chain.get(chain.size() - 1);
      page.put("len", last.seq() + 1);
      page.put("head", last.entryHash());
      page.put("pg", (long) olderPages);
    }
    return page;
  }

  private static Map<String, Object> entry(KarmaChainEntry e) {
    Map<String, Object> m = new TreeMap<>();
    m.put("k", e.kind().code());
    m.put("bh", e.blockHeight());
    m.put("bk", e.blockHash());
    m.put("s", e.signature());
    if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
      // Exactly the signed text: re-rounding a longer form could change the second decimal.
      m.put("en", String.format(Locale.ROOT, "%.2f", e.energy()));
      if (e.formatVersion() != KarmaChainEntry.FORMAT_DECAY_PER_COMMITMENT) {
        m.put("fv", (long) e.formatVersion());
      }
    } else {
      m.put("pp", e.peerPub());
      m.put("ih", e.infoHash());
      if (e.scoreDelta() != 1) {
        m.put("sd", (long) e.scoreDelta());
      }
    }
    return m;
  }

  /** The page's entries with their seq and prevHash filled in, or null if it is malformed. */
  private static Page decodePage(Map<String, Object> dict, byte[] owner) {
    Long seq = Bencode.asLong(dict.get("seq"));
    byte[] prevHash = bytes(dict.get("ph"), 32);
    List<Object> list = Bencode.asList(dict.get("e"));
    if (seq == null
        || seq < 0
        || prevHash == null
        || list == null
        || list.isEmpty()
        || list.size() > MAX_PAGE_ENTRIES
        || (seq == 0 && !Arrays.equals(prevHash, NO_HASH))) {
      return null;
    }
    List<KarmaChainEntry> entries = new ArrayList<>(list.size());
    byte[] prev = prevHash;
    for (Object value : list) {
      KarmaChainEntry e =
          decodeEntry(Bencode.asDictionary(value), owner, prev, seq + entries.size());
      if (e == null || e.blockHeight() < KarmaConstants.FIRST_KARMA_BLOCK_HEIGHT) {
        return null;
      }
      entries.add(e);
      prev = e.entryHash();
    }
    return new Page(seq, prevHash, entries);
  }

  private static KarmaChainEntry decodeEntry(
      Map<String, Object> m, byte[] owner, byte[] prevHash, long seq) {
    if (m == null) {
      return null;
    }
    String kind = Bencode.asString(m.get("k"));
    Long height = Bencode.asLong(m.get("bh"));
    byte[] blockHash = bytes(m.get("bk"), 32);
    byte[] signature = bytes(m.get("s"), 64);
    if (kind == null || height == null || height < 0 || blockHash == null || signature == null) {
      return null;
    }
    try {
      if ("EC".equals(kind)) {
        Double energy = energy(Bencode.asString(m.get("en")));
        Object fvValue = m.get("fv");
        Long fv = Bencode.asLong(fvValue);
        if (energy == null
            || m.containsKey("pp")
            || m.containsKey("ih")
            || (fvValue != null && (fv == null || fv != KarmaChainEntry.FORMAT_DECAY_PER_EPOCH))) {
          return null;
        }
        return KarmaChainEntry.fromStoredFields(
            KarmaChainEntry.Kind.EPOCH_COMMITMENT,
            fv == null ? KarmaChainEntry.FORMAT_DECAY_PER_COMMITMENT : fv.intValue(),
            prevHash,
            seq,
            owner,
            0L,
            height,
            blockHash,
            KarmaConstants.epochForHeight(height),
            energy,
            null,
            null,
            null,
            signature);
      }
      if ("EN".equals(kind)) {
        byte[] peer = bytes(m.get("pp"), 32);
        byte[] infoHash = bytes(m.get("ih"), 20);
        Object sdValue = m.get("sd");
        Long sd = Bencode.asLong(sdValue);
        if (peer == null
            || infoHash == null
            || m.containsKey("en")
            || m.containsKey("fv")
            || (sdValue != null
                && (sd == null || sd < Integer.MIN_VALUE || sd > Integer.MAX_VALUE))) {
          return null;
        }
        return KarmaChainEntry.fromStoredFields(
            KarmaChainEntry.Kind.ENDORSEMENT,
            prevHash,
            seq,
            owner,
            0L,
            height,
            blockHash,
            null,
            null,
            peer,
            infoHash,
            sd == null ? 1 : sd.intValue(),
            signature);
      }
      return null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** A finite energy within the cap, or null. NaN fails every range check (#1401). */
  private static Double energy(String text) {
    if (text == null) {
      return null;
    }
    try {
      double energy = Double.parseDouble(text);
      return Double.isFinite(energy) && energy >= 0 && energy <= KarmaConstants.MAX_ENERGY + 0.01
          ? energy
          : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static byte[] bytes(Object value, int length) {
    return value instanceof byte[] && ((byte[]) value).length == length
        ? ((byte[]) value).clone()
        : null;
  }

  private static final class Page {
    final long seq;
    final byte[] prevHash;
    final List<KarmaChainEntry> entries;

    Page(long seq, byte[] prevHash, List<KarmaChainEntry> entries) {
      this.seq = seq;
      this.prevHash = prevHash;
      this.entries = entries;
    }

    /** True when {@code next} continues right after this page's last entry. */
    boolean linksTo(Page next) {
      KarmaChainEntry last = entries.get(entries.size() - 1);
      return seq + entries.size() == next.seq && Arrays.equals(last.entryHash(), next.prevHash);
    }
  }
}
