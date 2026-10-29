package com.limegroup.gnutella.gui.tables;

/**
 * Simple comparable holder so that the Transfer table can properly be sorted by the number of
 * seeds.
 *
 * @author gubatron
 */
public class SeedsHolder implements Comparable<SeedsHolder> {
  private final String stringForm;
  private final int connected;
  private final int seeds;

  /**
   * Parses "connected/total" without throwing: HTTP downloads and torrents still fetching metadata
   * report "", and an exception per row and paint walked the whole paint stack on the EDT
   * (frostwire/frostwire#1380). Anything else than two plain counts is 0/0.
   */
  public SeedsHolder(String seedsString) {
    stringForm = seedsString;
    int slash = seedsString == null ? -1 : seedsString.indexOf('/');
    int parsedConnected = slash < 0 ? -1 : parseCount(seedsString.substring(0, slash));
    int parsedSeeds = slash < 0 ? -1 : parseCount(seedsString.substring(slash + 1));
    if (parsedConnected < 0 || parsedSeeds < 0) {
      parsedConnected = 0;
      parsedSeeds = 0;
    }
    connected = parsedConnected;
    seeds = parsedSeeds;
  }

  /** A non-negative count of at most 9 digits, surrounding blanks allowed; -1 otherwise. */
  static int parseCount(String text) {
    String digits = text.trim();
    if (digits.isEmpty() || digits.length() > 9) {
      return -1;
    }
    int value = 0;
    for (int i = 0; i < digits.length(); i++) {
      char c = digits.charAt(i);
      if (c < '0' || c > '9') {
        return -1;
      }
      value = value * 10 + (c - '0');
    }
    return value;
  }

  @Override
  public String toString() {
    return stringForm;
  }

  @Override
  public int compareTo(SeedsHolder other) {
    return (connected + seeds) - (other.connected + other.seeds);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SeedsHolder)) {
      return false;
    }
    SeedsHolder other = (SeedsHolder) o;
    return connected == other.connected && seeds == other.seeds;
  }

  @Override
  public int hashCode() {
    int result = Integer.hashCode(connected);
    result = 31 * result + Integer.hashCode(seeds);
    return result;
  }
}
