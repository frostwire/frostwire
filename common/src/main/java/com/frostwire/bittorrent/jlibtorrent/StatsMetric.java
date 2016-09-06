package com.frostwire.bittorrent.jlibtorrent;

import com.frostwire.jlibtorrent.LibTorrent;
import com.frostwire.jlibtorrent.swig.stats_metric;

/**
 * Describes one statistics metric from the session.
 *
 * @author gubatron
 * @author aldenml
 */
public final class StatsMetric {

    public static final String NET_SENT_PAYLOAD_BYTES_COUNTER_NAME = "net.sent_payload_bytes";
    public static final String NET_SENT_BYTES_COUNTER_NAME = "net.sent_bytes";
    public static final String NET_SENT_IP_OVERHEAD_BYTES_COUNTER_NAME = "net.sent_ip_overhead_bytes";
    public static final String NET_RECV_PAYLOAD_BYTES_COUNTER_NAME = "net.recv_payload_bytes";
    public static final String NET_RECV_BYTES_COUNTER_NAME = "net.recv_bytes";
    public static final String NET_RECV_IP_OVERHEAD_BYTES_COUNTER_NAME = "net.recv_ip_overhead_bytes";

    public static final int NET_SENT_PAYLOAD_BYTES_COUNTER_INDEX = LibTorrent.findMetricIdx(NET_SENT_PAYLOAD_BYTES_COUNTER_NAME);
    public static final int NET_SENT_BYTES_COUNTER_INDEX = LibTorrent.findMetricIdx(NET_SENT_BYTES_COUNTER_NAME);
    public static final int NET_SENT_IP_OVERHEAD_BYTES_COUNTER_INDEX = LibTorrent.findMetricIdx(NET_SENT_IP_OVERHEAD_BYTES_COUNTER_NAME);
    public static final int NET_RECV_PAYLOAD_BYTES_COUNTER_INDEX = LibTorrent.findMetricIdx(NET_RECV_PAYLOAD_BYTES_COUNTER_NAME);
    public static final int NET_RECV_BYTES_COUNTER_INDEX = LibTorrent.findMetricIdx(NET_RECV_BYTES_COUNTER_NAME);
    public static final int NET_RECV_IP_OVERHEAD_BYTES_COUNTER_INDEX = LibTorrent.findMetricIdx(NET_RECV_IP_OVERHEAD_BYTES_COUNTER_NAME);

    public static final String DHT_NODES_GAUGE_NAME = "dht.dht_nodes";
    public static final int DHT_NODES_GAUGE_INDEX = LibTorrent.findMetricIdx(DHT_NODES_GAUGE_NAME);

    public static final int TYPE_COUNTER = stats_metric.type_counter;
    public static final int TYPE_GAUGE = stats_metric.type_gauge;

    StatsMetric(stats_metric sm) {
        this.name = sm.get_name();
        this.valueIndex = sm.getValue_index();
        this.type = sm.getType();
    }

    public final String name;

    public final int valueIndex;

    public final int type;

    @Override
    public String toString() {
        return name + ":" + valueIndex + ":" + typeStr();
    }

    private String typeStr() {
        String str = "unknown";

        if (type == TYPE_COUNTER) {
            str = "counter";
        } else if (type == TYPE_GAUGE) {
            str = "gauge";
        }

        return str;
    }
}
