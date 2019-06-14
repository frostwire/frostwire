/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.CopyrightLicenseBroker;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
@SuppressWarnings("serial")
public class CreateTorrentDialog extends JDialog {
    private static final PieceSize[] values = PieceSize.values();
    private static final Logger LOG = Logger.getLogger(CreateTorrentDialog.class);
    private final List<String> trackers;
    private final Container container;
    private final JTabbedPane tabbedPane;
    private final JPanel basicTorrentPane;
    private final JPanel licensesPane;
    private final JPanel paymentsPane;
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
    /**
     * Only used on MacOSX, as FileChooser does not support both File and Directory selection.
     */
    private JButton buttonSelectFolder;
    private JLabel labelTrackers;
    private JTextArea textTrackers;
    private JCheckBox checkStartSeeding;
    private JCheckBox checkUseDHT;
    private JTextArea textWebSeeds;
    private JButton buttonSaveAs;
    private JProgressBar progressBar;
    private JScrollPane textTrackersScrollPane;
    private String invalidTrackerURL;
    private JButton buttonClose;
    private JComboBox<PieceSize> pieceSizeComboBox;
    private int pieceSize;

    public CreateTorrentDialog(JFrame frame) {
        super(frame);
        trackers = new ArrayList<>();
        container = getContentPane();
        tabbedPane = new JTabbedPane();
        basicTorrentPane = new JPanel();
        licensesPane = new JPanel();
        paymentsPane = new JPanel();
        licenseSelectorPanel = new CopyrightLicenseSelectorPanel();
        paymentOptionsPanel = new PaymentOptionsPanel();
        initContainersLayouts();
        initComponents();
        setLocationRelativeTo(frame);
        pack();
    }

    // TODO: Add this fix to Entry.fromMap() on jlibtorrent and delete this.
    private static Entry entryFromMap(Map<?, ?> map) {
        entry e = new entry(entry.data_type.dictionary_t);
        string_entry_map d = e.dict();
        for (Object o : map.keySet()) {
            String k = (String) o;
            Object v = map.get(k);
            if (v instanceof String) {
                d.set(k, new entry((String) v));
            } else if (v instanceof Integer) {
                d.set(k, new entry((Integer) v));
            } else if (v instanceof Entry) {
                d.set(k, ((Entry) v).swig());
            } else if (v instanceof entry) {
                d.set(k, (entry) v);
            } else if (v instanceof List) {
                d.set(k, Entry.fromList((List<?>) v).swig());
            } else if (v instanceof Map) {
                d.set(k, entryFromMap((Map<?, ?>) v).swig());
            } else {
                d.set(k, new entry(v.toString()));
            }
        }
        return new Entry(e);
    }

    private void initContainersLayouts() {
        container.setLayout(new MigLayout("fill, insets 3px 3px 3px 3px"));
        basicTorrentPane.setLayout(new MigLayout("fill"));
        licensesPane.setLayout(new MigLayout("fill"));
        paymentsPane.setLayout(new MigLayout("fill"));
    }

    private void initTabbedPane() {
        container.add(tabbedPane, "grow, pushy, wrap");
        licensesPane.add(licenseSelectorPanel, "grow");
        paymentsPane.add(paymentOptionsPanel, "grow");
        tabbedPane.addTab("1. " + I18n.tr("Contents and Tracking"), basicTorrentPane);
        tabbedPane.addTab("2. " + I18n.tr("Copyright License"), licensesPane);
        tabbedPane.addTab("3. " + I18n.tr("Payments/Tips"), paymentsPane);
    }

    private void initComponents() {
        initDialogSettings();
        initTabbedPane();
        initProgressBar();
        initSaveCloseButtons();
        initTorrentContents();
        initTorrentTracking();
        buildListeners();
    }

    private void initDialogSettings() {
        setTitle(I18n.tr("Create New Torrent"));
        setSize(MINIMUM_DIALOG_DIMENSIONS);
        setMinimumSize(MINIMUM_DIALOG_DIMENSIONS);
        setPreferredSize(MINIMUM_DIALOG_DIMENSIONS);
        //setMaximumSize(MINIMUM_DIALOG_DIMENSIONS);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);
        GUIUtils.addHideAction((JComponent) container);
    }

