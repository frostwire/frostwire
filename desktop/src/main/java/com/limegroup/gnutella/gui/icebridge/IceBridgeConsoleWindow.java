/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.icebridge;

import com.frostwire.search.relay.event.IceBridgeEvent;
import com.frostwire.search.relay.event.IceBridgeEventLog;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.I18n;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

/**
 * Live, filterable view of {@link IceBridgeEventLog} for diagnosing distributed search / IceBridge
 * behavior without a terminal.
 *
 * <p>Events arrive from arbitrary threads; they are queued and drained on the EDT by a coalesced
 * {@link SwingUtilities#invokeLater} flush, so producers are never blocked and Swing state is only
 * touched on the EDT. The in-memory table is bounded; older rows are discarded.
 *
 * <p>Threading: construct and interact on the EDT only.
 */
public final class IceBridgeConsoleWindow {

  private static final Logger LOG = Logger.getLogger(IceBridgeConsoleWindow.class);
  private static final int MAX_ROWS = 2000;
  private static final int SEED_ROWS = 500;
  private static final SimpleDateFormat TIME_FORMAT =
      new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

  private static IceBridgeConsoleWindow instance;

  private final JFrame frame = new JFrame(I18n.tr("IceBridge Console"));
  private final EventTableModel model = new EventTableModel();
  private final JTable table = new JTable(model);
  private final JComboBox<String> levelFilter = new JComboBox<>();
  private final JComboBox<String> categoryFilter = new JComboBox<>();
  private final JTextField textFilter = new JTextField(16);
  private final JTextField peerFilter = new JTextField(12);
  private final JCheckBox autoScroll = new JCheckBox(I18n.tr("Auto-scroll"), true);
  private final JButton pauseButton = new JButton(I18n.tr("Pause"));
  private final ConcurrentLinkedQueue<IceBridgeEvent> pending = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean flushScheduled = new AtomicBoolean();
  private final Consumer<IceBridgeEvent> listener = this::onEvent;
  private final List<IceBridgeEvent> all = new ArrayList<>();
  private boolean paused;

