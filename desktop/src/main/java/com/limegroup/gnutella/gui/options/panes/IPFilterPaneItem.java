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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.swig.address;
import com.frostwire.jlibtorrent.swig.error_code;
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
import com.limegroup.gnutella.gui.options.panes.ipfilter.CidrFilterInputStreamReader;
import com.limegroup.gnutella.gui.options.panes.ipfilter.DatFilterInputStreamReader;
import com.limegroup.gnutella.gui.options.panes.ipfilter.HostsFilterInputStreamReader;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPFilterFormat;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPFilterInputStreamReader;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import com.limegroup.gnutella.gui.options.panes.ipfilter.P2PIPFilterInputStreamReader;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    private int lastPercentage = -1;
    private long lastPercentageUpdateTimestamp;
    IPFilterPaneItem() {
        super(TITLE, LABEL);
        ipFilterTable = null;
        httpExecutor = ExecutorsHelper.newProcessingQueue("IPFilterPanelItem-http");
    }

    private static boolean isGZipped(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] signature = new byte[2];
            int read = fis.read(signature);
            if (read < 2) return false;
            return signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b;
        }
    }

    private static boolean isZipped(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] signature = new byte[2];
            int read = fis.read(signature);
            if (read < 2) return false;
            return signature[0] == (byte) 0x50 && signature[1] == (byte) 0x4b; // PK magic bytes
        }
    }

    @Override
    public void initOptions() {
        if (ipFilterTable != null) {
            return;
        }
        BTEngine engine;
        ip_filter ipFilter = null;
        if ((engine = BTEngine.getInstance()) != null) {
            ipFilter = engine.swig().get_ip_filter();
        }
        if (ipFilter == null) {
            throw new RuntimeException("Check your logic. No BTEngine ip_filter instance available");
        }
        JPanel panel = new JPanel(new MigLayout("fillx, ins 0, insets, nogrid", "[][][][][][][][]"));
        try {
            ipFilterTable = IPFilterTableMediator.getInstance();
        } catch (Exception e) {
            LOG.error("Failed to initialize IPFilterTableMediator", e);
            return;
        }
        DesktopParallelExecutor.execute(this::loadSerializedIPFilter);
        panel.add(ipFilterTable.getComponent(), "span, pad 0 0 0 0, grow, wrap");
        panel.add(new JLabel(I18n.tr("Enter the URL or local file path of an IP block list (P2P, DAT, CIDR, or Hosts format)")), "pad 0 5px, span, wrap");
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
        BTEngine engine = BTEngine.getInstance();
        if (engine != null) {
            ip_filter freshFilter = new ip_filter();
            engine.swig().set_ip_filter(freshFilter);
        }
        enableImportControls(true);
    }

    public void importFromIPBlockFileAsync(final File potentialGunzipFile, boolean removeInputFileWhenDone) {
        // decompress if zip file
        // import file
        DesktopParallelExecutor.execute(() -> {
            LOG.info("importFromStreamAsync(): thread invoked", true);
            File decompressedFile;
            try {
                if (isGZipped(potentialGunzipFile)) {
                    decompressedFile = gunzipFile(potentialGunzipFile,
                            new File(CommonUtils.getUserSettingsDir(), "gunzipped_blocklist.temp"));
                    if (removeInputFileWhenDone) {
                        potentialGunzipFile.delete();
                    }
                } else if (isZipped(potentialGunzipFile)) {
                    decompressedFile = unzipFile(potentialGunzipFile,
                            new File(CommonUtils.getUserSettingsDir(), "unzipped_blocklist.temp"));
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
                GUIMediator.showError(I18n.tr("Invalid IP Filter file format. Supported: P2P, DAT, CIDR, Hosts"));
                return;
            }
            final long decompressedFileSize = decompressedFile.length();
            final IPFilterInputStreamReader ipFilterReader;
            switch (format) {
                case P2P:
                case DAT:
                    ipFilterReader = new P2PIPFilterInputStreamReader(decompressedFile);
                    break;
                case CIDR:
                    ipFilterReader = new CidrFilterInputStreamReader(decompressedFile);
                    break;
                case HOSTS:
                    ipFilterReader = new HostsFilterInputStreamReader(decompressedFile);
                    break;
                default:
                    ipFilterReader = null;
            }
            if (ipFilterReader == null) {
                LOG.error("importFromStreamAsync(): Invalid IP Filter file format");
                fileUrlTextField.selectAll();
                enableImportControls(true);
                return;
            }
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File(CommonUtils.getUserSettingsDir(), "ip_filter.db"));
                IPFilterTableMediator.IPFilterModel dataModel = ipFilterTable.getDataModel();
                final String importingString = I18n.tr("Importing");
                BTEngine engine = BTEngine.getInstance();
                ip_filter currentFilter = null;
                if (engine != null) {
                    currentFilter = engine.swig().get_ip_filter();
                }
                while (ipFilterReader.available() > 0) {
                    IPRange ipRange = ipFilterReader.readLine();
                    if (ipRange != null) {
                        try {
                            ipRange.writeObjectTo(fos);
                            dataModel.add(ipRange, dataModel.getRowCount());
                            if (currentFilter != null) {
                                error_code ec = new error_code();
                                address addrStart = address.from_string(ipRange.startAddress(), ec);
                                if (!ec.failed()) {
                                    address addrEnd = address.from_string(ipRange.endAddress(), ec);
                                    if (!ec.failed()) {
                                        currentFilter.add_rule(addrStart, addrEnd, 0);
                                    }
                                }
                            }
                            if (dataModel.getRowCount() % 100 == 0) {
                                GUIMediator.safeInvokeLater(() -> updateProgressBar((int) ((ipFilterReader.bytesRead() * 100.0f / decompressedFileSize)), importingString));
                            }
                        } catch (Throwable t) {
                            LOG.warn(t.getMessage(), t);
                            // just keep going
                        }
                    }
                }
                if (engine != null && currentFilter != null) {
                    engine.swig().set_ip_filter(currentFilter);
                }
                GUIMediator.safeInvokeLater(() -> {
                    updateProgressBar(100, "");
                    ipFilterTable.refresh();
                    enableImportControls(true);
                    fileUrlTextField.setText("");
                    LOG.info("importFromStreamAsync() - done");
                });
                if (removeInputFileWhenDone) {
                    potentialGunzipFile.delete();
                }
                if (decompressedFile.getAbsolutePath().contains("gunzipped_blocklist.temp") ||
                    decompressedFile.getAbsolutePath().contains("unzipped_blocklist.temp")) {
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
        byte[] sample = new byte[4096];
        try (FileInputStream fis = new FileInputStream(decompressedFile)) {
            fis.read(sample);
        } catch (IOException e) {
            return null;
        }
        String sampleStr = new String(sample, StandardCharsets.UTF_8);

        // Try P2P/DAT detection first (pattern: name:low-high)
        Matcher matcher = P2P_LINE_PATTERN.matcher(sampleStr);
        if (matcher.find() && matcher.find() && matcher.find()) {
            return IPFilterFormat.P2P;
        }

        // Try CIDR detection (pattern: ip/prefix)
        Pattern cidrPattern = Pattern.compile("(?m)^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}");
        Matcher cidrMatcher = cidrPattern.matcher(sampleStr);
        int cidrMatches = 0;
        while (cidrMatcher.find() && cidrMatches < 3) {
            cidrMatches++;
        }
        if (cidrMatches >= 3) {
            return IPFilterFormat.CIDR;
        }

        // Try HOSTS detection (pattern: 0.0.0.0 domain or 127.0.0.1 domain)
        Pattern hostsPattern = Pattern.compile("(?m)^(0\\.0\\.0\\.0|127\\.0\\.0\\.1|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\s+\\S+");
        Matcher hostsMatcher = hostsPattern.matcher(sampleStr);
        int hostsMatches = 0;
        while (hostsMatcher.find() && hostsMatches < 3) {
            hostsMatches++;
        }
        if (hostsMatches >= 3) {
            return IPFilterFormat.HOSTS;
        }

        return null;
    }

    private void onAddRangeManuallyAction() {
        new AddRangeManuallyDialog(this).setVisible(true);
    }

    private void onFileChooserIconAction() {
        final File selectedFile = FileChooserHandler.getInputFile(getContainer(), I18n.tr("Select the IP filter file (P2P, DAT, CIDR, or Hosts format)"), FileChooserHandler.getLastInputDirectory(), new FileFilter() {
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
            BTEngine engine = BTEngine.getInstance();
            ip_filter currentFilter = null;
            if (engine != null) {
                currentFilter = engine.swig().get_ip_filter();
            }
            while (fis.available() > 0) {
                try {
                    IPRange ipRange = IPRange.readObjectFrom(fis);
                    dataModel.add(ipRange, dataModel.getRowCount());
                    if (currentFilter != null) {
                        error_code ec = new error_code();
                        address addrStart = address.from_string(ipRange.startAddress(), ec);
                        if (!ec.failed()) {
                            address addrEnd = address.from_string(ipRange.endAddress(), ec);
                            if (!ec.failed()) {
                                currentFilter.add_rule(addrStart, addrEnd, 0);
                            }
                        }
                    }
                    ranges++;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e2) {
                    LOG.error("Invalid IPRange entry detected", e2);
                }
            }
            if (engine != null && currentFilter != null) {
                engine.swig().set_ip_filter(currentFilter);
            }
            long end = System.currentTimeMillis();
            fis.close();
            long delta = end - start;
            LOG.info("loadSerializedIPFilter(): loaded " + ranges + " ip filter ranges in " + delta + "ms");
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

    private File unzipFile(File zipped, File unzipped) {
        if (unzipped.exists()) {
            unzipped.delete();
            try {
                unzipped.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        try (FileInputStream fis = new FileInputStream(zipped);
             ZipInputStream zis = new ZipInputStream(fis);
             FileOutputStream fos = new FileOutputStream(unzipped, false)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                LOG.error("unzipFile(): No entries found in zip file");
                return null;
            }
            byte[] buffer = new byte[32768];
            int totalUnzipped = 0;
            long estimatedSize = entry.getSize() > 0 ? entry.getSize() : zipped.length() * 3; // rough estimate
            int read;
            while ((read = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, read);
                totalUnzipped += read;
                if (estimatedSize > 0) {
                    onGunzipProgress((int) ((totalUnzipped * 100.0f / estimatedSize)));
                }
            }
            onGunzipProgress(100);
            fos.flush();
        } catch (Throwable t) {
            LOG.error("unzipFile(): " + t.getMessage(), t);
            unzipped.delete();
            return null;
        }
        return new File(unzipped.getAbsolutePath());
    }

    public void onRangeManuallyAdded(IPRange ipRange) {
        LOG.info("onRangeManuallyAdded() - " + ipRange);
        ipFilterTable.getDataModel().add(ipRange, ipFilterTable.getDataModel().getRowCount());
        ipFilterTable.refresh();
        BTEngine engine = BTEngine.getInstance();
        if (engine != null) {
            ip_filter currentFilter = engine.swig().get_ip_filter();
            error_code ec = new error_code();
            address addrStart = address.from_string(ipRange.startAddress(), ec);
            if (!ec.failed()) {
                address addrEnd = address.from_string(ipRange.endAddress(), ec);
                if (!ec.failed()) {
                    currentFilter.add_rule(addrStart, addrEnd, 0);
                    engine.swig().set_ip_filter(currentFilter);
                }
            }
        }
    }

}
