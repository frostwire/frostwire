/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Locale;

/**
 * A peer's karma as FrostWire shows it ({@link PeerDirectory#displayKarma(byte[])}), with the text
 * every karma indicator prints for it, so search results, the Peer Directory and the identity
 * screens show the same number. Immutable; sorts by value.
 */
public final class DisplayKarma implements Comparable<DisplayKarma> {

  private final double value;
  private final String text;

  private DisplayKarma(double value) {
    this.value = value;
    this.text = String.format(Locale.getDefault(), "%.2f", value);
  }

  /** A non-finite value shows as 0. */
  public static DisplayKarma of(double value) {
    return new DisplayKarma(Double.isFinite(value) ? value : 0);
  }

  /**
   * The karma {@code directory} gives {@code peerPub}, or null when there is no directory (the
   * distributed search stack is not running). Walks the trust graph: call it off the UI thread.
   */
  public static DisplayKarma forPeer(PeerDirectory directory, byte[] peerPub) {
    if (directory == null || peerPub == null) {
      return null;
    }
    return of(directory.displayKarma(peerPub));
  }

  public double value() {
    return value;
  }

  public String text() {
    return text;
  }

  @Override
  public int compareTo(DisplayKarma o) {
    return Double.compare(value, o.value);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof DisplayKarma && Double.compare(value, ((DisplayKarma) o).value) == 0;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(value);
  }

  @Override
  public String toString() {
    return text;
  }
}