  private IceBridgeConsoleWindow() {
    buildUi();
    seedFromLog();
    IceBridgeEventLog.instance().addListener(listener);
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            IceBridgeEventLog.instance().removeListener(listener);
            frame.dispose();
            instance = null;
          }
        });
  }

  /** Opens (or focuses) the single console window. Must be called on the EDT. */
  public static void showConsole() {
    if (instance == null) {
      instance = new IceBridgeConsoleWindow();
    }
    instance.frame.setVisible(true);
    instance.frame.toFront();
  }

  private void buildUi() {
    levelFilter.addItem("DEBUG");
    levelFilter.addItem("INFO");
    levelFilter.addItem("WARN");
    levelFilter.addItem("ERROR");
    levelFilter.setSelectedItem("DEBUG");
    categoryFilter.addItem(I18n.tr("All categories"));
    for (IceBridgeEvent.Category c : IceBridgeEvent.Category.values()) {
      categoryFilter.addItem(c.name());
    }

    JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    filters.add(new JLabel(I18n.tr("Min level:")));
    filters.add(levelFilter);
    filters.add(new JLabel(I18n.tr("Category:")));
    filters.add(categoryFilter);
    filters.add(new JLabel(I18n.tr("Text:")));
    filters.add(textFilter);
    filters.add(new JLabel(I18n.tr("Peer:")));
    filters.add(peerFilter);
    JButton apply = new JButton(I18n.tr("Apply"));
    apply.addActionListener(e -> applyFilter());
    filters.add(apply);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    pauseButton.addActionListener(e -> togglePause());
    controls.add(pauseButton);
    JButton clear = new JButton(I18n.tr("Clear"));
    clear.addActionListener(e -> clearConsole());
    controls.add(clear);
    JButton save = new JButton(I18n.tr("Save..."));
    save.addActionListener(e -> saveToFile());
    controls.add(save);
    JButton copy = new JButton(I18n.tr("Copy"));
    copy.addActionListener(e -> copyToClipboard());
    controls.add(copy);
    JButton peerCatalog = new JButton(I18n.tr("Browse Shared Torrents") + "...");
    peerCatalog.addActionListener(e -> PeerCatalogWindow.showPeerPicker());
    controls.add(peerCatalog);
    controls.add(autoScroll);
    controls.add(Box.createHorizontalGlue());

    table.setAutoCreateRowSorter(false);
    table.getColumnModel().getColumn(0).setPreferredWidth(90);
    table.getColumnModel().getColumn(1).setPreferredWidth(60);
    table.getColumnModel().getColumn(2).setPreferredWidth(90);
    table.getColumnModel().getColumn(3).setPreferredWidth(150);
    table.getColumnModel().getColumn(4).setPreferredWidth(700);

    JPanel top = new JPanel(new BorderLayout());
    top.add(filters, BorderLayout.NORTH);
    top.add(controls, BorderLayout.SOUTH);
    top.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    frame.add(top, BorderLayout.NORTH);
    frame.add(new JScrollPane(table), BorderLayout.CENTER);
    frame.setSize(new Dimension(1080, 600));
    frame.setLocationRelativeTo(null);
  }

  private void seedFromLog() {
    List<IceBridgeEvent> tail = IceBridgeEventLog.instance().tail(SEED_ROWS);
    // tail() is newest-first; show chronologically.
    for (int i = tail.size() - 1; i >= 0; i--) {
      appendToBuffer(tail.get(i));
    }
    model.setEvents(filtered());
  }

  private void onEvent(IceBridgeEvent event) {
    if (event == null || paused) {
      return;
    }
    pending.add(event);
    if (flushScheduled.compareAndSet(false, true)) {
      SwingUtilities.invokeLater(this::flushPending);
    }
  }

  private void flushPending() {
    flushScheduled.set(false);
    if (paused) {
      pending.clear();
      return;
    }
    IceBridgeEvent e;
    boolean added = false;
    while ((e = pending.poll()) != null) {
      appendToBuffer(e);
      added = true;
    }
    if (added) {
      model.setEvents(filtered());
      if (autoScroll.isSelected() && model.getRowCount() > 0) {
        table.scrollRectToVisible(table.getCellRect(model.getRowCount() - 1, 0, true));
      }
    }
  }

  private void appendToBuffer(IceBridgeEvent event) {
    all.add(event);
    if (all.size() > MAX_ROWS) {
      all.remove(0);
    }
  }

  private void togglePause() {
    paused = !paused;
    pauseButton.setText(paused ? I18n.tr("Resume") : I18n.tr("Pause"));
    if (!paused) {
      flushPending();
    }
  }

  private void clearConsole() {
    all.clear();
    IceBridgeEventLog.instance().clear();
    model.setEvents(new ArrayList<>());
  }

  private void applyFilter() {
    model.setEvents(filtered());
  }

  private List<IceBridgeEvent> filtered() {
    IceBridgeEvent.Level minLevel =
        IceBridgeEventLog.parseLevel((String) levelFilter.getSelectedItem());
    Object cat = categoryFilter.getSelectedItem();
    String categoryName = cat == null ? "" : cat.toString();
    IceBridgeEvent.Category category = null;
    for (IceBridgeEvent.Category c : IceBridgeEvent.Category.values()) {
      if (c.name().equals(categoryName)) {
        category = c;
        break;
      }
    }
    java.util.Set<IceBridgeEvent.Category> categories =
        category == null
            ? java.util.Collections.emptySet()
            : java.util.Collections.singleton(category);
    String text = textFilter.getText();
    String peer = peerFilter.getText();
    List<IceBridgeEvent> out = new ArrayList<>();
    for (IceBridgeEvent e : all) {
      if (IceBridgeEventLog.matches(e, minLevel, categories, text, peer, 0)) {
        out.add(e);
      }
    }
    return out;
  }

  private void saveToFile() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(I18n.tr("Save IceBridge Console"));
    chooser.setFileFilter(new FileNameExtensionFilter(I18n.tr("Log files"), "log", "txt"));
    if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File file = chooser.getSelectedFile();
    String content = toText(model.getEvents());
    Thread writer =
        new Thread(
            () -> {
              try (BufferedWriter out =
                  new BufferedWriter(
                      new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                out.write(content);
              } catch (Exception e) {
                LOG.warn("Failed to save IceBridge console to " + file, e);
                SwingUtilities.invokeLater(
                    () ->
                        JOptionPane.showMessageDialog(
                            frame,
                            I18n.tr("Could not save the console log: ") + e.getMessage(),
                            I18n.tr("Save failed"),
                            JOptionPane.ERROR_MESSAGE));
              }
            },
            "icebridge-console-save");
    writer.setDaemon(true);
    writer.start();
  }

  private void copyToClipboard() {
    Toolkit.getDefaultToolkit()
        .getSystemClipboard()
        .setContents(new StringSelection(toText(model.getEvents())), null);
  }

  private static String toText(List<IceBridgeEvent> events) {
    StringBuilder sb = new StringBuilder();
    for (IceBridgeEvent e : events) {
      sb.append(TIME_FORMAT.format(new Date(e.timestampMs())))
          .append('\t')
          .append(e.level())
          .append('\t')
          .append(e.category())
          .append('\t')
          .append(e.shortPeerPub())
          .append('\t')
          .append(e.message())
          .append('\n');
    }
    return sb.toString();
  }

  /** Bounded, EDT-only table model over the currently filtered events. */
  private static final class EventTableModel extends AbstractTableModel {
    private final String[] columns = {
      I18n.tr("Time"), I18n.tr("Level"), I18n.tr("Category"), I18n.tr("Peer"), I18n.tr("Message")
    };
    private List<IceBridgeEvent> events = new ArrayList<>();

    void setEvents(List<IceBridgeEvent> events) {
      this.events = events;
      fireTableDataChanged();
    }

    List<IceBridgeEvent> getEvents() {
      return events;
    }

    @Override
    public int getRowCount() {
      return events.size();
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int column) {
      return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      IceBridgeEvent e = events.get(rowIndex);
      switch (columnIndex) {
        case 0:
          return TIME_FORMAT.format(new Date(e.timestampMs()));
        case 1:
          return e.level().name();
        case 2:
          return e.category().name();
        case 3:
          return e.shortPeerPub();
        default:
          return e.message();
      }
    }
  }
}