    private void initTorrentContents() {
        JPanel torrentContentsPanel = new JPanel(new MigLayout("fillx, wrap 1", "[]"));
        GUIUtils.setTitledBorderOnPanel(torrentContentsPanel, I18n.tr("Torrent Contents"));
        textSelectedContent = new LimeTextField();
        textSelectedContent.setEditable(false);
        textSelectedContent.setToolTipText(I18n.tr("These box shows the contents you've selected for your new .torrent.\nEither a file, or the contents of a folder."));
        torrentContentsPanel.add(textSelectedContent, "north, growx, gap 5 5 0 0, wrap");
        JPanel contentSelectionButtonsPanel = new JPanel(new MigLayout("ins 0, nogrid, fillx, wrap 1", ""));
        buttonSelectFile = new JButton(I18n.tr("Select File"));
        buttonSelectFile.setToolTipText(I18n.tr("Click here to select a single file as the content indexed by your new .torrent"));
        buttonSelectFolder = new JButton(I18n.tr("Select Folder"));
        buttonSelectFolder.setToolTipText(I18n.tr("Click here to select a folder as the content indexed by your new .torrent"));
        contentSelectionButtonsPanel.add(buttonSelectFile, "align right, width 175px, gaptop 5, gapright 5, gapbottom 5");
        contentSelectionButtonsPanel.add(buttonSelectFolder, "align right, width 175px, gaptop 5, gapright 5, gapbottom 5");
        torrentContentsPanel.add(contentSelectionButtonsPanel, "width 380px, align right, gaptop 5, gapright 5, gapbottom 5");
        basicTorrentPane.add(torrentContentsPanel, "growx, wrap");
    }

    private void initTorrentTracking() {
        JPanel torrentTrackingPanel = new JPanel(new MigLayout("fill"));
        GUIUtils.setTitledBorderOnPanel(torrentTrackingPanel, I18n.tr("Torrent Properties"));
        checkUseDHT = new JCheckBox(I18n.tr("Trackerless Torrent (DHT)"), true);
        checkUseDHT.setToolTipText(I18n.tr("Select this option to create torrents that don't need trackers, completely descentralized. (Recommended)"));
        torrentTrackingPanel.add(checkUseDHT, "align left, gapleft 5");
        checkStartSeeding = new JCheckBox(I18n.tr("Start seeding"), true);
        checkStartSeeding.setToolTipText(I18n
                .tr("Announce yourself as a seed for the content indexed by this torrent as soon as it's created.\nIf nobody is seeding the torrent, it won't work. (Recommended)"));
        torrentTrackingPanel.add(checkStartSeeding, "align right, gapright 10, wrap");
        labelTrackers = new JLabel("<html><p>" + I18n.tr("Tracker Announce URLs") + "</p><p>(" + I18n.tr("One tracker per line") + ")</p></html>");
        labelTrackers.setToolTipText(I18n.tr("Enter a list of valid BitTorrent Tracker Server URLs.\nYour new torrent will be announced to these trackers if you start seeding the torrent."));
        torrentTrackingPanel.add(labelTrackers, "aligny top, pushy, growx 40, gapleft 5, gapright 10, wmin 150px");
        textTrackers = new JTextArea(10, 70);
        ThemeMediator.fixKeyStrokes(textTrackers);
        textTrackers.setToolTipText(labelTrackers.getToolTipText());
        textTrackers.setLineWrap(false);
        textTrackers.setText("udp://open.demonii.com:1337\nudp://tracker.coppersurfer.tk:6969\nudp://tracker.leechers-paradise.org:6969\nudp://exodus.desync.com:6969\nudp://tracker.pomf.se");
        textTrackersScrollPane = new JScrollPane(textTrackers);
        torrentTrackingPanel.add(textTrackersScrollPane, "gapright 5, gapleft 80, gapbottom 5, hmin 165px, growx 60, growy, wrap");
        JLabel _labelWebseeds = new JLabel(I18n.tr("Web Seeds Mirror URLs"));
        _labelWebseeds.setToolTipText(I18n.tr("If these files can be downloaded from the web, enter the URLs of each possible mirror, one per line (GetRight style)."));
        torrentTrackingPanel.add(_labelWebseeds, "aligny top, pushy, gapleft 5, gapright 10, wmin 150px");
        textWebSeeds = new JTextArea(4, 70);
        ThemeMediator.fixKeyStrokes(textWebSeeds);
        torrentTrackingPanel.add(new JScrollPane(textWebSeeds), "gapright 5, gapleft 80, gapbottom 5, hmin 165px, growx 60, growy, wrap");
        JLabel labelPieceSize = new JLabel(I18n.tr("Piece Size"));
        torrentTrackingPanel.add(labelPieceSize, "aligny top, pushy, gapleft 5, gapright 10, wmin 150px");
        pieceSizeComboBox = new JComboBox<>(values);
        torrentTrackingPanel.add(pieceSizeComboBox, "gapright 5, gapleft 80, width 175px");
        //suggest DHT by default
        updateTrackerRelatedControlsAvailability(true);
        basicTorrentPane.add(torrentTrackingPanel, "grow, push");
    }

