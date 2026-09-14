package com.limegroup.gnutella.gui.tables;

/**
 * Simple comparable holder so that the Transfer table can properly be sorted by the number of seeds.
 *
 * @author gubatron
 */
public class SeedsHolder implements Comparable<SeedsHolder> {
    private final String stringForm;
    private int connected;
    private int seeds;

    public SeedsHolder(String seedsString) {
        stringForm = seedsString;
        String[] split = seedsString.split("/");
        try {
            connected = Integer.parseInt(split[0].trim());
            seeds = Integer.parseInt(split[1].trim());
        } catch (Exception e) {
            connected = 0;
            seeds = 0;
        }
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
