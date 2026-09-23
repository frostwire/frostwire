/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkEdge;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkHealthReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkNode;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NetworkSnapshot;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.NodeType;
import com.frostwire.search.relay.icebridge.sim.IceBridgeWorkloadSimulator.SimulationObserver;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Standalone, interactive Swing visualization for simulation-only network snapshots. */
public final class NetworkVisualizationFrame extends JFrame {

  private static final Color BACKGROUND = new Color(7, 17, 15);
  private static final Color PANEL = new Color(13, 27, 24);
  private static final Color TEXT = new Color(237, 248, 243);
  private static final Color MUTED = new Color(105, 139, 127);
  private static final Color MINT = new Color(78, 230, 168);
  private static final Color AMBER = new Color(246, 196, 83);
  private static final Color RED = new Color(255, 32, 48);
  private static final Color BLUE = new Color(108, 182, 255);

  private final NetworkCanvas canvas = new NetworkCanvas();
  private final JTextArea details = new JTextArea();
  private final JLabel status = new JLabel("Preparing network...");
  private final JProgressBar progress = new JProgressBar();
  private final JButton openReport = new JButton("Open HTML report");
  private Path reportPath;
  private Integer inspectedNodeId;

  public NetworkVisualizationFrame() {
    super("IceBridge Message Model — sampled, not live sockets");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(980, 680));
    setSize(1380, 880);
    setLocationByPlatform(true);
    getContentPane().setBackground(BACKGROUND);
    setLayout(new BorderLayout());

    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    toolbar.setBackground(PANEL);
    toolbar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    JButton reset = new JButton("Reset view");
    reset.addActionListener(event -> canvas.resetView());
    openReport.setEnabled(false);
    openReport.addActionListener(event -> openReport());
    toolbar.add(reset);
    toolbar.addSeparator();
    toolbar.add(openReport);
    add(toolbar, BorderLayout.NORTH);

