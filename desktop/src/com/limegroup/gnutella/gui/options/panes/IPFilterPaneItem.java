/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.swig.ip_filter;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.JdkHttpClient;
import com.limegroup.gnutella.gui.FileChooserHandler;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconButton;
import com.limegroup.gnutella.gui.options.panes.ipfilter.AddRangeManuallyDialog;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPFilterHttpListener;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPFilterInputStreamReader;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.settings.FilterSettings;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.IOUtils;
import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPInputStream;

import static com.frostwire.gui.theme.ThemeMediator.fixKeyStrokes;

public class IPFilterPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("IP Filter");
    private final static String LABEL = I18n.tr("You can manually enter IP addresses ranges to filter out, you can also import bulk addresses from an IP block list file or URL");
    private final static Pattern P2P_LINE_PATTERN = Pattern.compile("(.*)\\:(.*)\\-(.*)$", java.util.regex.Pattern.COMMENTS);
    private final static Logger LOG = Logger.getLogger(IPFilterPaneItem.class);
    private final String decompressingString = I18n.tr("Decompressing");
    private final ExecutorService httpExecutor;
    private IPFilterTableMediator ipFilterTable;
    private JTextField fileUrlTextField;
    private JProgressBar progressBar;
    private JButton importButton;
    private JButton clearFilterButton;
    private JButton addRangeManuallyButton;
    private IconButton fileChooserIcon;
    private JCheckBox enableIPFilterCheckBox;
    private boolean initialized;
    private int lastPercentage = -1;
    private long lastPercentageUpdateTimestamp;
    private IPFilterPaneItem() {
        super(TITLE, LABEL);
        ipFilterTable = null;
        httpExecutor = ExecutorsHelper.newProcessingQueue("IPFilterPanelItem-http");
    }

    private static boolean isGZipped(File f) throws IOException {
        byte[] signature = new byte[2];
        FileInputStream fis = new FileInputStream(f);
        fis.read(signature);
        return signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b;
    }

    @Override
    public void initOptions() {
        if (initialized) {
            return;
        }
        initialized = true;
        BTEngine engine;
        ip_filter ipFilter = null;
        if ((engine = BTEngine.getInstance()) != null) {
            ipFilter = engine.swig().get_ip_filter();
        }
        if (ipFilter == null) {
            throw new RuntimeException("Check your logic. No BTEngine ip_filter instance available");
        }
        // ipFilterTableModel should be loaded in separate thread
        JPanel panel = new JPanel(new MigLayout("fillx, ins 0, insets, nogrid", "[][][][][][][][]"));
        ipFilterTable = IPFilterTableMediator.getInstance();
        BackgroundQueuedExecutorService.schedule(this::loadSerializedIPFilter);
        
        // Add enable/disable checkbox
        enableIPFilterCheckBox = new JCheckBox(I18n.tr("Enable IP Filtering"));
        enableIPFilterCheckBox.setSelected(FilterSettings.IP_FILTER_ENABLED.getValue());
        enableIPFilterCheckBox.addActionListener(e -> onIPFilterEnabledChanged());
        panel.add(enableIPFilterCheckBox, "span, wrap");
        
        panel.add(ipFilterTable.getComponent(), "span, pad 0 0 0 0, grow, wrap");
        panel.add(new JLabel(I18n.tr("Enter the URL or local file path of an IP Filter list (p2p format only supported)")), "pad 0 5px, span, wrap");
        fileUrlTextField = new JTextField();
        fixKeyStrokes(fileUrlTextField);
        panel.add(fileUrlTextField, "span 6, growx");
        fileChooserIcon = new IconButton("OPEN_IP_FILTER_FILE", 24, 24);
        panel.add(fileChooserIcon, "span 1");
        importButton = new JButton(I18n.tr("Import"));
        panel.add(importButton, "span 1, wrap");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        panel.add(progressBar, "growx, wrap");
        addRangeManuallyButton = new JButton(I18n.tr("Add IP Range Manually"));
        addRangeManuallyButton.addActionListener((e) -> onAddRangeManuallyAction());
        panel.add(addRangeManuallyButton);
        clearFilterButton = new JButton(I18n.tr("Clear IP Block List"));
        clearFilterButton.addActionListener((e) -> onClearFilterAction());
        panel.add(clearFilterButton);
        add(panel);
        fileChooserIcon.addActionListener((e) -> onFileChooserIconAction());
        importButton.addActionListener((e) -> onImportButtonAction());
    }

    private void onImportButtonAction() {
        enableImportControls(false);
        String filterDataPath = fileUrlTextField.getText().trim();
        if (null == filterDataPath || "".equals(filterDataPath)) {
            enableImportControls(true);
            return;
        }
        if (filterDataPath.toLowerCase().startsWith("http")) {
            // download file
            LOG.info("onImportButtonAction() trying URL");
            try {
                URI uri = URI.create(filterDataPath);
                HttpClient http = new JdkHttpClient();
                http.setListener(new IPFilterHttpListener(this, new File(CommonUtils.getUserSettingsDir(), "downloaded_blocklist.temp")));
                httpExecutor.execute(() -> {
                            try {
                                LOG.info("http.get() -> " + uri.toURL().toString());
                                http.get(uri.toURL().toString());
                            } catch (IOException e) {
                                e.printStackTrace();
                                enableImportControls(true);
                                fileUrlTextField.selectAll();
                            }
                        }
                );
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                GUIMediator.showError(I18n.tr("Invalid URI or file path"));
                enableImportControls(true);
                fileUrlTextField.selectAll();
                return;
            }
            return; // HTTP Listener will take care of this from another thread
        }
        importFromIPBlockFileAsync(new File(filterDataPath), false);
    }

    private void onClearFilterAction() {
        enableImportControls(false);
        ipFilterTable.clearTable();
        File ipFilterDBFile = getIPFilterDBFile();
        ipFilterDBFile.delete();
        try {
            ipFilterDBFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Clear actual IP Filter from BTEngine
        clearCurrentIPFilter();
        enableImportControls(true);
    }

    public void importFromIPBlockFileAsync(final File potentialGunzipFile, boolean removeInputFileWhenDone) {
        // decompress if zip file
        // import file
        BackgroundQueuedExecutorService.schedule(() -> {
            LOG.info("importFromStreamAsync(): thread invoked", true);
            File decompressedFile;
            try {
                if (isGZipped(potentialGunzipFile)) {
                    decompressedFile = gunzipFile(potentialGunzipFile,
                            new File(CommonUtils.getUserSettingsDir(), "gunzipped_blocklist.temp"));
                    if (removeInputFileWhenDone) {
                        potentialGunzipFile.delete();
                    }
                } else {
                    decompressedFile = potentialGunzipFile;
                }
            } catch (IOException e) {
                LOG.error("importFromIPBlockFileAsync(): " + e.getMessage(), e);
                fileUrlTextField.selectAll();
                enableImportControls(true);
                return;
            }
            final IPFilterFormat format = getIPFilterFileFormat(decompressedFile);
            if (format == null) {
                LOG.error("importFromStreamAsync(): IPFilterFormat could not be determined");
                fileUrlTextField.selectAll();
                enableImportControls(true);
                GUIMediator.showError(I18n.tr("Invalid IP Filter file format, only P2P (PeerGuardian) format supported"));
                return;
            }
            final long decompressedFileSize = decompressedFile.length();
            final IPFilterInputStreamReader ipFilterReader = (format == IPFilterFormat.P2P) ? new P2PIPFilterInputStreamReader(decompressedFile) : null;
            if (ipFilterReader == null) {
                LOG.error("importFromStreamAsync(): Invalid IP Filter file format, only p2p format supported");
                fileUrlTextField.selectAll();
                enableImportControls(true);
                return;
            }
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File(CommonUtils.getUserSettingsDir(), "ip_filter.db"));
                IPFilterTableMediator.IPFilterModel dataModel = ipFilterTable.getDataModel();
                final String importingString = I18n.tr("Importing");
                while (ipFilterReader.available() > 0) {
                    IPRange ipRange = ipFilterReader.readLine();
                    if (ipRange != null) {
                        try {
                            ipRange.writeObjectTo(fos);
                            dataModel.add(ipRange, dataModel.getRowCount());
                            // every few imported ipRanges, let's do an UI update
                            if (dataModel.getRowCount() % 100 == 0) {
                                GUIMediator.safeInvokeLater(() -> updateProgressBar((int) ((ipFilterReader.bytesRead() * 100.0f / decompressedFileSize)), importingString));
                            }
                            // Add to actual IP block filter
                            applyCurrentIPFilter();
                        } catch (Throwable t) {
                            LOG.warn(t.getMessage(), t);
                            // just keep going
                        }
                    }
                }
                GUIMediator.safeInvokeLater(() -> {
                    updateProgressBar(100, "");
                    ipFilterTable.refresh();
                    enableImportControls(true);
                    fileUrlTextField.setText("");
                    LOG.info("importFromStreamAsync() - done");
                    // Apply the complete IP filter after import is done
                    applyCurrentIPFilter();
                });
                if (removeInputFileWhenDone) {
                    potentialGunzipFile.delete();
                }
                if (decompressedFile.getAbsolutePath().contains("gunzipped_blocklist.temp")) {
                    decompressedFile.delete();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                IOUtils.closeQuietly(fos);
                ipFilterReader.close();
                enableImportControls(true);
            }
        });
    }

    private IPFilterFormat getIPFilterFileFormat(File decompressedFile) {
        byte[] sample = new byte[1024];
        try {
            FileInputStream fis = new FileInputStream(decompressedFile);
            fis.read(sample);
            fis.close();
        } catch (IOException e) {
            return null;
        }
        Matcher matcher = P2P_LINE_PATTERN.matcher(new String(sample, StandardCharsets.UTF_8));
        if (matcher.find() && matcher.find() && matcher.find()) { // no coincidences
            return IPFilterFormat.P2P;
        }
        return null;
    }

    private void onAddRangeManuallyAction() {
        new AddRangeManuallyDialog(this).setVisible(true);
    }

    private void onFileChooserIconAction() {
        final File selectedFile = FileChooserHandler.getInputFile(getContainer(), I18n.tr("Select the IP filter file (P2P/PeerGuardian format only supported)"), FileChooserHandler.getLastInputDirectory(), new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isFile();
            }

            @Override
            public String getDescription() {
                return null;
            }
        });
        if (selectedFile != null) {
            fileUrlTextField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void loadSerializedIPFilter() {
        LOG.info("loadSerializedIPFilter() invoked - " + hashCode());
        File ipFilterDBFile = getIPFilterDBFile();
        if (!ipFilterDBFile.exists()) {
            try {
                ipFilterDBFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            LOG.info("loadSerializedIPFilter(): done, initialized empty ip filter file");
            return;
        }
        if (ipFilterDBFile.length() == 0) {
            LOG.info("loadSerializedIPFilter(): done, ip filter file existed but it was empty");
            return;
        }
        try {
            long start = System.currentTimeMillis();
            LOG.info("loadSerializedIPFilter(): loading " + ipFilterDBFile.length() + "  bytes from ip filter file");
            final FileInputStream fis = new FileInputStream(ipFilterDBFile);
            int ranges = 0;
            final IPFilterTableMediator.IPFilterModel dataModel = ipFilterTable.getDataModel();
            while (fis.available() > 0) {
                try {
                    dataModel.add(IPRange.readObjectFrom(fis), dataModel.getRowCount());
                    ranges++;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e2) {
                    LOG.error("Invalid IPRange entry detected", e2);
                }
            }
            long end = System.currentTimeMillis();
            fis.close();
            long delta = end - start;
            LOG.info("loadSerializedIPFilter(): loaded " + ranges + " ip filter ranges in " + delta + "ms");
            
            // Apply the loaded IP filter to the BitTorrent session
            if (ranges > 0) {
                applyCurrentIPFilter();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getIPFilterDBFile() {
        return new File(CommonUtils.getUserSettingsDir(), "ip_filter.db");
    }

    public void updateProgressBar(int percentage, String status) {
        if (percentage == lastPercentage) {
            return;
        }
        long timeSinceLastUpdate = System.currentTimeMillis() - lastPercentageUpdateTimestamp;
        if (lastPercentage < 99 && timeSinceLastUpdate < 1000) {
            return;
        }
        lastPercentage = percentage;
        GUIMediator.safeInvokeLater(() -> {
            progressBar.setString(status + "...");
            progressBar.setValue(lastPercentage);
            progressBar.repaint();
            lastPercentageUpdateTimestamp = System.currentTimeMillis();
        });
    }

    private void onGunzipProgress(int percentage) {
        updateProgressBar(percentage, decompressingString);
    }

    public void enableImportControls(boolean enable) {
        GUIMediator.safeInvokeLater(() -> {
            fileUrlTextField.setEnabled(enable);
            fileChooserIcon.setEnabled(enable);
            importButton.setText(enable ? I18n.tr("Import") : I18n.tr("Importing..."));
            importButton.setEnabled(enable);
            clearFilterButton.setEnabled(enable);
            addRangeManuallyButton.setEnabled(enable);
            lastPercentageUpdateTimestamp = -1;
            lastPercentage = -1;
            progressBar.setVisible(!enable);
            if (enable) {
                fileUrlTextField.requestFocus();
                fileUrlTextField.selectAll();
                updateProgressBar(0, "");
            }
        });
    }

    @Override
    public boolean applyOptions() {
        return false;
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    private File gunzipFile(File gzipped, File gunzipped) {
        if (gunzipped.exists()) {
            gunzipped.delete();
            try {
                gunzipped.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        try {
            // NOTE: will work for files under 4GB only, should be fine for IPBlock lists, usually < 10MB
            RandomAccessFile raf = new RandomAccessFile(gzipped, "r");
            raf.seek(raf.length() - 4);
            int b4 = raf.read();
            int b3 = raf.read();
            int b2 = raf.read();
            int b1 = raf.read();
            raf.close();
            final int uncompressedSize = (b1 << 24) | (b2 << 16) + (b3 << 8) + b4;
            byte[] buffer = new byte[32768];
            GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(gzipped), buffer.length);
            FileOutputStream fileOutputStream = new FileOutputStream(gunzipped, false);
            int totalGunzipped = 0;
            while (gzipInputStream.available() == 1) {
                int read = gzipInputStream.read(buffer);
                if (read > 0) {
                    if (read != buffer.length) {
                        LOG.info("gunzipFile(): read = " + read);
                    }
                    totalGunzipped += read;
                    fileOutputStream.write(buffer, 0, read);
                    onGunzipProgress((int) ((totalGunzipped * 100.0f / uncompressedSize)));
                }
            }
            onGunzipProgress(100);
            gzipInputStream.close();
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (Throwable t) {
            LOG.error("gunzipFile(): " + t.getMessage(), t);
            gunzipped.delete();
            return null;
        }
        return new File(gunzipped.getAbsolutePath());
    }

    public void onRangeManuallyAdded(IPRange ipRange) {
        LOG.info("onRangeManuallyAdded() - " + ipRange);
        // Apply the updated IP filter after manually adding a range
        applyCurrentIPFilter();
    }

    enum IPFilterFormat {
        P2P
    }

    private static class P2PIPFilterInputStreamReader implements IPFilterInputStreamReader {
        private final BufferedReader br;
        private InputStream is;
        private int bytesRead;

        P2PIPFilterInputStreamReader(File uncompressedFile) {
            try {
                this.is = new FileInputStream(uncompressedFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            br = new BufferedReader(new InputStreamReader(is));
            bytesRead = 0;
        }

        @Override
        public IPRange readLine() {
            try {
                if (is.available() > 0) {
                    String line = br.readLine();
                    bytesRead += line.length();
                    while (line.startsWith("#") && is.available() > 0) {
                        line = br.readLine();
                        bytesRead += line.length();
                    }
                    Matcher matcher = P2P_LINE_PATTERN.matcher(line);
                    if (matcher.find()) {
                        return new IPRange(matcher.group(1), matcher.group(2), matcher.group(3));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public int bytesRead() {
            return bytesRead;
        }

        @Override
        public int available() {
            try {
                return is.available();
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        }

        @Override
        public void close() {
            try {
                is.close();
                br.close();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /**
     * Apply the current IP filter rules to the BitTorrent session.
     * This method reads all IP ranges from the table and applies them to the BTEngine.
     * This method must be called from a background thread as IP lists can be very large.
     */
    private void applyCurrentIPFilter() {
        // Ensure we're not on the UI thread since IP filtering can be expensive
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            BackgroundQueuedExecutorService.schedule(this::applyCurrentIPFilter);
            return;
        }
        
        try {
            // Check if IP filtering is enabled
            if (!FilterSettings.IP_FILTER_ENABLED.getValue()) {
                LOG.info("applyCurrentIPFilter(): IP filtering is disabled, skipping");
                return;
            }

            BTEngine engine = BTEngine.getInstance();
            if (engine == null) {
                LOG.warn("applyCurrentIPFilter(): BTEngine not available");
                return;
            }

            if (ipFilterTable == null) {
                LOG.warn("applyCurrentIPFilter(): ipFilterTable not available");
                return;
            }

            IPFilterTableMediator.IPFilterModel dataModel = ipFilterTable.getDataModel();
            if (dataModel == null) {
                LOG.warn("applyCurrentIPFilter(): dataModel not available");
                return;
            }

            // Get all IP ranges from the table
            java.util.List<com.frostwire.bittorrent.IPRange> ranges = new java.util.ArrayList<>();
            for (int i = 0; i < dataModel.getRowCount(); i++) {
                try {
                    IPRange range = (IPRange) dataModel.getValueAt(i, 0); // Assuming IPRange is in first column
                    if (range != null) {
                        ranges.add(range);
                    }
                } catch (Throwable t) {
                    LOG.warn("applyCurrentIPFilter(): error getting range at row " + i, t);
                }
            }

            LOG.info("applyCurrentIPFilter(): applying " + ranges.size() + " IP filter ranges to BTEngine");
            engine.applyIPFilter(ranges);

        } catch (Throwable t) {
            LOG.error("applyCurrentIPFilter(): error applying IP filter", t);
        }
    }

    /**
     * Clear the IP filter from the BitTorrent session.
     * This method must be called from a background thread.
     */
    private void clearCurrentIPFilter() {
        // Ensure we're not on the UI thread
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            BackgroundQueuedExecutorService.schedule(this::clearCurrentIPFilter);
            return;
        }
        
        try {
            BTEngine engine = BTEngine.getInstance();
            if (engine != null) {
                engine.clearIPFilter();
                LOG.info("clearCurrentIPFilter(): IP filter cleared");
            }
        } catch (Throwable t) {
            LOG.error("clearCurrentIPFilter(): error clearing IP filter", t);
        }
    }

    /**
     * Handle IP filter enabled/disabled checkbox change.
     * Operations are performed on background thread since IP filtering can be expensive.
     */
    private void onIPFilterEnabledChanged() {
        BackgroundQueuedExecutorService.schedule(() -> {
            boolean enabled = enableIPFilterCheckBox.isSelected();
            FilterSettings.IP_FILTER_ENABLED.setValue(enabled);
            
            if (enabled) {
                // Apply current IP filter
                applyCurrentIPFilter();
                LOG.info("onIPFilterEnabledChanged(): IP filtering enabled");
            } else {
                // Clear IP filter
                clearCurrentIPFilter();
                LOG.info("onIPFilterEnabledChanged(): IP filtering disabled");
            }
        });
    }
}
