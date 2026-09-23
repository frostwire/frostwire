/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.relay.icebridge.sim.LoopbackNetworkSimulator.NodeStats;
import com.frostwire.search.relay.icebridge.sim.LoopbackNetworkSimulator.Report;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** CLI for the bounded real-network scenario; never included in desktop client artifacts. */
public final class LoopbackSimulationMain {

  private LoopbackSimulationMain() {}

  public static void main(String[] args) throws Exception {
    int nodes = 3;
    for (String arg : args) {
      if (arg.startsWith("--nodes=")) {
        nodes = Integer.parseInt(arg.substring("--nodes=".length()));
      } else {
        throw new IllegalArgumentException("Unknown loopback option: " + arg);
      }
    }
    if (nodes < 2 || nodes > 20) {
      throw new IllegalArgumentException("loopback nodes must be between 2 and 20");
    }
    Path output = Path.of("build", "reports", "icebridge-loopback");
    Files.createDirectories(output);
    Path state = Files.createTempDirectory("icebridge-loopback-");
    System.out.println(
        "Starting "
            + nodes
            + " real IceBridge stacks on 127.0.0.1 (distinct control and UDP ports)");
    Report report;
    try {
      report = new LoopbackNetworkSimulator().run(nodes, state);
    } finally {
      try (Stream<Path> files = Files.walk(state)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
          Files.deleteIfExists(file);
        }
      }
    }
    if (report.findableHits() != 1 || report.unfindableHits() != 0 || !report.uniquePorts()) {
      throw new IllegalStateException("Loopback search or port invariant failed: " + report);
    }
    String stamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path html;
    Path json;
    int suffix = 1;
    do {
      String base = "icebridge-loopback-" + stamp + (suffix == 1 ? "" : "-" + suffix);
      html = output.resolve(base + ".html");
      json = output.resolve(base + ".json");
      suffix++;
    } while (Files.exists(html) || Files.exists(json));
    Files.writeString(
        json,
        new GsonBuilder().setPrettyPrinting().create().toJson(report),
        StandardCharsets.UTF_8);
    Files.writeString(html, html(report), StandardCharsets.UTF_8);
    Files.writeString(
        output.resolve("latest-report.txt"), html.toAbsolutePath() + System.lineSeparator());
    System.out.printf(
        Locale.US,
        "WIRE_RESULT nodes=%d remoteHits=%d misses=%d findable=%dms miss=%dms udpIn=%d udpOut=%d bytesOut=%d%n",
        report.nodes(),
        report.findableHits(),
        report.unfindableHits(),
        report.findableMillis(),
        report.unfindableMillis(),
        report.packetsIn(),
        report.packetsOut(),
        report.bytesOut());
    System.out.println("WIRE_HTML_REPORT=" + html.toAbsolutePath());
    System.out.println("WIRE_JSON_BENCHMARK=" + json.toAbsolutePath());
  }

  static String html(Report report) {
    StringBuilder out = new StringBuilder();
    out.append(
            "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>IceBridge Loopback Wire Benchmark</title>")
        .append(
            "<style>body{font:16px system-ui;background:#07110f;color:#edf8f3;max-width:1000px;margin:40px auto;padding:20px}")
        .append(
            "h1,h2{color:#4ee6a8}table{width:100%;border-collapse:collapse}td,th{padding:12px;border-bottom:1px solid #23443a;text-align:left}")
        .append(
            ".note{color:#f6c453}strong{color:#4ee6a8}</style><h1>IceBridge Loopback Wire Benchmark</h1>")
        .append(
            "<p class=\"note\">Real signed direct searches (TTL=1) on 127.0.0.1: independently bound IceBridge servers, HTTP control, rUDP, production request parsing and handlers, SQLite index, signed response verification. ")
        .append("This bounded ")
        .append(report.nodes())
        .append(
            "-node run is not the 1,020-node message model or 1,020 separate FrostWire processes.</p>")
        .append("<h2>End-to-end results</h2><table><tbody>")
        .append(row("Remote findable results", report.findableHits()))
        .append(row("Non-findable results", report.unfindableHits()))
        .append(row("Findable search wall time (ms)", report.findableMillis()))
        .append(row("Miss search wall time (ms)", report.unfindableMillis()))
        .append(row("Search deadline (ms)", 10_000))
        .append(row("Real UDP packets received", report.packetsIn()))
        .append(row("Real UDP packets sent", report.packetsOut()))
        .append(row("Real UDP bytes sent", report.bytesOut()))
        .append(row("Holder digests received", report.digestsKnown()));
    if (report.findableMillis() >= 10_000 || report.unfindableMillis() >= 10_000) {
      out.append(
          "</tbody></table><p class=\"note\">At least one search exhausted its deadline; the hit/miss outcome does not prove that every peer answered in time.</p>");
    } else {
      out.append("</tbody></table>");
    }
    out.append("<h2>Per-node sockets and counters</h2><table><thead><tr>")
        .append(
            "<th>Node</th><th>Control TCP</th><th>rUDP</th><th>Packets in</th><th>Packets out</th><th>Bytes out</th></tr></thead><tbody>");
    for (NodeStats node : report.nodeStats()) {
      out.append("<tr><td>")
          .append(node.id())
          .append("</td><td>")
          .append(node.controlPort())
          .append("</td><td>")
          .append(node.udpPort())
          .append("</td><td>")
          .append(node.packetsIn())
          .append("</td><td>")
          .append(node.packetsOut())
          .append("</td><td>")
          .append(node.bytesOut())
          .append("</td></tr>");
    }
    return out.append(
            "</tbody></table><h2>Measurement boundary</h2><p>Separate protocol stacks in one JVM, with simulated discovery and no internet traffic. TTL=1 tests direct request/response completion; MultiRelayMeshSearchTest covers forwarding. The companion model tests large topology traffic economics. This small sample is a functional smoke benchmark, not a capacity or p95 result.</p></html>")
        .toString();
  }

  private static String row(String label, long value) {
    return "<tr><th>" + label + "</th><td><strong>" + value + "</strong></td></tr>";
  }
}
