/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.CopyrightLicenseBroker;
import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.util.BoundedFileWalker;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.dnd.FileOrFolderTransferHandler;
import com.limegroup.gnutella.gui.dnd.TransferHandlerDropTargetListener;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import net.miginfocom.swing.MigLayout;

/**
 * @author gubatron
 * @author aldenml
 */
@SuppressWarnings("serial")
public class CreateTorrentDialog extends JDialog {
  private static final PieceSize[] values = PieceSize.values();
  private static final Logger LOG = Logger.getLogger(CreateTorrentDialog.class);

  /** Tracker URLs with their tier, from the last validation. */
  private final List<Map.Entry<String, Integer>> trackers;

  private final Container container;
  private final JTabbedPane tabbedPane;
  private final JPanel basicTorrentPane;
  private final JPanel licensesPane;
  private final JPanel paymentsPane;
  private final JPanel advancedPane;
  private final CopyrightLicenseSelectorPanel licenseSelectorPanel;
  private final PaymentOptionsPanel paymentOptionsPanel;
  private final Dimension MINIMUM_DIALOG_DIMENSIONS = new Dimension(942, 750);
  private boolean create_from_dir;
  private String singlePath = null;
  private String directoryPath = null;
  private String dotTorrentSavePath = null;
  private boolean autoOpen = true;
  private File saveDir;
  private LimeTextField textSelectedContent;
  private JButton buttonSelectFile;

  /** Only used on MacOSX, as FileChooser does not support both File and Directory selection. */
  private JButton buttonSelectFolder;

  private JLabel labelTrackers;
  private JTextArea textTrackers;
  private JCheckBox checkStartSeeding;
  private JCheckBox checkUseDHT;
  private JCheckBox checkPrivate;
  private LimeTextField textComment;
  private JCheckBox checkModificationTimes;
  private JCheckBox checkSymlinks;
  private JCheckBox checkNoAttributes;
  private create_flags_t creationFlags;
  // Read on the EDT when Save is clicked, for the thread that makes the torrent.
  private boolean makePrivate;
  private String comment;
  private JTextArea textWebSeeds;
  private JButton buttonSaveAs;
  private JProgressBar progressBar;
  private JScrollPane textTrackersScrollPane;
  private String invalidTrackerURL;
  private JButton buttonClose;
  private JComboBox<PieceSize> pieceSizeComboBox;
  private int pieceSize;
  private JComboBox<TorrentType> torrentTypeComboBox;
  private TorrentType selectedTorrentType = TorrentType.HYBRID;
  private boolean chosenFolderIsEmpty;
  // Only the latest content choice is shown, if several are read at once.
  private final AtomicInteger contentChoices = new AtomicInteger();

  public CreateTorrentDialog(JFrame frame) {
    super(frame);
    // Choosing the content and saving the .torrent open a JFileChooser, which waits on the Windows
    // shell on the EDT (frostwire/frostwire#1411).
    ShellFolderWarmUp.warmUpForDirectoryChooser();
    trackers = new ArrayList<>();
    container = getContentPane();
    tabbedPane = new JTabbedPane();
    basicTorrentPane = new JPanel();
    licensesPane = new JPanel();
    paymentsPane = new JPanel();
    advancedPane = new JPanel();
    licenseSelectorPanel = new CopyrightLicenseSelectorPanel();
    paymentOptionsPanel = new PaymentOptionsPanel();
    initContainersLayouts();
    initComponents();
    setLocationRelativeTo(frame);
  }

  // Note: This dedup fix should be upstreamed to Entry.fromMap() in jlibtorrent.
  private static Entry entryFromMap(Map<?, ?> map) {
    entry e = new entry(entry.data_type.dictionary_t);
    string_entry_map d = e.dict();
    for (Object o : map.keySet()) {
      String k = (String) o;
      Object v = map.get(k);
      if (v instanceof String) {
        d.put(k, new entry((String) v));
      } else if (v instanceof Integer) {
        d.put(k, new entry((Integer) v));
      } else if (v instanceof Entry) {
        d.put(k, ((Entry) v).swig());
      } else if (v instanceof entry) {
        d.put(k, (entry) v);
      } else if (v instanceof List) {
        d.put(k, Entry.fromList((List<?>) v).swig());
      } else if (v instanceof Map) {
        d.put(k, entryFromMap((Map<?, ?>) v).swig());
      } else {
        d.put(k, new entry(v.toString()));
      }
    }
    return new Entry(e);
  }

  private void initContainersLayouts() {
    container.setLayout(new MigLayout("fill, insets 3px 3px 3px 3px"));
    basicTorrentPane.setLayout(new MigLayout("fill"));
    licensesPane.setLayout(new MigLayout("fill"));
    paymentsPane.setLayout(new MigLayout("fill"));
    advancedPane.setLayout(new MigLayout("fillx, wrap 1", "[]", "[]8[]8[]push"));
  }

  private void initTabbedPane() {
    container.add(tabbedPane, "grow, pushy, wrap");
    licensesPane.add(licenseSelectorPanel, "grow");
    paymentsPane.add(paymentOptionsPanel, "grow");
    tabbedPane.addTab("1. " + I18n.tr("Contents and Tracking"), basicTorrentPane);
    tabbedPane.addTab("2. " + I18n.tr("Copyright License"), licensesPane);
    tabbedPane.addTab("3. " + I18n.tr("Payments/Tips"), paymentsPane);
    tabbedPane.addTab("4. " + I18n.tr("Advanced"), advancedPane);
  }

