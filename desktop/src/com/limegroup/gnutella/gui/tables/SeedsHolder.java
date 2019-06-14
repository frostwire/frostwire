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
}
