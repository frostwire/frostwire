/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityLifecycle;
import com.frostwire.search.relay.KarmaChainTable;
import com.frostwire.search.relay.KarmaConstants;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RelayConstants;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.FileChooserHandler;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchEngine;
import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.limewire.util.CommonUtils;

/**
 * Desktop UI for identity lifecycle. Business logic lives in
 * {@link IdentityLifecycle} (shared with Android).
 */
public final class IdentitySettingsPaneItem extends AbstractPaneItem {

  private static final Logger LOG = Logger.getLogger(IdentitySettingsPaneItem.class);

  private static SearchEngine distributedEngine() {
    return SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
  }

  private static SearchEngine localEngine() {
    return SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
  }

  /**
   * Same path as {@code Initializer.startRelayStack}: {@code settingsDir/libtorrent/identity.dat}.
   */
  static File identityFile() {
    return RelayConstants.identityFile(CommonUtils.getUserSettingsDir());
  }

  private static final String TITLE = I18n.tr("Identity");
  private static final String LABEL =
      I18n.tr(
          "Your FrostWire identity is a cryptographic keypair that uniquely identifies you on the distributed search network. "
              + "Back it up using the seed phrase to avoid losing your identity and karma reputation.");

  private final JLabel FINGERPRINT_LABEL = new JLabel("—");
  private final JLabel NODE_ID_LABEL = new JLabel("—");
  private final JLabel PUBKEY_LABEL = new JLabel("—");
  private final JLabel KARMA_LABEL = new JLabel("—");
  private final JLabel DIFFICULTY_LABEL = new JLabel("—");
  private final JLabel SHARED_COUNT_LABEL = new JLabel("—");

  private final JButton INITIALIZE_BUTTON = new JButton(I18n.tr("Initialize Identity"));
  private final JButton SHOW_SEED_BUTTON = new JButton(I18n.tr("Show Seed Phrase"));
  private final JButton ENTER_SEED_BUTTON = new JButton(I18n.tr("Restore from Seed Phrase"));
  private final JButton EXPORT_BUTTON = new JButton(I18n.tr("Export Identity File"));
  private final JButton IMPORT_BUTTON = new JButton(I18n.tr("Import Identity File"));
  private final JButton COPY_FINGERPRINT_BUTTON = new JButton(I18n.tr("Copy"));
  private final JProgressBar GENERATE_PROGRESS = new JProgressBar();
  private volatile boolean generating;

  public IdentitySettingsPaneItem() {
    super(TITLE, LABEL);

    add(buildInfoPanel());
    add(getHorizontalSeparator());
    add(buildActionsPanel());
    add(getVerticalSeparator());

    INITIALIZE_BUTTON.addActionListener(e -> initializeIdentity());
    SHOW_SEED_BUTTON.addActionListener(e -> showSeedPhrase());
    ENTER_SEED_BUTTON.addActionListener(e -> restoreFromSeedPhrase());
    EXPORT_BUTTON.addActionListener(e -> exportIdentityFile());
    IMPORT_BUTTON.addActionListener(e -> importIdentityFile());
    COPY_FINGERPRINT_BUTTON.addActionListener(e -> copyFingerprint());

    GENERATE_PROGRESS.setIndeterminate(true);
    GENERATE_PROGRESS.setStringPainted(true);
    GENERATE_PROGRESS.setString(I18n.tr("Mining proof-of-work identity…"));
    GENERATE_PROGRESS.setVisible(false);
  }

  private JPanel buildInfoPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.WEST;
    c.insets = new Insets(2, 4, 2, 4);

    addInfoRow(panel, c, 0, I18n.tr("Node ID:"), NODE_ID_LABEL);
    addInfoRow(panel, c, 1, I18n.tr("Fingerprint:"), FINGERPRINT_LABEL);
    JPanel fpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    fpPanel.add(COPY_FINGERPRINT_BUTTON);
    c.gridx = 2;
    c.gridy = 1;
    c.weightx = 0;
    panel.add(fpPanel, c);

    addInfoRow(panel, c, 2, I18n.tr("Public Key:"), PUBKEY_LABEL);
    addInfoRow(panel, c, 3, I18n.tr("Karma Score:"), KARMA_LABEL);
    addInfoRow(panel, c, 4, I18n.tr("Identity Difficulty:"), DIFFICULTY_LABEL);
    addInfoRow(panel, c, 5, I18n.tr("Shared Torrents:"), SHARED_COUNT_LABEL);