    details.setEditable(false);
    details.setBackground(PANEL);
    details.setForeground(TEXT);
    details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
    details.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
    details.setText(
        "ICEBRIDGE LIVE VIEW\n\n"
            + "Mouse wheel  Zoom\n"
            + "Drag         Pan\n"
            + "Click node   Inspect\n\n"
            + "Ultrapeer    mint\n"
            + "Leaf         blue\n"
            + "Searcher     amber\n"
            + "Flooder      red\n"
            + "Packet       sampled moving hop\n\n"
            + "Animation shows one sampled search per 25,\n"
            + "not every message on the wire.\n");
    canvas.setNodeSelectionListener(
        node -> {
          inspectedNodeId = node == null ? null : node.id;
          showNode(node);
        });
    JScrollPane detailScroll = new JScrollPane(details);
    detailScroll.setPreferredSize(new Dimension(300, 300));
    detailScroll.setBorder(BorderFactory.createEmptyBorder());
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, detailScroll);
    split.setResizeWeight(1.0);
    split.setDividerSize(4);
    split.setBorder(BorderFactory.createEmptyBorder());
    add(split, BorderLayout.CENTER);

    JPanel footer = new JPanel(new BorderLayout(12, 0));
    footer.setBackground(PANEL);
    footer.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    status.setForeground(TEXT);
    progress.setStringPainted(true);
    progress.setForeground(MINT);
    footer.add(status, BorderLayout.CENTER);
    footer.add(progress, BorderLayout.EAST);
    add(footer, BorderLayout.SOUTH);
  }

  public SimulationObserver observer(int paceMillis) {
    return snapshot -> {
      SwingUtilities.invokeLater(() -> update(snapshot));
      if (paceMillis > 0) {
        try {
          Thread.sleep(paceMillis);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    };
  }

  public void complete(NetworkHealthReport report, Path reportPath) {
    SwingUtilities.invokeLater(
        () -> {
          this.reportPath = reportPath;
          openReport.setEnabled(true);
          status.setText(
              String.format(
                  "Complete · score %.3f · recall %.1f%% · flood admitted %.1f%% · report %s",
                  report.healthScore,
                  report.findableHitRate * 100,
                  report.floodAdmissionRatio * 100,
                  reportPath.getFileName()));
        });
  }

  public void failed(Throwable failure) {
    SwingUtilities.invokeLater(
        () -> {
          status.setText("Simulation failed: " + failure.getMessage());
          details.setText("SIMULATION FAILURE\n\n" + failure);
        });
  }

  private void update(NetworkSnapshot snapshot) {
    canvas.setSnapshot(snapshot);
    if (inspectedNodeId != null) {
      showNode(nodeById(snapshot, inspectedNodeId));
    }
    progress.setMaximum(Math.max(1, snapshot.totalSearches));
    progress.setValue(snapshot.completedSearches);
    progress.setString(snapshot.completedSearches + " / " + snapshot.totalSearches);
    int percent =
        snapshot.totalSearches == 0 ? 0 : snapshot.completedSearches * 100 / snapshot.totalSearches;
    status.setText(
        String.format(
            "%d%%  %s  ·  findable %d/%d  ·  flood admitted %d/%d  ·  %,d messages",
            percent,
            snapshot.phase,
            snapshot.findableHits,
            snapshot.findableAttempts,
            snapshot.floodAdmitted,
            snapshot.floodAttempts,
            snapshot.messagesSoFar));
  }

  static NetworkNode nodeById(NetworkSnapshot snapshot, int id) {
    for (NetworkNode node : snapshot.nodes) {
      if (node.id == id) {
        return node;
      }
    }
    return null;
  }

  private void showNode(NetworkNode node) {
    if (node == null) {
      return;
    }
    details.setText(
        "NODE INSPECTOR\n\n"
            + node.label
            + "\n"
            + "type         "
            + node.type
            + "\n"
            + "connections  "
            + node.connections
            + "\n"
            + "content      "
            + node.contentItems
            + " items\n"
            + "messages     "
            + node.messages
            + "\n"
            + "rejected     "
            + node.rejected
            + "\n"
            + "duplicates   "
            + node.duplicates
            + "\n"
            + "searcher     "
            + node.searcher
            + "\n"
            + "flooder      "
            + node.flooder
            + "\n");
  }

  private void openReport() {
    if (reportPath == null || !Desktop.isDesktopSupported()) {
      return;
    }
    try {
      Desktop.getDesktop().browse(reportPath.toUri());
    } catch (Exception failure) {
      status.setText("Could not open report: " + failure.getMessage());
    }
  }

  private interface NodeSelectionListener {
    void selected(NetworkNode node);
  }

  private static final class NetworkCanvas extends JPanel {

    private final Map<Integer, Point2D.Double> positions = new HashMap<>();
    private NetworkSnapshot snapshot;
    private NodeSelectionListener nodeSelectionListener;
    private double zoom = 0.72;
    private double panX;
    private double panY;
    private Point dragStart;
    private boolean dragged;
    private Integer selectedNode;
    private final List<LivePacket> packets = new ArrayList<>();
    private final Timer animator;

    NetworkCanvas() {
      setBackground(BACKGROUND);
      animator =
          new Timer(
              33,
              event -> {
                long now = System.currentTimeMillis();
                packets.removeIf(packet -> now - packet.bornMillis > 800 + packet.hop.step * 90L);
                if (!packets.isEmpty()) {
                  repaint();
                }
              });
      animator.start();
      MouseAdapter mouse =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
              dragStart = event.getPoint();
              dragged = false;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
              if (dragStart == null) {
                return;
              }
              panX += event.getX() - dragStart.x;
              panY += event.getY() - dragStart.y;
              dragStart = event.getPoint();
              dragged = true;
              repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
              if (dragStart != null && !dragged) {
                select(event.getPoint());
              }
              dragStart = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
              double oldZoom = zoom;
              zoom =
                  Math.max(0.12, Math.min(4.5, zoom * Math.pow(1.12, -event.getWheelRotation())));
              double centerX = getWidth() / 2.0 + panX;
              double centerY = getHeight() / 2.0 + panY;
              double worldX = (event.getX() - centerX) / oldZoom;
              double worldY = (event.getY() - centerY) / oldZoom;
              panX += worldX * (oldZoom - zoom);
              panY += worldY * (oldZoom - zoom);
              repaint();
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
      addMouseWheelListener(mouse);
    }

    void setSnapshot(NetworkSnapshot snapshot) {
      this.snapshot = snapshot;
      if (positions.isEmpty()) {
        layout(snapshot);
      }
      long now = System.currentTimeMillis();
      for (IceBridgeWorkloadSimulator.ActivityHop hop : snapshot.activity) {
        packets.add(new LivePacket(hop, now));
      }
      while (packets.size() > 160) {
        packets.remove(0);
      }
      repaint();
    }

    void setNodeSelectionListener(NodeSelectionListener listener) {
      nodeSelectionListener = listener;
    }

    void resetView() {
      zoom = 0.72;
      panX = 0;
      panY = 0;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      NetworkSnapshot current = snapshot;
      if (current == null) {
        return;
      }
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      paintEdges(g, current);
      paintNodes(g, current);
      paintPackets(g);
      g.dispose();
    }

    private void paintPackets(Graphics2D g) {
      long now = System.currentTimeMillis();
      for (LivePacket packet : packets) {
        Point2D.Double from = positions.get(packet.hop.from);
        Point2D.Double to = positions.get(packet.hop.to);
        if (from == null || to == null) {
          continue;
        }
        float age = (now - packet.bornMillis - packet.hop.step * 90L) / 800f;
        if (age < 0 || age >= 1) {
          continue;
        }
        float travel = Math.min(1f, age / 0.75f);
        Point start = screen(from);
        Point end = screen(to);
        int x = start.x + Math.round((end.x - start.x) * travel);
        int y = start.y + Math.round((end.y - start.y) * travel);
        Color color = packet.hop.flood ? RED : packet.hop.hit ? MINT : AMBER;
        int alpha = age > 0.75f ? Math.max(0, (int) (255 * (1f - age) / 0.25f)) : 230;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g.setStroke(new BasicStroke(packet.hop.hit ? 2.5f : 1.5f));
        g.drawLine(start.x, start.y, x, y);
        int size = packet.hop.hit ? 9 : 6;
        g.fillOval(x - size / 2, y - size / 2, size, size);
      }
    }

    private void paintEdges(Graphics2D g, NetworkSnapshot current) {
      g.setStroke(new BasicStroke(1f));
      for (NetworkEdge edge : current.edges) {
        if (selectedNode != null && edge.from != selectedNode && edge.to != selectedNode) {
          continue;
        }
        Point2D.Double from = positions.get(edge.from);
        Point2D.Double to = positions.get(edge.to);
        if (from == null || to == null) {
          continue;
        }
        g.setColor(
            selectedNode != null
                ? new Color(246, 196, 83, 150)
                : edge.backbone ? new Color(78, 230, 168, 55) : new Color(108, 182, 255, 14));
        Point a = screen(from);
        Point b = screen(to);
        g.drawLine(a.x, a.y, b.x, b.y);
      }
    }

    private void paintNodes(Graphics2D g, NetworkSnapshot current) {
      long maxMessages = 1;
      for (NetworkNode node : current.nodes) {
        maxMessages = Math.max(maxMessages, node.messages);
      }
      for (NetworkNode node : current.nodes) {
        if (!node.flooder) {
          paintNode(g, node, maxMessages);
        }
      }
      for (NetworkNode node : current.nodes) {
        if (node.flooder) {
          paintNode(g, node, maxMessages);
        }
      }
    }

    private void paintNode(Graphics2D g, NetworkNode node, long maxMessages) {
      Point point = screen(positions.get(node.id));
      int size = node.type == NodeType.ULTRAPEER ? 15 : Math.max(3, (int) Math.round(4 * zoom));
      Color color = node.type == NodeType.ULTRAPEER ? MINT : BLUE;
      if (node.searcher) {
        color = AMBER;
      }
      int alpha = 90 + (int) (165 * Math.log1p(node.messages) / Math.log1p(maxMessages));
      if (node.flooder) {
        color = RED;
        alpha = 255;
        size = Math.max(12, (int) Math.round(14 * Math.max(0.7, zoom)));
      }
      g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
      g.fillOval(point.x - size / 2, point.y - size / 2, size, size);
      if (node.flooder) {
        g.setColor(new Color(255, 220, 220));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(point.x - size / 2 - 3, point.y - size / 2 - 3, size + 6, size + 6);
      }
      if (selectedNode != null && selectedNode == node.id) {
        g.setColor(AMBER);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(point.x - size / 2 - 5, point.y - size / 2 - 5, size + 10, size + 10);
      }
    }

    private void layout(NetworkSnapshot current) {
      int ultrapeers = 0;
      for (NetworkNode node : current.nodes) {
        if (node.type == NodeType.ULTRAPEER) {
          ultrapeers++;
        }
      }
      for (NetworkNode node : current.nodes) {
        if (node.type == NodeType.ULTRAPEER) {
          double angle = Math.PI * 2 * node.id / ultrapeers - Math.PI / 2;
          positions.put(node.id, new Point2D.Double(Math.cos(angle) * 230, Math.sin(angle) * 230));
        } else {
          int leaf = node.id - ultrapeers;
          double angle = leaf * 2.399963229728653;
          double radius = 360 + (leaf % 11) * 34;
          positions.put(
              node.id, new Point2D.Double(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
      }
    }

    private void select(Point click) {
      if (snapshot == null) {
        return;
      }
      NetworkNode nearest = null;
      double best = 15;
      for (NetworkNode node : snapshot.nodes) {
        double distance = click.distance(screen(positions.get(node.id)));
        if (distance < best) {
          best = distance;
          nearest = node;
        }
      }
      selectedNode = nearest == null ? null : nearest.id;
      if (nodeSelectionListener != null) {
        nodeSelectionListener.selected(nearest);
      }
      repaint();
    }

    private static final class LivePacket {
      final IceBridgeWorkloadSimulator.ActivityHop hop;
      final long bornMillis;

      LivePacket(IceBridgeWorkloadSimulator.ActivityHop hop, long bornMillis) {
        this.hop = hop;
        this.bornMillis = bornMillis;
      }
    }

    private Point screen(Point2D.Double point) {
      return new Point(
          (int) Math.round(getWidth() / 2.0 + panX + point.x * zoom),
          (int) Math.round(getHeight() / 2.0 + panY + point.y * zoom));
    }
  }
}
