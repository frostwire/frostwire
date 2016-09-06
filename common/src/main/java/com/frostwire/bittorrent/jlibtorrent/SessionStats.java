package com.frostwire.bittorrent.jlibtorrent;

import com.frostwire.jlibtorrent.alerts.SessionStatsAlert;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SessionStats {

    // these are the channels we keep stats for
    private static final int UPLOAD_PAYLOAD = 0;
    private static final int UPLOAD_PROTOCOL = 1;
    private static final int UPLOAD_IP_PROTOCOL = 2;
    private static final int DOWNLOAD_PAYLOAD = 3;
    private static final int DOWNLOAD_PROTOCOL = 4;
    private static final int DOWNLOAD_IP_PROTOCOL = 5;
    private static final int NUM_AVERAGES = 6;

    private final Average[] stat;

    private long lastTickTime;
    private long dhtNodes;

    SessionStats() {
        this.stat = new Average[NUM_AVERAGES];
        for (int i = 0; i < this.stat.length; i++) {
            this.stat[i] = new Average();
        }
    }

    public long totalDownload() {
        return stat[DOWNLOAD_PAYLOAD].total() +
                stat[DOWNLOAD_PROTOCOL].total() +
                stat[DOWNLOAD_IP_PROTOCOL].total();
    }

    public long totalUpload() {
        return stat[UPLOAD_PAYLOAD].total() +
                stat[UPLOAD_PROTOCOL].total() +
                stat[UPLOAD_IP_PROTOCOL].total();
    }

    public long downloadRate() {
        return stat[DOWNLOAD_PAYLOAD].rate() +
                stat[DOWNLOAD_PROTOCOL].rate() +
                stat[DOWNLOAD_IP_PROTOCOL].rate();
    }

    public long uploadRate() {
        return stat[UPLOAD_PAYLOAD].rate() +
                stat[UPLOAD_PROTOCOL].rate() +
                stat[UPLOAD_IP_PROTOCOL].rate();
    }

    public long dhtNodes() {
        return dhtNodes;
    }

    void update(SessionStatsAlert alert) {
        long now = System.currentTimeMillis();
        long tickIntervalMs = now - lastTickTime;
        lastTickTime = now;

        long received = alert.value(StatsMetric.NET_RECV_BYTES_COUNTER_INDEX);
        long payload = alert.value(StatsMetric.NET_RECV_PAYLOAD_BYTES_COUNTER_INDEX);
        long protocol = received - payload;
        long ip = alert.value(StatsMetric.NET_RECV_IP_OVERHEAD_BYTES_COUNTER_INDEX);

        payload -= stat[DOWNLOAD_PAYLOAD].total();
        protocol -= stat[DOWNLOAD_PROTOCOL].total();
        ip -= stat[DOWNLOAD_IP_PROTOCOL].total();
        stat[DOWNLOAD_PAYLOAD].add(payload);
        stat[DOWNLOAD_PROTOCOL].add(protocol);
        stat[DOWNLOAD_IP_PROTOCOL].add(ip);

        long sent = alert.value(StatsMetric.NET_SENT_BYTES_COUNTER_INDEX);
        payload = alert.value(StatsMetric.NET_SENT_PAYLOAD_BYTES_COUNTER_INDEX);
        protocol = sent - payload;
        ip = alert.value(StatsMetric.NET_SENT_IP_OVERHEAD_BYTES_COUNTER_INDEX);

        payload -= stat[UPLOAD_PAYLOAD].total();
        protocol -= stat[UPLOAD_PROTOCOL].total();
        ip -= stat[UPLOAD_IP_PROTOCOL].total();
        stat[UPLOAD_PAYLOAD].add(payload);
        stat[UPLOAD_PROTOCOL].add(protocol);
        stat[UPLOAD_IP_PROTOCOL].add(ip);

        tick(tickIntervalMs);
        dhtNodes = alert.value(StatsMetric.DHT_NODES_GAUGE_INDEX);
    }

    void clear() {
        for (int i = 0; i < NUM_AVERAGES; ++i) {
            stat[i].clear();
        }
        dhtNodes = 0;
    }

    // should be called once every second
    private void tick(long tickIntervalMs) {
        for (int i = 0; i < NUM_AVERAGES; ++i) {
            stat[i].tick(tickIntervalMs);
        }
    }

    private static final class Average {

        // total counters
        private long totalCounter;

        // the accumulator for this second.
        private long counter;

        // sliding average
        private long averageSec5;

        public Average() {
        }

        public void add(long count) {
            counter += count;
            totalCounter += count;
        }

        // should be called once every second
        public void tick(long tickIntervalMs) {
            if (tickIntervalMs >= 1) {
                long sample = (counter * 1000) / tickIntervalMs;
                averageSec5 = (averageSec5 * 4) / 5 + sample / 5;
                counter = 0;
            }
        }

        public long rate() {
            return averageSec5;
        }

        public long total() {
            return totalCounter;
        }

        public void clear() {
            counter = 0;
            averageSec5 = 0;
            totalCounter = 0;
        }
    }
}