    return panel;
  }

  private void addInfoRow(
      JPanel panel, GridBagConstraints c, int row, String label, JComponent value) {
    c.gridx = 0;
    c.gridy = row;
    c.weightx = 0;
    JLabel l = new JLabel(label);
    l.setFont(l.getFont().deriveFont(Font.BOLD));
    panel.add(l, c);
    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    panel.add(value, c);
    c.fill = GridBagConstraints.NONE;
  }

  private JPanel buildActionsPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 6));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    buttons.add(INITIALIZE_BUTTON);
    buttons.add(SHOW_SEED_BUTTON);
    buttons.add(ENTER_SEED_BUTTON);
    buttons.add(EXPORT_BUTTON);
    buttons.add(IMPORT_BUTTON);
    panel.add(buttons, BorderLayout.NORTH);
    panel.add(GENERATE_PROGRESS, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public void initOptions() {
    refreshIdentityInfo();
  }

  private void refreshIdentityInfo() {
    IdentityKeys identity = resolveIdentity();
    boolean hasIdentity = identity != null;
    INITIALIZE_BUTTON.setEnabled(!generating);
    INITIALIZE_BUTTON.setVisible(true);
    INITIALIZE_BUTTON.setText(
        hasIdentity ? I18n.tr("Create New Identity") : I18n.tr("Initialize Identity"));
    SHOW_SEED_BUTTON.setEnabled(hasIdentity && !generating);
    EXPORT_BUTTON.setEnabled(hasIdentity && !generating);
    ENTER_SEED_BUTTON.setEnabled(!generating);
    IMPORT_BUTTON.setEnabled(!generating);

    if (identity == null) {
      FINGERPRINT_LABEL.setText(I18n.tr("(not initialized)"));
      NODE_ID_LABEL.setText(I18n.tr("(not initialized)"));
      PUBKEY_LABEL.setText(I18n.tr("(not initialized)"));
      KARMA_LABEL.setText(I18n.tr("(not available)"));
      DIFFICULTY_LABEL.setText(I18n.tr("(not available)"));
      SHARED_COUNT_LABEL.setText(I18n.tr("(not available)"));
      return;
    }

    byte[] nodeId = identity.nodeId();
    byte[] pubRaw = identity.ed25519PubRaw();

    NODE_ID_LABEL.setText(Hex.encode(nodeId));
    FINGERPRINT_LABEL.setText(IdentityLifecycle.formatGroupedHex(pubRaw));
    PUBKEY_LABEL.setText(Hex.encode(pubRaw));

    int difficulty = IdentityLifecycle.difficultyBits(identity);
    DIFFICULTY_LABEL.setText(
        difficulty
            + " bits"
            + (IdentityLifecycle.meetsDifficultyRequirement(identity)
                ? " (meets requirement)"
                : " (below required " + KarmaConstants.IDENTITY_DIFFICULTY + ")"));

    KARMA_LABEL.setText(getKarmaScore(identity) + " endorsements");
    SHARED_COUNT_LABEL.setText(getSharedCount() + " torrents");
  }

  private IdentityKeys resolveIdentity() {
    return IdentityLifecycle.resolve(distributedEngine().identityKeys(), identityFile());
  }

  private void initializeIdentity() {
    if (generating) {
      return;
    }
    boolean replacing = resolveIdentity() != null;

    int confirm =
        JOptionPane.showConfirmDialog(
            GUIMediator.getAppFrame(),
            "<html>"
                + I18n.tr(
                    replacing
                        ? "Creating a new random identity will permanently replace your current node ID and karma identity. "
                            + "FrostWire will save a backup copy first, but you should also export your identity or seed phrase. "
                            + "FrostWire will restart when finished."
                        : "Generate a new FrostWire identity with proof-of-work ({0} leading zero bits). "
                            + "This may take a few seconds. FrostWire will restart when finished so the "
                            + "relay stack can load the new keys.",
                    String.valueOf(KarmaConstants.IDENTITY_DIFFICULTY))
                + "</html>",
            I18n.tr(replacing ? "Create New Identity" : "Initialize Identity"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) {
      return;
    }

    generating = true;
    GENERATE_PROGRESS.setVisible(true);
    refreshIdentityInfo();

    DesktopParallelExecutor.execute(
        () -> {
          try {
            IdentityKeys keys =
                IdentityLifecycle.generateAndInstall(
                    identityFile(), KarmaConstants.IDENTITY_DIFFICULTY);
            GUIMediator.safeInvokeLater(
                () -> {
                  generating = false;
                  GENERATE_PROGRESS.setVisible(false);
                  LOG.info(
                      "Identity initialized, nodeId="
                          + Hex.encode(keys.nodeId())
                          + " path="
                          + identityFile().getAbsolutePath());
                  JOptionPane.showMessageDialog(
                      GUIMediator.getAppFrame(),
                      I18n.tr(
                          "Identity created successfully. FrostWire will now restart to load it into the relay stack."),
                      I18n.tr("Restart Required"),
                      JOptionPane.INFORMATION_MESSAGE);
                  GUIMediator.shutdown();
                });
          } catch (Throwable t) {
            GUIMediator.safeInvokeLater(
                () -> {
                  generating = false;
                  GENERATE_PROGRESS.setVisible(false);
                  LOG.error("Failed to initialize identity", t);
                  GUIMediator.showError(
                      I18n.tr("Failed to initialize identity: ") + t.getMessage());
                  refreshIdentityInfo();
                });
          }
        });
  }

  private long getKarmaScore(IdentityKeys identity) {
    try {
      File homeDir = CommonUtils.getUserSettingsDir();
      File karmaDb = new File(homeDir, "frostwire-karma.db");
      if (!karmaDb.exists()) {
        return 0;
      }
      try (KarmaChainTable table = KarmaChainTable.open(karmaDb)) {
        var chain = table.loadChain(identity.ed25519PubRaw());
        return PeerKarmaCache.computeScore(chain.entries());
      }
    } catch (Throwable t) {
      LOG.warn("Failed to read karma score", t);
      return 0;
    }
  }

  private int getSharedCount() {
    try {
      var index = localEngine().getLocalIndex();
      if (index != null) {
        return index.size();
      }
    } catch (Throwable ignored) {
    }
    return 0;
  }

  private void showSeedPhrase() {
    IdentityKeys identity = resolveIdentity();
    if (identity == null) {
      GUIMediator.showError(I18n.tr("No identity loaded."));
      return;
    }

    try {
      String mnemonic = IdentityLifecycle.seedPhrase(identity);

      JTextArea textArea = new JTextArea(mnemonic);
      textArea.setEditable(false);
      textArea.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      textArea.setBackground(new Color(255, 255, 240));

      JLabel warning =
          new JLabel(
              "<html><b>"
                  + I18n.tr("WARNING:")
                  + "</b> "
                  + I18n.tr(
                      "Anyone with this phrase can impersonate you and access your karma reputation. "
                          + "Write it down on paper and store it securely. Never share it with anyone.")
                  + "</html>");

      JPanel panel = new JPanel(new BorderLayout(0, 10));
      panel.add(warning, BorderLayout.NORTH);
      panel.add(new JScrollPane(textArea), BorderLayout.CENTER);

      JButton copyButton = new JButton(I18n.tr("Copy to Clipboard"));
      copyButton.addActionListener(
          e -> {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(mnemonic), null);
            copyButton.setText(I18n.tr("Copied!"));
          });
      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      buttonPanel.add(copyButton);
      panel.add(buttonPanel, BorderLayout.SOUTH);

      JOptionPane.showMessageDialog(
          GUIMediator.getAppFrame(),
          panel,
          I18n.tr("Your Seed Phrase (24 words)"),
          JOptionPane.PLAIN_MESSAGE);
    } catch (Throwable t) {
      LOG.error("Failed to generate seed phrase", t);
      GUIMediator.showError(I18n.tr("Failed to generate seed phrase: ") + t.getMessage());
    }
  }

  private void restoreFromSeedPhrase() {
    int confirm =
        JOptionPane.showConfirmDialog(
            GUIMediator.getAppFrame(),
            "<html>"
                + I18n.tr(
                    "Restoring from a seed phrase will <b>permanently replace</b> your current identity, "
                        + "including your karma reputation and node ID. This cannot be undone.<br><br>"
                        + "Make sure you have backed up your current identity first.<br><br>"
                        + "FrostWire will need to restart after restoring.")
                + "</html>",
            I18n.tr("Confirm Identity Replacement"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) {
      return;
    }

    String mnemonic =
        JOptionPane.showInputDialog(
            GUIMediator.getAppFrame(),
            I18n.tr(
                "Enter your 24-word seed phrase. Words may be separated by spaces, commas, or line breaks. "
                    + "Uppercase letters are accepted."),
            I18n.tr("Restore Identity"),
            JOptionPane.PLAIN_MESSAGE);
    if (mnemonic == null || mnemonic.trim().isEmpty()) {
      return;
    }

    try {
      IdentityLifecycle.restoreFromSeedPhrase(mnemonic, identityFile());

      JOptionPane.showMessageDialog(
          GUIMediator.getAppFrame(),
          I18n.tr(
              "Identity restored successfully. FrostWire will now restart to apply the new identity."),
          I18n.tr("Restart Required"),
          JOptionPane.INFORMATION_MESSAGE);

      GUIMediator.shutdown();
    } catch (Throwable t) {
      LOG.error("Failed to restore identity from seed phrase", t);
      GUIMediator.showError(I18n.tr("Failed to restore identity: ") + t.getMessage());
    }
  }

  private void exportIdentityFile() {
    IdentityKeys identity = resolveIdentity();
    if (identity == null) {
      GUIMediator.showError(I18n.tr("No identity loaded."));
      return;
    }

    File selected =
        FileChooserHandler.getSaveAsFile(
            "Export Identity",
            new File(FileChooserHandler.getLastInputDirectory(), "frostwire-identity.dat"),
            new FileNameExtensionFilter(I18n.tr("FrostWire Identity File"), "dat"));
    if (selected == null) {
      return;
    }

    try {
      IdentityLifecycle.exportToFile(identity, selected);
      JOptionPane.showMessageDialog(
          GUIMediator.getAppFrame(),
          I18n.tr("Identity exported to:") + "\n" + selected.getAbsolutePath(),
          I18n.tr("Export Complete"),
          JOptionPane.INFORMATION_MESSAGE);
    } catch (Throwable t) {
      LOG.error("Failed to export identity", t);
      GUIMediator.showError(I18n.tr("Failed to export identity: ") + t.getMessage());
    }
  }

  private void importIdentityFile() {
    int confirm =
        JOptionPane.showConfirmDialog(
            GUIMediator.getAppFrame(),
            "<html>"
                + I18n.tr(
                    "Importing an identity file will <b>permanently replace</b> your current identity. "
                        + "This cannot be undone. FrostWire will need to restart after importing.")
                + "</html>",
            I18n.tr("Confirm Identity Replacement"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) {
      return;
    }

    File selected =
        FileChooserHandler.getInputFile(
            GUIMediator.getAppFrame(),
            new FileNameExtensionFilter(I18n.tr("FrostWire Identity File"), "dat"));
    if (selected == null) {
      return;
    }

    try {
      IdentityLifecycle.importFromFile(selected, identityFile());

      JOptionPane.showMessageDialog(
          GUIMediator.getAppFrame(),
          I18n.tr("Identity imported successfully. FrostWire will now restart."),
          I18n.tr("Restart Required"),
          JOptionPane.INFORMATION_MESSAGE);

      GUIMediator.shutdown();
    } catch (Throwable t) {
      LOG.error("Failed to import identity", t);
      GUIMediator.showError(I18n.tr("Failed to import identity: ") + t.getMessage());
    }
  }

  private void copyFingerprint() {
    IdentityKeys identity = resolveIdentity();
    if (identity == null) return;
    String fp = Hex.encode(identity.ed25519PubRaw());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fp), null);
    COPY_FINGERPRINT_BUTTON.setText(I18n.tr("Copied!"));
    Timer timer = new Timer(1500, e -> COPY_FINGERPRINT_BUTTON.setText(I18n.tr("Copy")));
    timer.setRepeats(false);
    timer.start();
  }

  @Override
  public boolean applyOptions() {
    return true;
  }

  @Override
  public boolean isDirty() {
    return false;
  }
}