  private void initComponents() {
    initDialogSettings();
    initTabbedPane();
    initProgressBar();
    initSaveCloseButtons();
    initTorrentContents();
    initTorrentTracking();
    initAdvancedOptions();
    buildListeners();
    // Its components take no drops (#1471); the dialog itself takes a dropped file or folder as
    // the torrent's content (#1474). Set after removeAll, which would clear it.
    DropTargets.removeAll(this);
    new DropTarget(
        this,
        new TransferHandlerDropTargetListener(
            new FileOrFolderTransferHandler(this::onContentDropped)));
    // Sized from its content: the tabs grew (private torrent, comment, piece size, torrent type,
    // the Advanced tab) and a fixed preferred size cut off the progress bar and the Save and Close
    // buttons until the dialog was resized by hand (frostwire/frostwire#1549).
    pack();
    Dimension size =
        initialSize(
            getSize(), MINIMUM_DIALOG_DIMENSIONS, usableScreenBounds(getGraphicsConfiguration()));
    setMinimumSize(size);
    setSize(size);
  }

  /**
   * The dialog's first size: what its content asks for, never smaller than {@code minimum}, and
   * never taller or wider than the screen's usable area.
   */
  static Dimension initialSize(Dimension packed, Dimension minimum, Rectangle screen) {
    int width = Math.max(packed.width, minimum.width);
    int height = Math.max(packed.height, minimum.height);
    if (screen != null && screen.width > 0 && screen.height > 0) {
      width = Math.min(width, screen.width);
      height = Math.min(height, screen.height);
    }
    return new Dimension(width, height);
  }

