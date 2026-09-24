/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.HealthBudgets;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.HubStats;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkHealthReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.WorkloadConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Writes persistent machine-readable benchmarks and a self-contained HTML analysis report. */
public final class SimulationReportWriter {

  private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final DateTimeFormatter DISPLAY_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  public record ReportArtifacts(Path html, Path json) {}

  public ReportArtifacts write(
      NetworkHealthReport report, Path outputDirectory, ZonedDateTime timestamp)
      throws IOException {
    Files.createDirectories(outputDirectory);
    List<Path> previousFiles = benchmarkFiles(outputDirectory);
    Path previousFile =
        previousFiles.isEmpty() ? null : previousFiles.get(previousFiles.size() - 1);
    JsonObject previous = previousFile == null ? null : read(previousFile);
    String stamp = FILE_TIME.format(timestamp);
    Path json;
    Path html;
    int sequence = 1;
    while (true) {
      String base =
          "icebridge-simulation-"
              + stamp
              + (sequence == 1 ? "" : String.format(Locale.ROOT, "-%04d", sequence));
      json = outputDirectory.resolve(base + ".json");
      html = outputDirectory.resolve(base + ".html");
      if (Files.exists(html)) {
        sequence++;
        continue;
      }
      try {
        Files.createFile(json);
        break;
      } catch (FileAlreadyExistsException collision) {
        sequence++;
      }
    }
    JsonObject benchmark = benchmark(report, timestamp);
    Files.writeString(json, GSON.toJson(benchmark), StandardCharsets.UTF_8);
    List<JsonObject> history = new ArrayList<>();
    for (Path file : previousFiles) {
      history.add(read(file));
    }
    history.add(benchmark);
    Files.writeString(
        html,
        html(report, timestamp, json, previousFile, previous, benchmark, history),
        StandardCharsets.UTF_8);
    Files.writeString(
        outputDirectory.resolve("latest-report.txt"),
        html.toAbsolutePath() + System.lineSeparator(),
        StandardCharsets.UTF_8);
    return new ReportArtifacts(html, json);
  }