    private void initSaveCloseButtons() {
        JPanel buttonContainer = new JPanel();
        buttonContainer.setLayout(new MigLayout("fillx, insets 5 5 5 5"));
        //first button will dock all the way east,
        buttonSaveAs = new JButton(I18n.tr("Save torrent as..."));
        buttonContainer.add(buttonSaveAs, "pushx, alignx right, gapleft 5");
        //then this one will dock east (west of) the next to the existing component
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
        checkUseDHT.addChangeListener(arg0 -> {
            boolean useDHT = checkUseDHT.isSelected();
            updateTrackerRelatedControlsAvailability(useDHT);
        });
        textTrackers.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (checkUseDHT.isSelected()) {
                    checkUseDHT.setSelected(false);
                }
            }
        });
        pieceSizeComboBox.addActionListener(
                e -> onPieceSizeSelected(pieceSizeComboBox.getSelectedIndex())
        );
    }

    private void onPieceSizeSelected(int selectedIndex) {
        if (selectedIndex > 0 && values.length > selectedIndex && values[selectedIndex] != null) {
            pieceSize = values[selectedIndex].bytes();
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

    private void onButtonClose(ActionEvent e) {
        GUIUtils.getDisposeAction().actionPerformed(e);
    }

    private void showFileChooser(final int fileFolderChoosingMode) {
        FileFilter directoryFilesAllowedFilter = new FileFilter() {
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
            chosenFile = FileChooserHandler.getInputFile(GUIMediator.getAppFrame(),
                    I18n.tr("Select a single file"),
                    directory,
                    directoryFilesAllowedFilter);
        } else if (fileFolderChoosingMode == JFileChooser.DIRECTORIES_ONLY) {
            chosenFile = FileChooserHandler.getInputDirectory(GUIMediator.getAppFrame(),
                    I18n.tr("Select a single directory"),
                    I18n.tr("Select folder"),
                    directory,
                    directoryFilesAllowedFilter);
        }
        if (chosenFile != null) {
            FileChooserHandler.setLastInputDirectory(chosenFile); //might not be necessary.
            setChosenContent(chosenFile, fileFolderChoosingMode);
        }
    }

    public void setChosenContent(File chosenFile, int fileChoosingMode) {
        // if we don't have read permissions on that file/folder...
        if (!chosenFile.canRead()) {
            textSelectedContent.setText(I18n.tr("Error: You can't read on that file/folder."));
            return;
        }
        chosenFile = correctFileSelectionMode(chosenFile, fileChoosingMode);
        setTorrentPathFromChosenFile(chosenFile);
        displayChosenContent(chosenFile);
    }

    private void displayChosenContent(File chosenFile) {
        String prefix = (chosenFile.isFile()) ? "[file] " : "[folder] ";
        textSelectedContent.setText(prefix + chosenFile.getAbsolutePath());
    }

    private void setTorrentPathFromChosenFile(File chosenFile) {
        File canonicalFile;
        try {
            canonicalFile = chosenFile.getCanonicalFile();
            if (canonicalFile.isFile()) {
                create_from_dir = false;
                directoryPath = null;
                singlePath = chosenFile.getAbsolutePath();
            } else if (canonicalFile.isDirectory()) {
                create_from_dir = true;
                directoryPath = chosenFile.getAbsolutePath();
                singlePath = null;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private File correctFileSelectionMode(File chosenFile, int fileSelectionMode) {
        if (fileSelectionMode == JFileChooser.DIRECTORIES_ONLY && chosenFile.isFile()) {
            chosenFile = chosenFile.getParentFile();
        }
        create_from_dir = chosenFile.isDirectory();
        return chosenFile;
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
        //Make sure a readable file or folder has been selected.
        if (singlePath == null && directoryPath == null) {
            JOptionPane.showMessageDialog(this, I18n.tr("Please select a file or a folder.\nYour new torrent will need content to index."), I18n.tr("Something's missing"), JOptionPane.ERROR_MESSAGE);
            tabbedPane.setSelectedIndex(0);
            return;
        }
        //if user chose a folder that's empty
        File[] fileArray;
        if (directoryPath != null &&
                ((fileArray = new File(directoryPath).listFiles()) != null) &&
                fileArray.length == 0) {
            JOptionPane.showMessageDialog(this, I18n.tr("The folder you selected is empty."), I18n.tr("Invalid Folder"), JOptionPane.ERROR_MESSAGE);
            tabbedPane.setSelectedIndex(0);
            return;
        }
        //if it's not tracker-less make sure we have valid tracker urls
        boolean useTrackers = !checkUseDHT.isSelected();
        if (useTrackers) {
            if (!validateAndFixTrackerURLS()) {
                if (invalidTrackerURL == null) {
                    invalidTrackerURL = "";
                }
                JOptionPane.showMessageDialog(this, I18n.tr("Check again your tracker URL(s).\n" + invalidTrackerURL), I18n.tr("Invalid Tracker URL\n"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            trackers.clear();
        }
        //Whether or not to start seeding this torrent right away
        autoOpen = checkStartSeeding.isSelected();
        //show save as dialog
        if (!showSaveAsDialog()) {
            return;
        }
        new Thread(() -> {
            if (makeTorrent()) {
                revertSaveCloseButtons();
                progressBar.setString(I18n.tr("Torrent Created."));
                SwingUtilities.invokeLater(CreateTorrentDialog.this::dispose);
                if (autoOpen) {
                    SwingUtilities.invokeLater(() -> GUIMediator.instance().openTorrentForSeed(new File(dotTorrentSavePath), saveDir));
                }
            }
        }).start();
    }

    private boolean showSaveAsDialog() {
        FileFilter saveAsFilter = new FileFilter() {
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
        File suggestedFile = new File(SharingSettings.DEFAULT_TORRENTS_DIR, torrContents.getName() + ".torrent");
        File chosenFile = FileChooserHandler.getSaveAsFile(
                I18n.tr("Save .torrent"),
                suggestedFile,
                saveAsFilter);
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
        String trackersText = textTrackers.getText();
        if (trackersText == null || trackersText.length() == 0) {
            return false;
        }
        String patternStr = "^(https?|udp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern pattern = Pattern.compile(patternStr);
        String[] tracker_urls = trackersText.split("\n");
        List<String> valid_tracker_urls = new ArrayList<>();
        for (String tracker_url : tracker_urls) {
            if (tracker_url.trim().equals("")) {
                continue;
            }
            // assume http if the user does not specify it
            if (!tracker_url.startsWith("http://") &&
                    !tracker_url.startsWith("https://") &&
                    !tracker_url.startsWith("udp://")) {
                tracker_url = "http://" + tracker_url.trim();
            }
            Matcher matcher = pattern.matcher(tracker_url.trim());
            if (!matcher.matches()) {
                invalidTrackerURL = tracker_url.trim();
                return false;
            } else {
                valid_tracker_urls.add(tracker_url.trim());
            }
        }
        fixValidTrackers(valid_tracker_urls);
        //update the trackers list of lists
        trackers.clear();
        trackers.addAll(valid_tracker_urls);
        invalidTrackerURL = null;
        return true;
    }

    private void fixValidTrackers(List<String> valid_tracker_urls) {
        //re-write the tracker's text area with corrections
        StringBuilder builder = new StringBuilder();
        for (String valid_tracker_url : valid_tracker_urls) {
            builder.append(valid_tracker_url);
            builder.append("\n");
        }
        textTrackers.setText(builder.toString());
    }

    private boolean makeTorrent() {
        boolean result;
        disableSaveCloseButtons();
        File f = new File((create_from_dir) ? directoryPath : singlePath);
        try {
            file_storage fs = new file_storage();
            reportCurrentTask(I18n.tr("Adding files..."));
            libtorrent.add_files(fs, f.getPath());
            create_torrent torrent = new create_torrent(fs, pieceSize);
            torrent.set_priv(false);
            torrent.set_creator("FrostWire " + FrostWireUtils.getFrostWireVersion() + " build " + FrostWireUtils.getBuildNumber());
            if (trackers != null && !trackers.isEmpty()) {
                reportCurrentTask(I18n.tr("Adding trackers..."));
                for (String trackerUrl : trackers) {
                    torrent.add_tracker(trackerUrl, 0);
                }
            }
            if (addAvailableWebSeeds(torrent, create_from_dir)) {
                reportCurrentTask(I18n.tr("Calculating piece hashes..."));
                saveDir = f.getParentFile();
                error_code ec = new error_code();
                libtorrent.set_piece_hashes(torrent, saveDir.getAbsolutePath(), ec);
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
                //check just the first file on all mirrors.
                reportCurrentTask(I18n.tr("Checking Web seed mirror URLs..."));
                for (String mirror : mirrors) {
                    if (isMultiFile && !mirror.endsWith("/")) {
                        fixWebSeedMirrorUrl(mirror);
                    }
                    if (!checkWebSeedMirror(mirror, torrent, isMultiFile)) {
                        result = false;
                        showWebseedsErrorMessage(new Exception(getWebSeedTestPath(mirror, torrent, isMultiFile) + " " + I18n.tr("Web seed not reachable.")));
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
        GUIMediator.safeInvokeLater(() -> {
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
        //fix mirror
        if (isMultiFile && !mirror.endsWith("/")) {
            mirror = mirror + "/";
        }
        if (isMultiFile) {
            final file_storage files = torrent.files();
            //path should be <http://mirror-url/> + torrentName + "/" + relativeFilePath
            urlPath = mirror + files.name() + "/" + files.file_path(0);//torrent.getFiles()[0].getRelativePath();
        } else {
            //url-list should point straight to the file.
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
     * Not sure if we need to implement this, I suppose this changed one of the
     * buttons of the wizard from next|cancel to close
     */
    private void disableSaveCloseButtons() {
        SwingUtilities.invokeLater(() -> {
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

    private enum PieceSize {
        AUTO_DETECT(0, "(" + I18n.tr("Auto Detect") + ")"),
        _16KB(16),
        _32KB(32),
        _64KB(64),
        _128KB(128),
        _256KB(256),
        _512KB(512),
        _1024KB(1024),
        _2048KB(2048),
        _4096KB(4096);
        private final int kb;
        private final String humanRep;

        PieceSize(int k, String rep) {
            kb = k;
            humanRep = rep;
        }

        PieceSize(int k) {
            this(k, k + " kB");
        }

        int bytes() {
            return kb * 1024;
        }

        @Override
        public String toString() {
            return humanRep;
        }
    }

    /*
    public static void main(String[] args) {
        
        if (OSUtils.isMacOSX()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.eawt.CocoaComponent.CompatibilityMode", "false");
        }

       // ThemeMediator.changeTheme();

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
