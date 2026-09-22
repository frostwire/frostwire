/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.HealthBudgets;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkHealthReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.WorkloadConfig;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import javax.swing.SwingUtilities;

/** Command-line entry point for the standalone IceBridge benchmark and live network viewer. */
public final class IceBridgeSimulationMain {

  private IceBridgeSimulationMain() {}

  public static void main(String[] arguments) throws Exception {
    for (String argument : arguments) {
      if ("--help".equals(argument) || "-h".equals(argument)) {
        Options.usage();
        return;
      }
    }
    Options options = Options.parse(arguments);
    WorkloadConfig config = new WorkloadConfig();
    config.seed = options.seed;
    boolean showUi = options.showUi && !GraphicsEnvironment.isHeadless();
    NetworkVisualizationFrame viewer = showUi ? createViewer() : null;
    Runnable simulation = () -> run(config, options, viewer);
    if (viewer == null) {
      simulation.run();
    } else {
      Thread worker = new Thread(simulation, "icebridge-simulation");
      worker.start();
      worker.join();
    }
  }

  private static void run(
      WorkloadConfig config, Options options, NetworkVisualizationFrame viewer) {
    try {
      IceBridgeWorkloadSimulator simulator = new IceBridgeWorkloadSimulator(config);
      NetworkHealthReport report =
          viewer == null ? simulator.run() : simulator.run(viewer.observer(options.paceMillis));
      SimulationReportWriter.ReportArtifacts artifacts =
          new SimulationReportWriter().write(report, options.outputDirectory, ZonedDateTime.now());
      System.out.println(report);
      System.out.println("HTML_REPORT=" + artifacts.html().toAbsolutePath());
      System.out.println("JSON_BENCHMARK=" + artifacts.json().toAbsolutePath());
      if (viewer != null) {
        viewer.complete(report, artifacts.html());
      }
      if (options.openReport) {
        open(artifacts.html());
      }
      if (!report.meets(new HealthBudgets())) {
        System.err.println("Simulation completed with failed network-health budgets.");
        if (viewer == null) {
          throw new IllegalStateException("network-health budgets failed");
        }
      }
    } catch (Throwable failure) {
      failure.printStackTrace(System.err);
      if (viewer != null) {
        viewer.failed(failure);
      } else {
        throw new IllegalStateException("IceBridge simulation failed", failure);
      }
    }
  }

  private static NetworkVisualizationFrame createViewer() throws Exception {
    NetworkVisualizationFrame[] result = new NetworkVisualizationFrame[1];
    SwingUtilities.invokeAndWait(
        () -> {
          result[0] = new NetworkVisualizationFrame();
          result[0].setVisible(true);
        });
    return result[0];
  }

  private static void open(Path report) {
    if (!Desktop.isDesktopSupported()) {
      return;
    }
    try {
      Desktop.getDesktop().browse(report.toUri());
    } catch (Exception failure) {
      System.err.println("Could not open report: " + failure.getMessage());
    }
  }

  private static final class Options {
    final Path outputDirectory;
    final long seed;
    final int paceMillis;
    final boolean showUi;
    final boolean openReport;

    Options(Path outputDirectory, long seed, int paceMillis, boolean showUi, boolean openReport) {
      this.outputDirectory = outputDirectory;
      this.seed = seed;
      this.paceMillis = paceMillis;
      this.showUi = showUi;
      this.openReport = openReport;
    }

    static Options parse(String[] arguments) {
      Path output = Path.of("build", "reports", "icebridge-simulation");
      long seed = 20260922L;
      int pace = 35;
      boolean ui = true;
      boolean open = true;
      for (String argument : arguments) {
        if ("--no-ui".equals(argument)) {
          ui = false;
        } else if ("--no-open".equals(argument)) {
          open = false;
        } else if (argument.startsWith("--output-dir=")) {
          output = Path.of(argument.substring("--output-dir=".length()));
        } else if (argument.startsWith("--seed=")) {
          seed = Long.parseLong(argument.substring("--seed=".length()));
        } else if (argument.startsWith("--pace-ms=")) {
          pace = Integer.parseInt(argument.substring("--pace-ms=".length()));
        } else {
          throw new IllegalArgumentException("Unknown simulation option: " + argument);
        }
      }
      return new Options(output, seed, Math.max(0, pace), ui, open);
    }

    static void usage() {
      System.out.println(
          "Usage: runIceBridgeSimulation [--no-ui] [--no-open] [--seed=N] "
              + "[--pace-ms=N] [--output-dir=PATH]");
    }
  }
}
