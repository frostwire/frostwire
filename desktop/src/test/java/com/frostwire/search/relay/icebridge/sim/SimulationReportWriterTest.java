/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkHealthReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.WorkloadConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulationReportWriterTest {

  @TempDir Path outputDirectory;

  @Test
  void writesTimestampedBenchmarkAndComparesTheNextRun() throws Exception {
    NetworkHealthReport report = new IceBridgeWorkloadSimulator(new WorkloadConfig()).run();
    SimulationReportWriter writer = new SimulationReportWriter();
    ZonedDateTime firstRun = ZonedDateTime.of(2026, 9, 22, 15, 55, 10, 0, ZoneId.of("UTC"));

    SimulationReportWriter.ReportArtifacts first = writer.write(report, outputDirectory, firstRun);

    assertEquals(
        "icebridge-simulation-20260922-155510.html", first.html().getFileName().toString());
    assertEquals(
        "icebridge-simulation-20260922-155510.json", first.json().getFileName().toString());
    String html = Files.readString(first.html());
    assertTrue(html.contains("IceBridge Network Benchmark"));
    assertTrue(html.contains("Network topology"));
    assertTrue(html.contains("Tuning insights"));
    assertTrue(html.contains("Findable recall"));
    assertTrue(Files.readString(first.json()).contains("\"findableHitRate\""));

    SimulationReportWriter.ReportArtifacts second =
        writer.write(report, outputDirectory, firstRun.plusMinutes(1));
    String secondHtml = Files.readString(second.html());
    assertTrue(secondHtml.contains("Previous benchmark comparison"));
    assertTrue(secondHtml.contains("icebridge-simulation-20260922-155510.json"));
    assertEquals(
        second.html().toAbsolutePath().toString(),
        Files.readString(outputDirectory.resolve("latest-report.txt")).trim());
  }
}