  private static JsonObject benchmark(NetworkHealthReport report, ZonedDateTime timestamp) {
    JsonObject root = new JsonObject();
    root.addProperty("timestamp", timestamp.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
    root.addProperty("gitCommit", git("rev-parse", "--short", "HEAD"));
    root.addProperty("gitDirty", !git("status", "--porcelain").isBlank());
    root.addProperty("host", host());
    root.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
    root.addProperty("architecture", System.getProperty("os.arch"));
    root.addProperty("java", System.getProperty("java.version"));
    root.addProperty("processors", Runtime.getRuntime().availableProcessors());
    root.add("report", GSON.toJsonTree(report));
    return root;
  }

  private static String html(
      NetworkHealthReport report,
      ZonedDateTime timestamp,
      Path json,
      Path previousFile,
      JsonObject previous,
      JsonObject current,
      List<JsonObject> history) {
    HealthBudgets budgets = new HealthBudgets();
    StringBuilder out = new StringBuilder(48_000);
    out.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        .append("<title>IceBridge Network Benchmark</title><style>")
        .append(css())
        .append("</style></head><body><main>")
        .append("<header><div><p class=\"eyebrow\">FROSTWIRE LABS / DISTRIBUTED SEARCH</p>")
        .append("<h1>IceBridge Network Benchmark</h1><p class=\"subtitle\">")
        .append(escape(timestamp.format(DISPLAY_TIME)))
        .append(" · deterministic seed ")
        .append(report.config.seed)
        .append(" · ")
        .append(report.meets(budgets) ? "All health budgets passed" : "Health budget failure")
        .append("</p></div><div class=\"score ")
        .append(report.meets(budgets) ? "pass" : "fail")
        .append("\"><span>HEALTH SCORE</span><strong>")
        .append(f3(report.healthScore))
        .append("</strong></div></header>")
        .append("<section class=\"grid metrics\">")
        .append(metric("Findable recall", percent(report.findableHitRate), "target ≥ 85%"))
        .append(metric("Miss p95", f0(report.nonFindableP95Messages), "messages · target ≤ 900"))
        .append(metric("Flood admitted", percent(report.floodAdmissionRatio), "target ≤ 35%"))
        .append(metric("Amplification", f3(report.amplificationRatio) + "×", "flood / honest"))
        .append(
            metric("Duplicate delivery", percent(report.duplicateDeliveryRatio), "leaf requests"))
        .append(
            metric(
                "Compute time",
                report.durationMillis + " ms",
                "UI wait excluded; tracing included"))
        .append("</section>")
        .append(section("SLO gate", sloTable(report, budgets)))
        .append(section("Traffic profile", traffic(report)))
        .append(section("Network topology", topology(report)))
        .append(section("Tuning insights", insights(report)))
        .append(
            section(
                "Previous benchmark comparison",
                comparison(report, previousFile, previous, current)))
        .append(section("Benchmark history", history(history, current)))
        .append(section("Reproducibility", reproducibility(report, timestamp, json)))
        .append(section("Model boundaries", limitations()))
        .append(
            "<footer>Generated by IceBridgeSimulationMain · self-contained HTML · JSON companion: ")
        .append(escape(json.getFileName().toString()))
        .append("</footer></main></body></html>");
    return out.toString();
  }

  private static String sloTable(NetworkHealthReport r, HealthBudgets b) {
    StringBuilder out =
        new StringBuilder(
            "<table><thead><tr><th>Signal</th><th>Observed</th><th>Budget</th><th>Status</th></tr></thead><tbody>");
    slo(out, "Findable recall", r.findableHitRate, b.minFindableHitRate, true, true);
    slo(
        out,
        "Non-findable p95 messages",
        r.nonFindableP95Messages,
        b.maxNonFindableP95Messages,
        false,
        false);
    slo(out, "Flood admission ratio", r.floodAdmissionRatio, b.maxFloodAdmissionRatio, false, true);
    slo(out, "Flood amplification", r.amplificationRatio, b.maxAmplificationRatio, false, false);
    slo(out, "Hottest hub share", r.hottestHubShare, b.maxHottestHubShare, false, true);
    slo(
        out,
        "Duplicate leaf delivery",
        r.duplicateDeliveryRatio,
        b.maxDuplicateDeliveryRatio,
        false,
        true);
    return out.append("</tbody></table>").toString();
  }

  private static void slo(
      StringBuilder out,
      String name,
      double actual,
      double budget,
      boolean minimum,
      boolean percentage) {
    boolean pass = minimum ? actual >= budget : actual <= budget;
    out.append("<tr><td>")
        .append(name)
        .append("</td><td>")
        .append(percentage ? percent(actual) : f3(actual))
        .append("</td><td>")
        .append(minimum ? "≥ " : "≤ ")
        .append(percentage ? percent(budget) : f3(budget))
        .append("</td><td><span class=\"pill ")
        .append(pass ? "ok\">PASS" : "bad\">FAIL")
        .append("</span></td></tr>");
  }

  private static String traffic(NetworkHealthReport r) {
    double max =
        Math.max(
            r.findableP95Messages, Math.max(r.nonFindableP95Messages, r.floodMessagesPerSearch));
    StringBuilder out = new StringBuilder("<div class=\"bars\">");
    bar(out, "Findable mean", r.findableMeanMessages, max);
    bar(out, "Findable p95", r.findableP95Messages, max);
    bar(out, "Miss mean", r.nonFindableMeanMessages, max);
    bar(out, "Miss p95", r.nonFindableP95Messages, max);
    bar(out, "Flood / admitted search", r.floodMessagesPerSearch, max);
    out.append("</div><div class=\"callouts\"><p><b>")
        .append(String.format(Locale.US, "%,d", r.totalSearchMessages))
        .append("</b> request/forward messages</p><p><b>")
        .append(String.format(Locale.US, "%,d", r.totalResponseMessages))
        .append("</b> result messages</p><p><b>")
        .append(r.findableSearches + r.nonFindableSearches)
        .append("</b> honest searches</p><p><b>")
        .append(r.floodAttempts)
        .append("</b> flood attempts</p><p><b>")
        .append(String.format(Locale.US, "%,d", r.hubForwardMessages))
        .append("</b> hub forwards</p><p><b>")
        .append(String.format(Locale.US, "%,d", r.duplicateHubDeliveries))
        .append("</b> duplicate hub deliveries</p></div>");
    return out.toString();
  }

  private static void bar(StringBuilder out, String label, double value, double max) {
    double width = max == 0 ? 0 : value / max * 100;
    out.append("<div class=\"barrow\"><span>")
        .append(label)
        .append("</span><div><i style=\"width:")
        .append(f1(width))
        .append("%\"></i></div><b>")
        .append(f1(value))
        .append("</b></div>");
  }

  private static String topology(NetworkHealthReport r) {
    StringBuilder out =
        new StringBuilder("<div class=\"topology-summary\"><p><b>")
            .append(r.ultrapeers)
            .append("</b> ultrapeers in the backbone</p><p><b>")
            .append(r.leaves)
            .append("</b> leaf nodes</p><p><b>")
            .append(r.config.minUplinks)
            .append("–")
            .append(r.config.maxUplinks)
            .append("</b> uplinks per leaf</p><p><b>M=")
            .append(r.config.searchPeerFanout)
            .append("</b> shared holder/relay budget</p></div>")
            .append(
                "<div class=\"table-scroll\"><table><thead><tr><th>Hub</th><th>Leaves</th><th>Indexed leaves</th><th>Admitted</th><th>Rejected</th><th>Outgoing</th><th>Load share</th></tr></thead><tbody>");
    for (HubStats hub : r.hubStats) {
      double share =
          r.totalSearchMessages == 0 ? 0 : (double) hub.outgoingMessages / r.totalSearchMessages;
      out.append("<tr><td>UP-")
          .append(hub.id)
          .append("</td><td>")
          .append(hub.connectedLeaves)
          .append("</td><td>")
          .append(hub.indexedLeaves)
          .append("</td><td>")
          .append(hub.admittedSearches)
          .append("</td><td>")
          .append(hub.rejectedSearches)
          .append("</td><td>")
          .append(hub.outgoingMessages)
          .append("</td><td>")
          .append(percent(share))
          .append("</td></tr>");
    }
    return out.append("</tbody></table></div>").toString();
  }

  private static String insights(NetworkHealthReport r) {
    List<String> insights = new ArrayList<>();
    if (r.findableHitRate >= 0.99) {
      insights.add(
          "This workload achieved near-complete recall. The 20-hub backbone is easy to saturate; test smaller fanout and rarer items before concluding that recall is robust.");
    }
    if (r.duplicateDeliveryRatio > 0.20) {
      insights.add(
          "More than 20% of leaf deliveries are duplicates caused by multi-homing. Try reducing max uplinks from "
              + r.config.maxUplinks
              + " or excluding already-covered leaf clusters before reducing TTL.");
    }
    if (r.floodAdmissionRatio > 0.25) {
      insights.add(
          "The token bucket admits "
              + percent(r.floodAdmissionRatio)
              + " of a burst attack. A smaller burst can reduce attack cost; validate reconnect and dynamic-query traffic before changing it.");
    }
    if (r.hottestHubShare < 0.08) {
      insights.add(
          "The fully connected backbone has uniform load: the hottest hub carries "
              + percent(r.hottestHubShare)
              + " of observed traffic. Test asymmetric topologies before drawing a load-balance conclusion.");
    }
    if (r.amplificationRatio <= 1.0) {
      insights.add(
          "An admitted flood search costs no more than an honest search. Admission control, rather than per-search routing, is the primary attack-volume lever.");
    }
    StringBuilder out = new StringBuilder("<ol class=\"insights\">");
    for (String insight : insights) {
      out.append("<li>").append(escape(insight)).append("</li>");
    }
    return out.append("</ol>").toString();
  }

  private static String comparison(
      NetworkHealthReport report,
      Path previousFile,
      JsonObject previousRoot,
      JsonObject currentRoot) {
    if (previousRoot == null || !previousRoot.has("report")) {
      return "<p class=\"muted\">No previous benchmark exists yet. The next run will compare against this one.</p>";
    }
    if (!compatible(previousRoot, currentRoot)) {
      return "<p class=\"muted\">Not comparable: seed, workload configuration, host or JVM differs from the previous benchmark.</p>";
    }
    JsonObject previous = previousRoot.getAsJsonObject("report");
    StringBuilder out =
        new StringBuilder("<p class=\"muted\">Baseline: ")
            .append(escape(previousFile.getFileName().toString()))
            .append(
                "</p><table><thead><tr><th>Metric</th><th>Previous</th><th>Current</th><th>Change</th></tr></thead><tbody>");
    delta(out, "Health score", previous, "healthScore", report.healthScore, true);
    delta(out, "Findable recall", previous, "findableHitRate", report.findableHitRate, true);
    delta(
        out,
        "Miss p95 messages",
        previous,
        "nonFindableP95Messages",
        report.nonFindableP95Messages,
        false);
    delta(
        out, "Flood admission", previous, "floodAdmissionRatio", report.floodAdmissionRatio, false);
    delta(out, "Amplification", previous, "amplificationRatio", report.amplificationRatio, false);
    delta(
        out,
        "Duplicate delivery",
        previous,
        "duplicateDeliveryRatio",
        report.duplicateDeliveryRatio,
        false);
    return out.append("</tbody></table>").toString();
  }

  private static void delta(
      StringBuilder out,
      String label,
      JsonObject previous,
      String key,
      double current,
      boolean higherIsBetter) {
    double old = previous.has(key) ? previous.get(key).getAsDouble() : 0;
    double change = old == 0 ? 0 : (current - old) / old;
    boolean improved = higherIsBetter ? change > 0 : change < 0;
    out.append("<tr><td>")
        .append(label)
        .append("</td><td>")
        .append(f3(old))
        .append("</td><td>")
        .append(f3(current))
        .append("</td><td class=\"")
        .append(change == 0 ? "neutral" : improved ? "positive" : "negative")
        .append("\">")
        .append(change > 0 ? "+" : "")
        .append(percent(change))
        .append("</td></tr>");
  }

  private static boolean compatible(JsonObject previous, JsonObject current) {
    if (!previous.has("report") || !previous.getAsJsonObject("report").has("config")) {
      return false;
    }
    if (!workload(previous).equals(workload(current))) {
      return false;
    }
    for (String key : List.of("host", "java", "architecture")) {
      if (!previous.has(key) || !previous.get(key).equals(current.get(key))) {
        return false;
      }
    }
    return true;
  }

  private static JsonObject workload(JsonObject run) {
    JsonObject config = run.getAsJsonObject("report").getAsJsonObject("config").deepCopy();
    for (String routingParameter :
        List.of(
            "minUplinks",
            "maxUplinks",
            "flooderUplinks",
            "searchPeerFanout",
            "holderBudget",
            "searchTtl",
            "softMax",
            "admissionBurst",
            "admissionRefillPerSecond")) {
      config.remove(routingParameter);
    }
    return config;
  }

  private static String history(List<JsonObject> history, JsonObject current) {
    List<JsonObject> comparable = history.stream().filter(run -> compatible(run, current)).toList();
    int start = Math.max(0, comparable.size() - 20);
    StringBuilder out =
        new StringBuilder(
            "<div class=\"history-chart\"><svg viewBox=\"0 0 900 180\" role=\"img\" aria-label=\"Health score history\"><polyline points=\"");
    List<Double> scores = new ArrayList<>();
    for (int i = start; i < comparable.size(); i++) {
      scores.add(comparable.get(i).getAsJsonObject("report").get("healthScore").getAsDouble());
    }
    for (int i = 0; i < scores.size(); i++) {
      double x = scores.size() == 1 ? 450 : 20 + i * 860.0 / (scores.size() - 1);
      double y = 160 - Math.max(0, Math.min(1, scores.get(i))) * 140;
      out.append(f1(x)).append(',').append(f1(y)).append(' ');
    }
    out.append(
        "\"/></svg></div><table><thead><tr><th>Run</th><th>Commit</th><th>Score</th><th>Recall</th><th>Miss p95</th><th>Flood admitted</th><th>Duplicate</th></tr></thead><tbody>");
    for (int i = comparable.size() - 1; i >= start; i--) {
      JsonObject root = comparable.get(i);
      JsonObject r = root.getAsJsonObject("report");
      out.append("<tr><td>")
          .append(escape(root.get("timestamp").getAsString()))
          .append("</td><td><code>")
          .append(escape(root.get("gitCommit").getAsString()))
          .append(root.get("gitDirty").getAsBoolean() ? " dirty" : "")
          .append("</code></td><td>")
          .append(f3(value(r, "healthScore")))
          .append("</td><td>")
          .append(percent(value(r, "findableHitRate")))
          .append("</td><td>")
          .append(f0(value(r, "nonFindableP95Messages")))
          .append("</td><td>")
          .append(percent(value(r, "floodAdmissionRatio")))
          .append("</td><td>")
          .append(percent(value(r, "duplicateDeliveryRatio")))
          .append("</td></tr>");
    }
    return out.append("</tbody></table>").toString();
  }

  private static String reproducibility(NetworkHealthReport r, ZonedDateTime timestamp, Path json) {
    WorkloadConfig c = r.config;
    return "<div class=\"two-col\"><table><tbody>"
        + row("Timestamp", timestamp.format(DISPLAY_TIME))
        + row("JSON record", json.getFileName().toString())
        + row("Seed", Long.toString(c.seed))
        + row("Nodes", c.ultrapeerCount + " ultrapeers + " + c.leafCount + " leaves")
        + row("Catalog", c.contentItems + " items × " + c.tokensPerItem + " tokens")
        + row("Maximum holders per item", Integer.toString(c.maxHoldersPerItem))
        + row("Digest availability", percent(c.digestCoverageFraction))
        + row("Honest workload", percent(c.searcherFraction) + " × " + c.searchesPerSearcher)
        + row("Flood workload", percent(c.flooderFraction) + " × " + c.flooderBurst)
        + "</tbody></table><table><tbody>"
        + row("Uplinks", c.minUplinks + "–" + c.maxUplinks)
        + row("Flooder uplinks", Integer.toString(Math.min(c.flooderUplinks, c.ultrapeerCount)))
        + row("Search fanout M", Integer.toString(c.searchPeerFanout))
        + row("Holder budget", Integer.toString(c.holderBudget))
        + row("Search TTL / soft max", c.searchTtl + " / " + c.softMax)
        + row("Admission burst", Integer.toString(c.admissionBurst))
        + row("Admission refill", f3(c.admissionRefillPerSecond) + " / second")
        + row("Compute duration", r.durationMillis + " ms")
        + "</tbody></table></div>";
  }

  private static String limitations() {
    return "<ul class=\"limitations\"><li>All nodes are in-memory objects within one JVM. This is a message-count model, not separate FrostWire processes: production request parsing, signing, response handlers, rUDP, NAT, loss, latency, and cryptographic CPU cost are excluded.</li><li>Leaf and cluster tables use the production IndexDigest Bloom filter; digest availability is an explicit workload assumption.</li><li>Duplicate hub deliveries are counted before receiver deduplication; available sessions and actual transport delays are not modeled.</li><li>Compare runs with the same seed and workload; change one routing parameter at a time. Fully covering a small hub mesh is not WAN recall evidence.</li></ul>";
  }

  private static String section(String title, String body) {
    return "<section class=\"panel\"><h2>" + escape(title) + "</h2>" + body + "</section>";
  }

  private static String metric(String label, String value, String note) {
    return "<article><span>"
        + escape(label)
        + "</span><strong>"
        + escape(value)
        + "</strong><small>"
        + escape(note)
        + "</small></article>";
  }

  private static String row(String key, String value) {
    return "<tr><th>" + escape(key) + "</th><td>" + escape(value) + "</td></tr>";
  }

  private static List<Path> benchmarkFiles(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(
              path ->
                  path.getFileName()
                      .toString()
                      .matches("icebridge-simulation-\\d{8}-\\d{6}(?:-\\d+)?\\.json"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
    }
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  private static double value(JsonObject object, String key) {
    return object.has(key) ? object.get(key).getAsDouble() : 0;
  }

  private static String host() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
      return "unknown";
    }
  }

  private static String git(String... arguments) {
    try {
      List<String> command = new ArrayList<>();
      command.add("git");
      Collections.addAll(command, arguments);
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      String output =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      return process.waitFor() == 0 ? output : "unknown";
    } catch (Exception ignored) {
      return "unknown";
    }
  }

  private static String escape(String value) {
    return value == null
        ? ""
        : value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
  }

  private static String percent(double value) {
    return String.format(Locale.US, "%.1f%%", value * 100);
  }

  private static String f0(double value) {
    return String.format(Locale.US, "%.0f", value);
  }

  private static String f1(double value) {
    return String.format(Locale.US, "%.1f", value);
  }

  private static String f3(double value) {
    return String.format(Locale.US, "%.3f", value);
  }

  private static String css() {
    return ""
        + ":root{color-scheme:dark;--bg:#07110f;--panel:#0d1b18;--line:#23443a;--text:#edf8f3;--muted:#8fa99f;--mint:#4ee6a8;--amber:#f6c453;--red:#ff6b6b;--blue:#6cb6ff}"
        + "*{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at 15% 0,#17362d 0,transparent 35%),var(--bg);color:var(--text);font:15px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}main{max-width:1240px;margin:auto;padding:44px 26px 80px}header{display:flex;justify-content:space-between;align-items:end;border-bottom:1px solid var(--line);padding-bottom:28px;margin-bottom:24px}.eyebrow{color:var(--mint);letter-spacing:.18em;font-size:12px}h1{font:700 clamp(32px,6vw,68px)/.95 system-ui,sans-serif;margin:8px 0}.subtitle,.muted{color:var(--muted)}.score{border:1px solid var(--line);padding:16px 22px;min-width:180px}.score span,.metrics span{display:block;color:var(--muted);font-size:11px;letter-spacing:.1em}.score strong{font-size:36px}.score.pass{border-color:var(--mint)}.score.fail{border-color:var(--red)}.grid{display:grid;gap:14px}.metrics{grid-template-columns:repeat(auto-fit,minmax(170px,1fr));margin-bottom:20px}.metrics article,.panel{background:linear-gradient(145deg,#10231e,#0a1613);border:1px solid var(--line);box-shadow:0 14px 40px #0005}.metrics article{padding:18px}.metrics strong{display:block;font:700 28px/1.2 system-ui,sans-serif;margin:10px 0}.metrics small{color:var(--muted)}.panel{padding:24px;margin:18px 0;overflow:hidden}h2{font:700 21px system-ui,sans-serif;margin:0 0 18px;color:var(--amber)}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:10px 12px;border-bottom:1px solid #18342c}thead th{color:var(--muted);font-size:11px;letter-spacing:.08em;text-transform:uppercase}.pill{padding:4px 8px;border:1px solid}.pill.ok,.positive{color:var(--mint)}.pill.bad,.negative{color:var(--red)}.neutral{color:var(--muted)}.bars{display:grid;gap:10px}.barrow{display:grid;grid-template-columns:210px 1fr 70px;align-items:center;gap:12px}.barrow div{height:12px;background:#152b25}.barrow i{display:block;height:100%;background:linear-gradient(90deg,var(--mint),var(--blue))}.barrow b{text-align:right}.callouts,.topology-summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:10px;margin-top:20px}.callouts p,.topology-summary p{border-left:3px solid var(--mint);padding:8px 12px;background:#091512}.callouts b,.topology-summary b{display:block;font-size:20px}.table-scroll{overflow:auto;max-height:520px}.insights{display:grid;gap:12px;padding-left:28px}.insights li{padding:12px;background:#091512;border-left:3px solid var(--amber)}.history-chart{background:#081310;border:1px solid var(--line);margin-bottom:16px}.history-chart svg{width:100%;height:180px}.history-chart polyline{fill:none;stroke:var(--mint);stroke-width:4;vector-effect:non-scaling-stroke}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:18px}.limitations{color:var(--muted)}code{color:var(--blue)}footer{color:var(--muted);text-align:center;margin-top:32px;font-size:12px}@media(max-width:700px){header{display:block}.score{margin-top:20px}.barrow{grid-template-columns:1fr}.two-col{grid-template-columns:1fr}main{padding:24px 12px}}";
  }
}