  private static Rectangle usableScreenBounds(GraphicsConfiguration gc) {
    if (gc == null) {
      return null;
    }
    Rectangle bounds = gc.getBounds();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
    return new Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom);
  }

  private void initDialogSettings() {
    setTitle(I18n.tr("Create New Torrent"));
    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    setModalityType(ModalityType.MODELESS);
    GUIUtils.addHideAction((JComponent) container);
  }

  private void initTorrentContents() {
    JPanel torrentContentsPanel = new JPanel(new MigLayout("fillx, wrap 1", "[]"));
    GUIUtils.setTitledBorderOnPanel(torrentContentsPanel, I18n.tr("Torrent Contents"));
    textSelectedContent = new LimeTextField();
    textSelectedContent.setEditable(false);
    textSelectedContent.setToolTipText(
        I18n.tr(
            "These box shows the contents you've selected for your new .torrent.\n"
                + "Either a file, or the contents of a folder."));
    torrentContentsPanel.add(textSelectedContent, "north, growx, gap 5 5 0 0, wrap");
    JPanel contentSelectionButtonsPanel =
        new JPanel(new MigLayout("ins 0, nogrid, fillx, wrap 1", ""));
    buttonSelectFile = new JButton(I18n.tr("Select File"));
    buttonSelectFile.setToolTipText(
        I18n.tr("Click here to select a single file as the content indexed by your new .torrent"));
    buttonSelectFolder = new JButton(I18n.tr("Select Folder"));
    buttonSelectFolder.setToolTipText(
        I18n.tr("Click here to select a folder as the content indexed by your new .torrent"));
    contentSelectionButtonsPanel.add(
        buttonSelectFile, "align right, width 175px, gaptop 5, gapright 5, gapbottom 5");
    contentSelectionButtonsPanel.add(
        buttonSelectFolder, "align right, width 175px, gaptop 5, gapright 5, gapbottom 5");
    torrentContentsPanel.add(
        contentSelectionButtonsPanel,
        "width 380px, align right, gaptop 5, gapright 5, gapbottom 5");
    basicTorrentPane.add(torrentContentsPanel, "growx, wrap");
  }

  private void initTorrentTracking() {
    JPanel torrentTrackingPanel = new JPanel(new MigLayout("fill"));
    GUIUtils.setTitledBorderOnPanel(torrentTrackingPanel, I18n.tr("Torrent Properties"));
    checkUseDHT = new JCheckBox(I18n.tr("Trackerless Torrent (DHT)"), true);
    checkUseDHT.setToolTipText(
        I18n.tr(
            "Select this option to create torrents that don't need trackers, completely"
                + " descentralized. (Recommended)"));
    torrentTrackingPanel.add(checkUseDHT, "align left, gapleft 5");
    checkStartSeeding = new JCheckBox(I18n.tr("Start seeding"), true);
    checkStartSeeding.setToolTipText(
        I18n.tr(
            "Announce yourself as a seed for the content indexed by this torrent as soon as it's"
                + " created.\n"
                + "If nobody is seeding the torrent, it won't work. (Recommended)"));
    torrentTrackingPanel.add(checkStartSeeding, "align right, gapright 10, wrap");
    checkPrivate = new JCheckBox(I18n.tr("Private torrent"), false);
    checkPrivate.setToolTipText(
        I18n.tr(
            "Peers come only from the trackers listed here: no DHT and no peer exchange. A private"
                + " torrent needs at least one tracker."));
    torrentTrackingPanel.add(checkPrivate, "align left, gapleft 5, wrap");
    labelTrackers =
        new JLabel(
            "<html><p>"
                + I18n.tr("Tracker Announce URLs")
                + "</p><p>("
                + I18n.tr("One tracker per line, a blank line starts a new tier")
                + ")</p></html>");
    labelTrackers.setToolTipText(
        I18n.tr(
            "Enter a list of valid BitTorrent Tracker Server URLs.\n"
                + "Your new torrent will be announced to these trackers if you start seeding the"
                + " torrent."));
    torrentTrackingPanel.add(
        labelTrackers, "aligny top, pushy, growx 40, gapleft 5, gapright 10, wmin 150px");
    textTrackers = new JTextArea(10, 70);
    ThemeMediator.fixKeyStrokes(textTrackers);
    textTrackers.setToolTipText(labelTrackers.getToolTipText());
    textTrackers.setLineWrap(false);
    textTrackers.setText(String.join("\n", DefaultTrackers.ANNOUNCE_URLS));
    textTrackersScrollPane = new JScrollPane(textTrackers);
    torrentTrackingPanel.add(
        textTrackersScrollPane,
        "gapright 5, gapleft 80, gapbottom 5, hmin 165px, growx 60, growy, wrap");
    JLabel _labelWebseeds = new JLabel(I18n.tr("Web Seeds Mirror URLs"));
    _labelWebseeds.setToolTipText(
        I18n.tr(
            "If these files can be downloaded from the web, enter the URLs of each possible mirror,"
                + " one per line (GetRight style)."));
    torrentTrackingPanel.add(
        _labelWebseeds, "aligny top, pushy, gapleft 5, gapright 10, wmin 150px");
    textWebSeeds = new JTextArea(4, 70);
    ThemeMediator.fixKeyStrokes(textWebSeeds);
    torrentTrackingPanel.add(
        new JScrollPane(textWebSeeds),
        "gapright 5, gapleft 80, gapbottom 5, hmin 165px, growx 60, growy, wrap");
    JLabel labelPieceSize = new JLabel(I18n.tr("Piece Size"));
    torrentTrackingPanel.add(
        labelPieceSize, "aligny top, pushy, gapleft 5, gapright 10, wmin 150px");
    pieceSizeComboBox = new JComboBox<>(values);
    torrentTrackingPanel.add(pieceSizeComboBox, "gapright 5, gapleft 80, width 175px, wrap");

    JLabel labelTorrentType = new JLabel(I18n.tr("Torrent Type"));
    labelTorrentType.setToolTipText(I18n.tr("Select the torrent format type to create"));
    torrentTrackingPanel.add(
        labelTorrentType, "aligny top, pushy, gapleft 5, gapright 10, wmin 150px");
    torrentTypeComboBox = new JComboBox<>(TorrentType.values());
    torrentTypeComboBox.setSelectedItem(TorrentType.HYBRID); // Default to hybrid
    torrentTypeComboBox.setToolTipText(
        I18n.tr("Hybrid torrents support both v1 and v2 protocols and are recommended"));
    torrentTrackingPanel.add(torrentTypeComboBox, "gapright 5, gapleft 80, width 175px, wrap");

    JLabel labelComment = new JLabel(I18n.tr("Comment"));
    labelComment.setToolTipText(
        I18n.tr("A note stored in the .torrent. Most BitTorrent clients show it."));
    torrentTrackingPanel.add(labelComment, "aligny top, gapleft 5, gapright 10, wmin 150px");
    textComment = new LimeTextField();
    textComment.setToolTipText(labelComment.getToolTipText());
    torrentTrackingPanel.add(textComment, "gapright 5, gapleft 80, growx");

    // suggest DHT by default
    updateTrackerRelatedControlsAvailability(true);
    basicTorrentPane.add(torrentTrackingPanel, "grow, push");
  }

  /** libtorrent 2's creation flags that change what the .torrent stores about the files. */
  private void initAdvancedOptions() {
    checkModificationTimes = new JCheckBox(I18n.tr("Store file modification times"), false);
    checkModificationTimes.setToolTipText(
        I18n.tr(
            "Saves each file's last-modified time in the .torrent, so the files downloaded get the"
                + " same dates."));
    checkSymlinks = new JCheckBox(I18n.tr("Keep symbolic links as links"), false);
    checkSymlinks.setToolTipText(
        I18n.tr(
            "Stores symbolic links in the .torrent as links, instead of the files they point to."));
    checkNoAttributes = new JCheckBox(I18n.tr("Leave out file attributes"), false);
    checkNoAttributes.setToolTipText(
        I18n.tr("Doesn't store whether each file is hidden or executable."));
    advancedPane.add(checkModificationTimes, "gapleft 5, gaptop 10");
    advancedPane.add(checkSymlinks, "gapleft 5");
    advancedPane.add(checkNoAttributes, "gapleft 5");
  }

  /**
   * The libtorrent creation flags for a torrent type and the Advanced options. {@code add_files}
   * reads the symlink and modification time flags as it lists the content, and {@code
   * create_torrent} reads them all.
   */
  static create_flags_t creationFlags(
      TorrentType type, boolean modificationTimes, boolean symlinks, boolean noAttributes) {
    create_flags_t flags = new create_flags_t();
    if (type == TorrentType.V1_ONLY) {
      flags = flags.or_(create_torrent.v1_only);
    } else if (type == TorrentType.V2_ONLY) {
      flags = flags.or_(create_torrent.v2_only);
    }
    if (modificationTimes) {
      flags = flags.or_(create_torrent.modification_time);
    }
    if (symlinks) {
      flags = flags.or_(create_torrent.symlinks);
    }
    if (noAttributes) {
      flags = flags.or_(create_torrent.no_attributes);
    }
    return flags;
  }

  private void initSaveCloseButtons() {
    JPanel buttonContainer = new JPanel();
    buttonContainer.setLayout(new MigLayout("fillx, insets 5 5 5 5"));
    // first button will dock all the way east,
    buttonSaveAs = new JButton(I18n.tr("Save torrent as..."));
    buttonContainer.add(buttonSaveAs, "pushx, alignx right, gapleft 5");
    // then this one will dock east (west of) the next to the existing component
    buttonClose = new JButton(I18n.tr("Close"));
    buttonContainer.add(buttonClose, "alignx right, gapright 5");
    container.add(buttonContainer, "alignx right, pushx");
  }

  private void initProgressBar() {
    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    container.add(progressBar, "growx, gap 5px 0, wrap");
  }

  private void buildListeners() {
    buttonSelectFile.addActionListener(arg0 -> onButtonSelectFile());
    buttonSelectFolder.addActionListener(e -> onButtonSelectFolder());
    buttonClose.addActionListener(this::onButtonClose);
    buttonSaveAs.addActionListener(arg0 -> onButtonSaveAs());
    checkPrivate.addItemListener(
        e -> {
          // A private torrent's peers come from its trackers only: it can't be trackerless.
          boolean isPrivate = checkPrivate.isSelected();
          if (isPrivate) {
            checkUseDHT.setSelected(false);
          }
          checkUseDHT.setEnabled(!isPrivate);
        });
    checkUseDHT.addChangeListener(
        arg0 -> {
          boolean useDHT = checkUseDHT.isSelected();
          updateTrackerRelatedControlsAvailability(useDHT);
        });
    textTrackers.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (checkUseDHT.isSelected()) {
              checkUseDHT.setSelected(false);
            }
          }
        });
    pieceSizeComboBox.addActionListener(
        e -> onPieceSizeSelected(pieceSizeComboBox.getSelectedIndex()));
    torrentTypeComboBox.addActionListener(
        e -> onTorrentTypeSelected((TorrentType) torrentTypeComboBox.getSelectedItem()));
  }

  private void onPieceSizeSelected(int selectedIndex) {
    if (selectedIndex > 0 && values.length > selectedIndex && values[selectedIndex] != null) {
      pieceSize = values[selectedIndex].bytes();
    }
  }

  private void onTorrentTypeSelected(TorrentType torrentType) {
    if (torrentType != null) {
      selectedTorrentType = torrentType;
      // Update the tooltip to show the description of the selected type
      torrentTypeComboBox.setToolTipText(torrentType.getDescription());
    }
  }

  private void updateTrackerRelatedControlsAvailability(boolean useDHT) {
    labelTrackers.setEnabled(!useDHT);
    textTrackers.setEnabled(!useDHT);
    textTrackersScrollPane.setEnabled(!useDHT);
    textTrackersScrollPane.getHorizontalScrollBar().setEnabled(!useDHT);
    textTrackersScrollPane.getVerticalScrollBar().setEnabled(!useDHT);
    labelTrackers.setForeground(useDHT ? Color.GRAY : Color.BLACK);
  }

  /**
   * Size of {@code file}, or of the files under it. Bounded by {@link BoundedFileWalker}, so a
   * symlink loop in the chosen folder can't recurse until the stack overflows.
   */
  static long calculateTotalSize(File file) {
    if (file.isFile()) {
      return file.length();
    }
    long[] total = {0};
    BoundedFileWalker.walk(file, (path, attrs) -> total[0] += attrs.size());
    return total[0];
  }

  private void onButtonClose(ActionEvent e) {
    GUIUtils.getDisposeAction().actionPerformed(e);
  }

  private void showFileChooser(final int fileFolderChoosingMode) {
    FileFilter directoryFilesAllowedFilter =
        new FileFilter() {
          @Override
          public boolean accept(File f) {
            return f.isDirectory() || f.isFile();
          }

          @Override
          public String getDescription() {
            String desc = I18n.tr("Select a single file or one directory");
            if (fileFolderChoosingMode == JFileChooser.FILES_ONLY) {
              desc = I18n.tr("Select a single file");
            } else if (fileFolderChoosingMode == JFileChooser.DIRECTORIES_ONLY) {
              desc = I18n.tr("Select a single directory");
            }
            return desc;
          }
        };
    File directory = FileChooserHandler.getLastInputDirectory();
    File chosenFile = null;
    if (fileFolderChoosingMode == JFileChooser.FILES_ONLY) {
      chosenFile =
          FileChooserHandler.getInputFile(
              GUIMediator.getAppFrame(),
              I18n.tr("Select a single file"),
              directory,
              directoryFilesAllowedFilter);
    } else if (fileFolderChoosingMode == JFileChooser.DIRECTORIES_ONLY) {
      chosenFile =
          FileChooserHandler.getInputDirectory(
              GUIMediator.getAppFrame(),
              I18n.tr("Select a single directory"),
              I18n.tr("Select folder"),
              directory,
              directoryFilesAllowedFilter);
    }
    if (chosenFile != null) {
      FileChooserHandler.setLastInputDirectory(chosenFile); // might not be necessary.
      setChosenContent(chosenFile, fileFolderChoosingMode);
    }
  }

  /**
   * What choosing a file or folder found on disk, read off the EDT by {@link #readChosenContent}.
   */
  record ChosenContent(
      File file,
      boolean readable,
      boolean directory,
      boolean emptyFolder,
      PieceSize recommendedPieceSize) {}

  /**
   * Picks {@code chosenFile} as the torrent's content: what it is on disk, and the piece size its
   * total size suggests, are read off the EDT. That reads every file under a folder, which froze
   * the dialog for large folders (frostwire/frostwire#1474).
   *
   * @param fileChoosingMode a JFileChooser mode; DIRECTORIES_ONLY turns a file into its folder
   */
  public void setChosenContent(File chosenFile, int fileChoosingMode) {
    int choice = contentChoices.incrementAndGet();
    BTDownloadMediator.readThenShow(
        () -> readChosenContent(chosenFile, fileChoosingMode),
        BackgroundQueuedExecutorService::schedule,
        GUIMediator::safeInvokeLater,
        content -> {
          if (choice == contentChoices.get()) {
            showChosenContent(content);
          }
        },
        e -> LOG.warn("Could not read the chosen content " + chosenFile, e));
  }

  /** Reads what {@code chosenFile} is on disk. Disk I/O: not on the EDT. */
  static ChosenContent readChosenContent(File chosenFile, int fileChoosingMode) {
    if (!chosenFile.canRead()) {
      return new ChosenContent(chosenFile, false, false, false, PieceSize.AUTO_DETECT);
    }
    File file =
        fileChoosingMode == JFileChooser.DIRECTORIES_ONLY && chosenFile.isFile()
            ? chosenFile.getParentFile()
            : chosenFile;
    File canonical;
    try {
      canonical = file.getCanonicalFile();
    } catch (IOException e) {
      canonical = file;
    }
    boolean directory = canonical.isDirectory();
    if (!directory && !canonical.isFile()) {
      return new ChosenContent(file, false, false, false, PieceSize.AUTO_DETECT);
    }
    String[] children = directory ? canonical.list() : null;
    boolean emptyFolder = children != null && children.length == 0;
    return new ChosenContent(
        file,
        true,
        directory,
        emptyFolder,
        PieceSize.recommendForSize(calculateTotalSize(canonical)));
  }

  private void showChosenContent(ChosenContent content) {
    if (!content.readable()) {
      textSelectedContent.setText(I18n.tr("Error: You can't read on that file/folder."));
      return;
    }
    String path = content.file().getAbsolutePath();
    create_from_dir = content.directory();
    directoryPath = content.directory() ? path : null;
    singlePath = content.directory() ? null : path;
    chosenFolderIsEmpty = content.emptyFolder();
    if (content.recommendedPieceSize() != PieceSize.AUTO_DETECT) {
      pieceSizeComboBox.setSelectedItem(content.recommendedPieceSize());
      pieceSize = content.recommendedPieceSize().bytes();
    }
    textSelectedContent.setText((content.directory() ? "[folder] " : "[file] ") + path);
  }

  /**
   * A file or folder dropped on the dialog becomes the torrent's content, as with Select File or
   * Select Folder. Nothing is built: the dialog stays open for the rest of the settings. Ignored
   * while a torrent is being made.
   */
  private void onContentDropped(File dropped) {
    if (!buttonSaveAs.isEnabled()) {
      return;
    }
    tabbedPane.setSelectedIndex(0);
    setChosenContent(dropped, JFileChooser.FILES_AND_DIRECTORIES);
    revertSaveCloseButtons();
  }

  private void onContentSelectionButton(int onContentSelectionButton) {
    showFileChooser(onContentSelectionButton);
    revertSaveCloseButtons();
  }

  private void onButtonSelectFile() {
    onContentSelectionButton(JFileChooser.FILES_ONLY);
  }

  private void onButtonSelectFolder() {
    onContentSelectionButton(JFileChooser.DIRECTORIES_ONLY);
  }

  private void onButtonSaveAs() {
    // Make sure a readable file or folder has been selected.
    if (singlePath == null && directoryPath == null) {
      JOptionPane.showMessageDialog(
          this,
          I18n.tr(
              "Please select a file or a folder.\nYour new torrent will need content to index."),
          I18n.tr("Something's missing"),
          JOptionPane.ERROR_MESSAGE);
      tabbedPane.setSelectedIndex(0);
      return;
    }
    // if user chose a folder that's empty (read with the content, off the EDT)
    if (directoryPath != null && chosenFolderIsEmpty) {
      JOptionPane.showMessageDialog(
          this,
          I18n.tr("The folder you selected is empty."),
          I18n.tr("Invalid Folder"),
          JOptionPane.ERROR_MESSAGE);
      tabbedPane.setSelectedIndex(0);
      return;
    }
    // if it's not tracker-less make sure we have valid tracker urls
    boolean useTrackers = !checkUseDHT.isSelected();
    if (useTrackers) {
      if (!validateAndFixTrackerURLS()) {
        if (invalidTrackerURL == null) {
          invalidTrackerURL = "";
        }
        JOptionPane.showMessageDialog(
            this,
            I18n.tr("Check again your tracker URL(s).\n" + invalidTrackerURL),
            I18n.tr("Invalid Tracker URL\n"),
            JOptionPane.ERROR_MESSAGE);
        return;
      }
    } else {
      trackers.clear();
    }
    // Whether or not to start seeding this torrent right away
    autoOpen = checkStartSeeding.isSelected();
    makePrivate = checkPrivate.isSelected();
    creationFlags =
        creationFlags(
            selectedTorrentType,
            checkModificationTimes.isSelected(),
            checkSymlinks.isSelected(),
            checkNoAttributes.isSelected());
    comment = textComment.getText().trim();
    // show save as dialog
    if (!showSaveAsDialog()) {
      return;
    }
    BackgroundQueuedExecutorService.schedule(
        () -> {
          if (makeTorrent()) {
            revertSaveCloseButtons();
            progressBar.setString(I18n.tr("Torrent Created."));
            SwingUtilities.invokeLater(CreateTorrentDialog.this::dispose);
            if (autoOpen) {
              SwingUtilities.invokeLater(
                  () ->
                      GUIMediator.instance()
                          .openTorrentForSeed(new File(dotTorrentSavePath), saveDir));
            }
          }
        });
  }

  private boolean showSaveAsDialog() {
    FileFilter saveAsFilter =
        new FileFilter() {
          @Override
          public String getDescription() {
            return I18n.tr("Torrent File");
          }

          @Override
          public boolean accept(File f) {
            return f.getName().endsWith(".torrent");
          }
        };
    File torrContents = (create_from_dir) ? new File(directoryPath) : new File(singlePath);
    File suggestedFile =
        new File(SharingSettings.DEFAULT_TORRENTS_DIR, torrContents.getName() + ".torrent");
    File chosenFile =
        FileChooserHandler.getSaveAsFile(I18n.tr("Save .torrent"), suggestedFile, saveAsFilter);
    if (chosenFile == null) {
      dotTorrentSavePath = null;
      return false;
    }
    dotTorrentSavePath = chosenFile.getAbsolutePath();
    if (!dotTorrentSavePath.endsWith(".torrent")) {
      dotTorrentSavePath = dotTorrentSavePath + ".torrent";
    }
    return true;
  }

  private boolean validateAndFixTrackerURLS() {
    ParsedTrackers parsed = parseTrackers(textTrackers.getText());
    if (parsed.invalid() != null) {
      invalidTrackerURL = parsed.invalid();
      return false;
    }
    textTrackers.setText(formatTrackers(parsed.trackers()));
    trackers.clear();
    trackers.addAll(parsed.trackers());
    invalidTrackerURL = null;
    return true;
  }

  /** Tracker URLs with their tiers, or the first one that isn't valid ("" when there are none). */
  record ParsedTrackers(List<Map.Entry<String, Integer>> trackers, String invalid) {}

  private static final Pattern TRACKER_URL =
      Pattern.compile("^(https?|udp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");

  /**
   * Reads the trackers typed one per line. A blank line starts the next tier: clients try a tier's
   * trackers before the next tier's. A URL without a scheme gets {@code http://}.
   */
  static ParsedTrackers parseTrackers(String text) {
    List<Map.Entry<String, Integer>> parsed = new ArrayList<>();
    int tier = 0;
    boolean tierHasTrackers = false;
    for (String line : (text == null ? "" : text).split("\n", -1)) {
      String url = line.trim();
      if (url.isEmpty()) {
        if (tierHasTrackers) {
          tier++;
          tierHasTrackers = false;
        }
        continue;
      }
      if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("udp://")) {
        url = "http://" + url;
      }
      if (!TRACKER_URL.matcher(url).matches()) {
        return new ParsedTrackers(List.of(), url);
      }
      parsed.add(Map.entry(url, tier));
      tierHasTrackers = true;
    }
    return parsed.isEmpty() ? new ParsedTrackers(List.of(), "") : new ParsedTrackers(parsed, null);
  }

  /** The trackers one per line, with a blank line between tiers. */
  static String formatTrackers(List<Map.Entry<String, Integer>> trackers) {
    StringBuilder text = new StringBuilder();
    Integer tier = null;
    for (Map.Entry<String, Integer> tracker : trackers) {
      if (tier != null && !tier.equals(tracker.getValue())) {
        text.append('\n');
      }
      tier = tracker.getValue();
      text.append(tracker.getKey()).append('\n');
    }
    return text.toString();
  }

  private boolean makeTorrent() {
    boolean result;
    disableSaveCloseButtons();
    File f = new File((create_from_dir) ? directoryPath : singlePath);
    try {
      reportCurrentTask(I18n.tr("Adding files..."));
      // libtorrent 2.1 lists the files first and creates the torrent from that list. The listener
      // is a director: only delete() frees it, and so the vector once the torrent has its copy.
      add_files_listener acceptAll =
          new add_files_listener() {
            @Override
            public boolean pred(String p) {
              return true;
            }
          };
      create_file_entry_vector files;
      try {
        files = libtorrent.list_files_ex(f.getPath(), acceptAll, creationFlags);
      } finally {
        acceptAll.delete();
      }
      create_torrent torrent;
      try {
        torrent = new create_torrent(files, pieceSize, creationFlags);
      } finally {
        files.delete();
      }
      torrent.set_priv(makePrivate);
      if (comment != null && !comment.isEmpty()) {
        torrent.set_comment(comment);
      }
      torrent.set_creator(
          "FrostWire "
              + FrostWireUtils.getFrostWireVersion()
              + " build "
              + FrostWireUtils.getBuildNumber());
      if (trackers != null && !trackers.isEmpty()) {
        reportCurrentTask(I18n.tr("Adding trackers..."));
        for (Map.Entry<String, Integer> tracker : trackers) {
          torrent.add_tracker(tracker.getKey(), tracker.getValue());
        }
      } else {
        // Trackerless: give clients that start with an empty DHT somewhere to start (BEP 5).
        for (Map.Entry<String, Integer> node : dhtNodes(BTEngine.dhtBootstrapNodes())) {
          torrent.add_node(new string_int_pair(node.getKey(), node.getValue()));
        }
      }
      if (addAvailableWebSeeds(torrent, create_from_dir)) {
        reportCurrentTask(I18n.tr("Calculating piece hashes..."));
        saveDir = f.getParentFile();
        error_code ec = new error_code();
        // Never pass a null listener: jlibtorrent 2.0.12.3 crashed on it.
        IntConsumer hashed = hashingPercentReporter(torrent.num_pieces(), this::reportHashing);
        set_piece_hashes_listener progressListener =
            new set_piece_hashes_listener() {
              @Override
              public void progress(int pieceIndex) {
                hashed.accept(pieceIndex);
              }
            };
        libtorrent.set_piece_hashes_posix_disk_io(
            torrent, saveDir.getAbsolutePath(), progressListener, ec);
        reportCurrentTask(I18n.tr("Generating torrent entry..."));
        Entry entry = new Entry(torrent.generate());
        Map<String, Entry> entryMap = entry.dictionary();
        addAvailablePaymentOptions(entryMap);
        addAvailableCopyrightLicense(entryMap);
        final File torrent_file = new File(dotTorrentSavePath);
        reportCurrentTask(I18n.tr("Saving torrent to disk..."));
        Entry entryFromUpdatedMap = entryFromMap(entryMap);
        final byte[] bencoded_torrent_bytes = entryFromUpdatedMap.bencode();
        FileOutputStream fos = new FileOutputStream(torrent_file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        bos.write(bencoded_torrent_bytes);
        bos.flush();
        bos.close();
        result = true;
        reportCurrentTask("");
      } else {
        result = false;
        revertSaveCloseButtons();
        textWebSeeds.selectAll();
      }
    } catch (Throwable e) {
      result = false;
      revertSaveCloseButtons();
      LOG.error(e.getMessage(), e);
      reportCurrentTask(I18n.tr("Operation failed."));
    }
    return result;
  }

  private boolean addAvailableWebSeeds(create_torrent torrent, boolean isMultiFile) {
    boolean result = true;
    if (textWebSeeds.getText().length() > 0) {
      List<String> mirrors = Arrays.asList(textWebSeeds.getText().split("\n"));
      if (!mirrors.isEmpty()) {
        // check just the first file on all mirrors.
        reportCurrentTask(I18n.tr("Checking Web seed mirror URLs..."));
        for (String mirror : mirrors) {
          if (isMultiFile && !mirror.endsWith("/")) {
            fixWebSeedMirrorUrl(mirror);
          }
          if (!checkWebSeedMirror(mirror, torrent, isMultiFile)) {
            result = false;
            showWebseedsErrorMessage(
                new Exception(
                    getWebSeedTestPath(mirror, torrent, isMultiFile)
                        + " "
                        + I18n.tr("Web seed not reachable.")));
            break;
          }
        }
        if (result) {
          for (String mirror : mirrors) {
            torrent.add_url_seed(mirror);
          }
          result = true;
        }
      }
    }
    return result;
  }

  private void fixWebSeedMirrorUrl(final String mirror) {
    GUIMediator.safeInvokeLater(
        () -> {
          String text = textWebSeeds.getText();
          textWebSeeds.setText(text.replaceAll(mirror, mirror + "/"));
        });
  }

  /**
   * Sends HEAD request to the mirror location along with the test path to see if the file exists.
   * Read http://getright.com/seedtorrent.html to find out how mirror urls are interpreted
   */
  private boolean checkWebSeedMirror(String mirror, create_torrent torrent, boolean isMultiFile) {
    String urlPath = getWebSeedTestPath(mirror, torrent, isMultiFile);
    HttpClient browser = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
    int responseCode = 500;
    try {
      responseCode = browser.head(urlPath, 2000, null);
      System.out.println(responseCode + ": " + urlPath);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return responseCode == 200;
  }

  private String getWebSeedTestPath(String mirror, create_torrent torrent, boolean isMultiFile) {
    String urlPath;
    // fix mirror
    if (isMultiFile && !mirror.endsWith("/")) {
      mirror = mirror + "/";
    }
    if (isMultiFile) {
      // path should be <http://mirror-url/> + torrentName + "/" + relativeFilePath. libtorrent
      // 2.1's
      // file entries carry that path, starting with the torrent's name.
      urlPath = mirror + torrent.file_at(0).getFilename();
    } else {
      // url-list should point straight to the file.
      urlPath = mirror;
    }
    return urlPath;
  }

  private void addAvailableCopyrightLicense(final Map<String, Entry> entryMap) {
    if (licenseSelectorPanel.hasConfirmedRightfulUseOfLicense()) {
      CopyrightLicenseBroker license = licenseSelectorPanel.getLicenseBroker();
      if (license != null) {
        final Map<String, Entry> info = entryMap.get("info").dictionary();
        info.put("license", Entry.fromMap(license.asMap()));
        entryMap.put("info", Entry.fromMap(info));
      }
    }
  }

  private void addAvailablePaymentOptions(final Map<String, Entry> entryMap) {
    if (paymentOptionsPanel.hasPaymentOptions()) {
      PaymentOptions paymentOptions = paymentOptionsPanel.getPaymentOptions();
      if (paymentOptions != null) {
        final Map<String, Entry> info = entryMap.get("info").dictionary();
        info.put("paymentOptions", Entry.fromMap(paymentOptions.asMap()));
        entryMap.put("info", Entry.fromMap(info));
      }
    }
  }

  private void revertSaveCloseButtons() {
    buttonSaveAs.setText(I18n.tr("Save torrent as..."));
    buttonSaveAs.setEnabled(true);
    buttonClose.setEnabled(true);
  }

  /**
   * Not sure if we need to implement this, I suppose this changed one of the buttons of the wizard
   * from next|cancel to close
   */
  private void disableSaveCloseButtons() {
    SwingUtilities.invokeLater(
        () -> {
          buttonSaveAs.setText(I18n.tr("Saving Torrent..."));
          buttonSaveAs.setEnabled(false);
          buttonClose.setEnabled(false);
        });
  }

  private void showWebseedsErrorMessage(Exception webSeedsException) {
    final Exception e = webSeedsException;
    reportCurrentTask(e.getMessage());
    GUIMediator.safeInvokeLater(() -> GUIMediator.showError(e.getMessage()));
  }

  private void reportCurrentTask(final String task_description) {
    SwingUtilities.invokeLater(() -> progressBar.setString(task_description));
  }

  /** {@code host:port} entries separated by commas, as pairs; malformed entries are skipped. */
  static List<Map.Entry<String, Integer>> dhtNodes(String hostPorts) {
    List<Map.Entry<String, Integer>> nodes = new ArrayList<>();
    for (String hostPort : hostPorts.split(",")) {
      int colon = hostPort.lastIndexOf(':');
      if (colon <= 0 || colon == hostPort.length() - 1) {
        continue;
      }
      try {
        int port = Integer.parseInt(hostPort.substring(colon + 1).trim());
        if (port > 0 && port <= 65535) {
          nodes.add(Map.entry(hostPort.substring(0, colon).trim(), port));
        }
      } catch (NumberFormatException ignored) {
        // Not a port.
      }
    }
    return nodes;
  }

  /** Shows how much of the content has been hashed; called from the hashing thread. */
  private void reportHashing(int percent) {
    SwingUtilities.invokeLater(
        () -> {
          progressBar.setValue(percent);
          progressBar.setString(I18n.tr("Calculating piece hashes...") + " " + percent + "%");
        });
  }

  /**
   * Turns libtorrent's hashing progress (the index of each piece hashed) into percentages for
   * {@code onPercent}, called only when the percentage changes: content with many pieces would
   * otherwise post one EDT update per piece.
   */
  static IntConsumer hashingPercentReporter(int numPieces, IntConsumer onPercent) {
    int[] last = {-1};
    return pieceIndex -> {
      if (numPieces <= 0) {
        return;
      }
      int percent = (int) Math.min(100, (pieceIndex + 1L) * 100 / numPieces);
      if (percent != last[0]) {
        last[0] = percent;
        onPercent.accept(percent);
      }
    };
  }

  enum PieceSize {
    AUTO_DETECT(0, "(" + I18n.tr("Auto Detect") + ")"),
    _16KB(16),
    _32KB(32),
    _64KB(64),
    _128KB(128),
    _256KB(256),
    _512KB(512),
    _1024KB(1024),
    _2048KB(2048),
    _4096KB(4096),
    _8192KB(8192),
    _16384KB(16384),
    _32768KB(32768),
    _65536KB(65536),
    _131072KB(131072);
    private final int kb;
    private final String humanRep;

    PieceSize(int k, String rep) {
      kb = k;
      humanRep = rep;
    }

    PieceSize(int k) {
      this(k, k >= 1024 ? (k / 1024) + " MB" : k + " kB");
    }

    int bytes() {
      return kb * 1024;
    }

    @Override
    public String toString() {
      return humanRep;
    }

    static PieceSize recommendForSize(long totalBytes) {
      long minPieces = 1000;
      long maxPieces = 3000;
      long targetPieces = Math.max(minPieces, Math.min(maxPieces, totalBytes / (1024 * 1024)));
      long targetPieceBytes = totalBytes / targetPieces;
      long targetKB = Math.max(16, targetPieceBytes / 1024);

      PieceSize best = AUTO_DETECT;
      for (PieceSize size : values()) {
        if (size == AUTO_DETECT) continue;
        if (size.kb <= targetKB) {
          best = size;
        } else {
          break;
        }
      }
      return best;
    }
  }

  /*
  public static void main(String[] args) {

      if (OSUtils.isMacOSX()) {
          System.setProperty("apple.laf.useScreenMenuBar", "true");
          System.setProperty("com.apple.eawt.CocoaComponent.CompatibilityMode", "false");
      }

     // ThemeMediator.loadDefaultTheme();

      try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }catch(Exception ex) {
          ex.printStackTrace();
      }

      CreateTorrentDialog dlg = new CreateTorrentDialog(null);
      dlg.setVisible(true);
      dlg.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
              System.out.println("End of Test");
              System.out.println("Stopped");
              System.exit(0);
          }
      });
  }
  */
}
