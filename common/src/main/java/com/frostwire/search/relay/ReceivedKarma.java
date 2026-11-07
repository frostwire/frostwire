/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The karma peers have received, as this node sees it (frostwire/frostwire#1417): an index of the
 * endorsements in every karma chain this node has verified, by endorsee, scored by trust-weighted
 * endorsers. It is a partial, local view (frostwire/frostwire#1416, question 2): an endorsement
 * counts once this node has verified the endorser's chain.
 *
 * <p>An endorsement of peer X by endorser E, for torrent T at epoch t, counts only if
 *
 * <ul>
 *   <li>E's chain verified ({@link KarmaChain#verify}, with the Bitcoin checks where available) and
 *       E is not X;
 *   <li>E's identity has proof of work ({@link IdentityKeys#meetsIdentityDifficulty}), as the rUDP
 *       handshake requires;
 *   <li>X announced T itself (question 3): this node verified a search response X signed that
 *       listed T as X's, or T is in this node's own index when X is this node.
 * </ul>
 *
 * <p>Score: each endorser gives each endorsee one vote, whatever the number of torrents, worth the
 * freshness of its newest counting endorsement, {@code 0.5^(age / HALF_LIFE_EPOCHS)}. A vote is
 * weighted by the endorser's trust {@code w(E)}:
 *
 * <pre>
 *   T(self) = 1;  T(X) = min(1, sum over endorsers E of X: T(E) * DECAY * vote(E, X)),
 *                          to {@link RelayConstants#WOT_MAX_DEPTH} hops from this node
 *   w(self) = 1;  w(E) = max(T(E), STRANGER_WEIGHT if E was first seen MIN_ENDORSER_AGE_EPOCHS
 *                                  epochs ago, else 0)
 *   received(X) = sum over endorsers E of X: w(E) * vote(E, X)
 * </pre>
 *
 * <p>Trust starts at this node and flows only along endorsements: peers it endorsed (after a
 * download), the peers they endorsed, and so on, halving per hop. A cluster of identities that only
 * endorse each other gets no trust, and each of them counts at most {@link #STRANGER_WEIGHT}, and
 * only after this node has known it for {@link #MIN_ENDORSER_AGE_EPOCHS} epochs. The age is
 * witnessed, not read from the chain: an owner can sign commitments at any past height, so a
 * chain's own epochs prove nothing about when it was made. First sightings are kept across restarts
 * in the file given to {@link #persistTo(File)}.
 *
 * <p>Time is the newest epoch of this node's own chain, which commits every epoch (before it has
 * one, the newest epoch indexed), so scoring needs no network. Bounded: {@link #MAX_ENDORSERS}
 * chains, {@link #MAX_ENDORSEMENTS_PER_ENDORSER} endorsements each, {@link #MAX_ANNOUNCED_PEERS}
 * peers' last {@link #MAX_ANNOUNCED_PER_PEER} announcements. Thread-safe; scores are recomputed
 * once per change, on the first read after it.
 */
public final class ReceivedKarma {

  private static final Logger LOG = Logger.getLogger(ReceivedKarma.class);

  /** Weight of a vote from an endorser this node has no trust path to. */
  public static final double STRANGER_WEIGHT = 0.1;

  /** Epochs (about days) this node must have known a stranger before its votes count. */
  public static final int MIN_ENDORSER_AGE_EPOCHS = 7;

  /** Epochs (about days) after which an endorsement's vote is worth half. */
  public static final int HALF_LIFE_EPOCHS = 30;

  /** Trust decay per hop, as the Peer Directory's web of trust. */
  public static final double DECAY = PeerDirectory.DECAY;

  static final int MAX_ENDORSERS = 4096;
  static final int MAX_ENDORSEMENTS_PER_ENDORSER = 64;
  static final int MAX_ANNOUNCED_PEERS = 4096;
  static final int MAX_ANNOUNCED_PER_PEER = 256;
  static final int MAX_FIRST_SEEN = 4 * MAX_ENDORSERS;

  private static final long SAVE_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(10);

  // All guarded by this.
  private final Map<String, List<Endorsement>> endorsements = lru(MAX_ENDORSERS);
  private final Map<String, Set<String>> announced = lru(MAX_ANNOUNCED_PEERS);
  private final Map<String, Long> firstSeen = new LinkedHashMap<>();
  private String self;
  private Predicate<byte[]> selfAnnounced = infoHash -> false;
  private long selfEpoch = -1;
  private long othersEpoch = -1;
  private long version;
  private long scoredVersion = -1;
  private long scoredNow = -1;
  private LongSupplier clock;
  private Map<String, Double> scores = Collections.emptyMap();
  private File firstSeenFile;
  private boolean firstSeenDirty;
  private long lastSaveNanos = System.nanoTime();

  /**
   * Sets this node's identity: its votes count fully, and endorsements of it count for torrents
   * {@code announced} reports in its own index. {@code announced} is called on the threads that
   * index chains (workers), never on a reader's.
   */
  public synchronized void setSelf(byte[] selfPub, Predicate<byte[]> announced) {
    this.self = selfPub == null ? null : Hex.encode(selfPub);
    this.selfAnnounced = announced == null ? infoHash -> false : announced;
    version++;
  }

  /**
   * Keeps first sightings in {@code file}, loading those already there. Call once, off the UI
   * thread; a missing or unreadable file starts empty.
   */
  public synchronized void persistTo(File file) {
    firstSeenFile = file;
    if (file == null || !file.isFile()) {
      return;
    }
    try (BufferedReader in = Files.newBufferedReader(file.toPath(), StandardCharsets.US_ASCII)) {
      String line;
      while ((line = in.readLine()) != null && firstSeen.size() < MAX_FIRST_SEEN) {
        String[] parts = line.trim().split(" ");
        if (parts.length == 2 && parts[0].length() == 64) {
          try {
            Hex.decode(parts[0]);
            firstSeen.putIfAbsent(parts[0], Long.parseLong(parts[1]));
          } catch (IllegalArgumentException ignored) {
            // A damaged line costs that peer its age, nothing more.
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      LOG.warn("Could not read karma first sightings from " + file, e);
    }
    version++;
  }

  /** Writes the first sightings if they changed. Off the UI thread. */
  public void save() {
    File file;
    List<Map.Entry<String, Long>> lines;
    synchronized (this) {
      if (firstSeenFile == null || !firstSeenDirty) {
        return;
      }
      file = firstSeenFile;
      lines = new ArrayList<>(firstSeen.entrySet());
      firstSeenDirty = false;
      lastSaveNanos = System.nanoTime();
    }
    File tmp = new File(file.getPath() + ".tmp");
    try {
      File parent = file.getAbsoluteFile().getParentFile();
      if (parent != null) {
        Files.createDirectories(parent.toPath());
      }
      try (Writer out = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.US_ASCII)) {
        for (Map.Entry<String, Long> e : lines) {
          out.write(e.getKey() + " " + e.getValue() + "\n");
        }
      }
      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      LOG.warn("Could not save karma first sightings to " + file, e);
      synchronized (this) {
        firstSeenDirty = true;
      }
    } finally {
      tmp.delete();
    }
  }

  /**
   * Indexes the endorsements in {@code owner}'s verified chain (or verified tail), replacing
   * nothing older: chains only grow, and a later fetch may hold only newer entries. Ignored for an
   * owner without proof of work (other than this node). Call on a worker thread.
   */
  public void indexChain(byte[] owner, List<KarmaChainEntry> verified) {
    if (owner == null || owner.length != 32 || verified == null || verified.isEmpty()) {
      return;
    }
    String ownerHex = Hex.encode(owner);
    boolean isSelf;
    Predicate<byte[]> announcedBySelf;
    String selfHex;
    synchronized (this) {
      isSelf = ownerHex.equals(self);
      announcedBySelf = selfAnnounced;
      selfHex = self;
    }
    if (!isSelf && !IdentityKeys.meetsIdentityDifficulty(owner)) {
      return;
    }
    List<Endorsement> found = new ArrayList<>();
    List<String> aboutSelf = new ArrayList<>();
    long newest = -1;
    for (KarmaChainEntry e : verified) {
      long epoch = KarmaConstants.epochForHeight(e.blockHeight());
      newest = Math.max(newest, epoch);
      if (e.kind() != KarmaChainEntry.Kind.ENDORSEMENT
          || !Arrays.equals(owner, e.endorserPub())
          || Arrays.equals(owner, e.peerPub())) {
        continue;
      }
      Endorsement en = new Endorsement(Hex.encode(e.peerPub()), Hex.encode(e.infoHash()), epoch);
      found.add(en);
      // The own index is read here, on the worker, so readers never wait on it.
      if (en.endorsee.equals(selfHex) && announcedBySelf.test(e.infoHash())) {
        aboutSelf.add(en.infoHash);
      }
    }
    boolean save;
    synchronized (this) {
      if (isSelf) {
        selfEpoch = Math.max(selfEpoch, newest);
      } else {
        othersEpoch = Math.max(othersEpoch, newest);
      }
      if (!firstSeen.containsKey(ownerHex)) {
        firstSeen.put(ownerHex, now());
        firstSeenDirty = true;
        trimFirstSeen();
      }
      merge(ownerHex, found);
      for (String infoHash : aboutSelf) {
        announce(self, infoHash);
      }
      version++;
      save = firstSeenDirty && System.nanoTime() - lastSaveNanos > SAVE_INTERVAL_NANOS;
    }
    if (save) {
      save();
    }
  }

  /**
   * Records that {@code peer} announced {@code infoHash} as its own, in a search response this node
   * verified {@code peer} signed.
   */
  public synchronized void noteAnnounced(byte[] peer, byte[] infoHash) {
    if (peer == null || peer.length != 32 || infoHash == null || infoHash.length != 20) {
      return;
    }
    if (announce(Hex.encode(peer), Hex.encode(infoHash))) {
      version++;
    }
  }

  /** {@code peer}'s received karma; 0 for a peer nobody this node indexed endorsed. Never waits. */
  public double score(byte[] peer) {
    if (peer == null || peer.length != 32) {
      return 0;
    }
    Double score = scores().get(Hex.encode(peer));
    return score == null ? 0 : score;
  }

  private synchronized Map<String, Double> scores() {
    long now = now();
    if (scoredVersion != version || scoredNow != now) {
      scores = compute();
      scoredVersion = version;
      scoredNow = now;
    }
    return scores;
  }

  /**
   * Reads the current epoch from {@code currentEpoch} (-1 when unknown), e.g. this node's own
   * chain's, which commits every epoch. Must not wait: it is read on every score.
   */
  public synchronized void setClock(LongSupplier currentEpoch) {
    this.clock = currentEpoch;
  }

  /**
   * The current epoch: the clock's ({@link #setClock}), else the newest in this node's own chain,
   * which commits every epoch and was anchored by this node's own Bitcoin view; only without
   * either, the newest in any chain indexed, which a chain verified offline could push into the
   * future.
   */
  private long now() {
    long epoch = clock == null ? -1 : clock.getAsLong();
    if (epoch >= 0) {
      return epoch;
    }
    return selfEpoch >= 0 ? selfEpoch : othersEpoch;
  }

  /** Votes, by endorser then endorsee: the freshness of the newest endorsement that counts. */
  private Map<String, Map<String, Double>> votes() {
    long now = now();
    Map<String, Map<String, Double>> votes = new HashMap<>();
    for (Map.Entry<String, List<Endorsement>> byEndorser : endorsements.entrySet()) {
      for (Endorsement en : byEndorser.getValue()) {
        Set<String> torrents = announced.get(en.endorsee);
        if (torrents == null || !torrents.contains(en.infoHash)) {
          continue;
        }
        double fresh = Math.pow(0.5, Math.max(0, now - en.epoch) / (double) HALF_LIFE_EPOCHS);
        votes
            .computeIfAbsent(byEndorser.getKey(), k -> new HashMap<>())
            .merge(en.endorsee, fresh, Math::max);
      }
    }
    return votes;
  }

  private Map<String, Double> compute() {
    Map<String, Map<String, Double>> votes = votes();
    Map<String, Double> trust = new HashMap<>();
    if (self != null) {
      trust.put(self, 1.0);
      for (int hop = 0; hop < RelayConstants.WOT_MAX_DEPTH; hop++) {
        Map<String, Double> next = new HashMap<>();
        next.put(self, 1.0);
        for (Map.Entry<String, Double> endorser : trust.entrySet()) {
          Map<String, Double> theirs = votes.get(endorser.getKey());
          if (theirs == null) {
            continue;
          }
          for (Map.Entry<String, Double> vote : theirs.entrySet()) {
            if (!vote.getKey().equals(self)) {
              next.merge(vote.getKey(), endorser.getValue() * DECAY * vote.getValue(), Double::sum);
            }
          }
        }
        next.replaceAll((peer, t) -> Math.min(1.0, t));
        trust = next;
      }
    }
    Map<String, Double> received = new HashMap<>();
    for (Map.Entry<String, Map<String, Double>> byEndorser : votes.entrySet()) {
      double weight = weight(byEndorser.getKey(), trust);
      if (weight <= 0) {
        continue;
      }
      for (Map.Entry<String, Double> vote : byEndorser.getValue().entrySet()) {
        received.merge(vote.getKey(), weight * vote.getValue(), Double::sum);
      }
    }
    return received;
  }

  private double weight(String endorser, Map<String, Double> trust) {
    if (endorser.equals(self)) {
      return 1.0;
    }
    Long seen = firstSeen.get(endorser);
    double stranger = seen != null && now() - seen >= MIN_ENDORSER_AGE_EPOCHS ? STRANGER_WEIGHT : 0;
    return Math.max(trust.getOrDefault(endorser, 0.0), stranger);
  }

  private void merge(String owner, List<Endorsement> found) {
    Map<String, Endorsement> byTorrent = new LinkedHashMap<>();
    List<Endorsement> known = endorsements.remove(owner);
    if (known != null) {
      for (Endorsement en : known) {
        byTorrent.put(en.key(), en);
      }
    }
    for (Endorsement en : found) {
      byTorrent.merge(en.key(), en, (a, b) -> a.epoch >= b.epoch ? a : b);
    }
    List<Endorsement> kept = new ArrayList<>(byTorrent.values());
    kept.sort((a, b) -> Long.compare(b.epoch, a.epoch));
    if (kept.size() > MAX_ENDORSEMENTS_PER_ENDORSER) {
      kept = new ArrayList<>(kept.subList(0, MAX_ENDORSEMENTS_PER_ENDORSER));
    }
    endorsements.put(owner, kept);
  }

  /** True if it is new. */
  private boolean announce(String peer, String infoHash) {
    Set<String> torrents = announced.computeIfAbsent(peer, k -> new LinkedHashSet<>());
    if (!torrents.add(infoHash)) {
      return false;
    }
    if (torrents.size() > MAX_ANNOUNCED_PER_PEER) {
      Iterator<String> oldest = torrents.iterator();
      oldest.next();
      oldest.remove();
    }
    return true;
  }

  /** Forgets the sightings seen longest ago, in insertion order. */
  private void trimFirstSeen() {
    Iterator<String> it = firstSeen.keySet().iterator();
    while (firstSeen.size() > MAX_FIRST_SEEN && it.hasNext()) {
      it.next();
      it.remove();
    }
  }

  private static <V> Map<String, V> lru(int max) {
    return new LinkedHashMap<String, V>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
        return size() > max;
      }
    };
  }

  private static final class Endorsement {
    final String endorsee;
    final String infoHash;
    final long epoch;

    Endorsement(String endorsee, String infoHash, long epoch) {
      this.endorsee = endorsee;
      this.infoHash = infoHash;
      this.epoch = epoch;
    }

    String key() {
      return endorsee + infoHash;
    }
  }
}
