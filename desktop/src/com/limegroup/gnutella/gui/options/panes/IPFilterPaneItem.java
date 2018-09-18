/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
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
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPFilterInputStreamReader;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.IOUtils;
import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPInputStream;

import static com.frostwire.gui.theme.ThemeMediator.fixKeyStrokes;

public class IPFilterPaneItem extends AbstractPaneItem {
    enum IPFilterFormat {
        P2P
    }

    private final static Pattern P2P_LINE_PATTERN = Pattern.compile("(.*)\\:(.*)\\-(.*)");

    private final static Logger LOG = Logger.getLogger(IPFilterPaneItem.class);
    public final static String TITLE = I18n.tr("IP Filter");
    public final static String LABEL = I18n.tr("You can manually enter IP addresses ranges to filter out, you can also import bulk addresses from an IP block list file or URL");
    private final String IP_BLOCK_LIST_SEARCH_URL = "https://duckduckgo.com/?q=ip+filter+blocklist&t=h_&ia=noneofyourbusiness";

    private IPFilterTableMediator ipFilterTable;
    private JTextField fileUrlTextField;
    private JButton importButton;
    private IconButton fileChooserIcon;

    private boolean initialized;
    private int lastPercentage = -1;
    private long lastImportedPercentageUpdateTimestamp;
    private final ExecutorService httpExecutor;


    public IPFilterPaneItem() {
        super(TITLE, LABEL);
        ipFilterTable = null;
        httpExecutor = ExecutorsHelper.newProcessingQueue("IPFilterPanelItem-http");
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
        JLabel findIpLabel = new JLabel("<html><a href=\"" + IP_BLOCK_LIST_SEARCH_URL + "\">" + I18n.tr("Find IP Block Lists") + "</a></html>");
        findIpLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        findIpLabel.addMouseListener(onFindIPBlockListsClick());
        panel.add(findIpLabel, "wrap");

        ipFilterTable = IPFilterTableMediator.getInstance();
        BackgroundExecutorService.schedule(this::loadSerializedIPFilter);

        panel.add(ipFilterTable.getComponent(), "span, pad 0 0 0 0, grow, wrap");
        panel.add(new JLabel(I18n.tr("Enter the URL or local file path of an IP Filter list (p2p format only supported)")), "pad 0 5px, span, wrap");
        fileUrlTextField = new JTextField();
        fixKeyStrokes(fileUrlTextField);
        panel.add(fileUrlTextField, "span 6, growx");
        fileChooserIcon = new IconButton("OPEN_IP_FILTER_FILE", 24, 24);
        panel.add(fileChooserIcon, "span 1");
        importButton = new JButton(I18n.tr("Import"));
        panel.add(importButton, "span 1, wrap");

        JButton addRangeManuallyButton = new JButton(I18n.tr("Add IP Range Manually"));
        addRangeManuallyButton.addActionListener((e) -> onAddRangeManuallyAction());
        panel.add(addRangeManuallyButton);

        JButton clearFilterButton = new JButton(I18n.tr("Clear IP Block List"));
        clearFilterButton.addActionListener((e) -> onClearFilterAction());
        panel.add(clearFilterButton);
        add(panel);

        fileChooserIcon.addActionListener((e) -> onFileChooserIconAction());
        importButton.addActionListener((e) -> onImportButtonAction());
    }

    private MouseListener onFindIPBlockListsClick() {
        return new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                GUIMediator.openURL(IP_BLOCK_LIST_SEARCH_URL);
            }
        };
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
                http.setListener(new IPFilterHttpListener(new File(CommonUtils.getUserSettingsDir(), "downloaded_blocklist.temp")));
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

    private void onGunzipProgress(int percentageGunzipped) {
        //TODO
        LOG.info("onGunzipProgress: " + percentageGunzipped);
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
        // TODO: Clear actual IP Filter from BTEngine
        enableImportControls(true);
    }

    private void importFromIPBlockFileAsync(final File potentialGunzipFile, boolean removeInputFileWhenDone) {
        // decompress if zip file
        // import file

        BackgroundExecutorService.schedule(() -> {
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
                while (ipFilterReader.available() > 0) {
                    IPFilterTableMediator.IPRange ipRange = ipFilterReader.readLine();
                    if (ipRange != null) {
                        try {
                            ipRange.writeObjectTo(fos);
                            dataModel.add(ipRange, dataModel.getRowCount());

                            // every few imported ipRanges, let's do an UI update
                            if (dataModel.getRowCount() % 100 == 0) {
                                GUIMediator.safeInvokeLater(() -> updateDownloadedPercentage((int) ((ipFilterReader.bytesRead() * 100.0f / decompressedFileSize))));
                            }

                            // TODO: add to actual ip block filter
                        } catch (Throwable t) {
                            LOG.warn(t.getMessage(), t);
                            // just keep going
                        }
                    }
                }
                GUIMediator.safeInvokeLater(() -> {
                    updateDownloadedPercentage(100);
                    ipFilterTable.refresh();
                    enableImportControls(true);
                    fileUrlTextField.setText("");
                    LOG.info("importFromStreamAsync() - done");
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
        if (matcher.find()) {
            return IPFilterFormat.P2P;
        }
        return null;
    }

    private void onAddRangeManuallyAction() {
        new AddRangeManuallyDialog(this).setVisible(true);
    }

    private void onFileChooserIconAction() {
        final File selectedFile = FileChooserHandler.getInputFile(getContainer(), I18n.tr("Select the IP filter file (p2p format only)"), FileChooserHandler.getLastInputDirectory(), new FileFilter() {
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
                    dataModel.add(IPFilterTableMediator.IPRange.readObjectFrom(fis), dataModel.getRowCount());
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getIPFilterDBFile() {
        return new File(CommonUtils.getUserSettingsDir(), "ip_filter.db");
    }

    private void updateDownloadedPercentage(int percentage) {
        if (percentage == lastPercentage) {
            return;
        }
        long timeSinceLastUpdate = System.currentTimeMillis() - lastImportedPercentageUpdateTimestamp;
        if (percentage < 99 && timeSinceLastUpdate < 1000) {
            return;
        }
        lastPercentage = percentage;
        GUIMediator.safeInvokeLater(() -> {
            importButton.setText(I18n.tr("Importing... (" + percentage + "%)"));
            importButton.setEnabled(false);
            lastImportedPercentageUpdateTimestamp = System.currentTimeMillis();
            importButton.repaint();
        });
    }

    private void enableImportControls(boolean enable) {
        GUIMediator.safeInvokeLater(() -> {
            fileUrlTextField.setEnabled(enable);
            fileChooserIcon.setEnabled(enable);
            importButton.setText(enable ? I18n.tr("Import") : I18n.tr("Importing..."));
            importButton.setEnabled(enable);
            lastImportedPercentageUpdateTimestamp = -1;
            lastPercentage = -1;
            if (enable) {
                fileUrlTextField.requestFocus();
                fileUrlTextField.selectAll();
            }
        });
    }

    @Override
    public boolean applyOptions() throws IOException {
        return false;
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    private static boolean isGZipped(File f) throws IOException {
        byte[] signature = new byte[2];
        FileInputStream fis = new FileInputStream(f);
        fis.read(signature);
        return signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b;
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

            //4096|8192|16384|32768
            byte[] buffer = new byte[8192];
            GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(gzipped), buffer.length);
            FileOutputStream fileOutputStream = new FileOutputStream(gunzipped, false);
            int totalGunzipped = 0;
            while (gzipInputStream.available() == 1) {
                int read = 0;
                try {
                    read = gzipInputStream.read(buffer);
                    if (read > 0) {
                        totalGunzipped += read;
                        fileOutputStream.write(buffer, 0, read);
                        onGunzipProgress((int) ((totalGunzipped * 100.0f / uncompressedSize)));
                    }
                } catch (Throwable t) {
                    LOG.info("read = " + read);
                    LOG.info("totalGunzipped = " + totalGunzipped);
                    LOG.info("buffer = " + buffer);
                    t.printStackTrace();
                    gunzipped.delete();
                    return null;
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

    private static class P2PIPFilterInputStreamReader implements IPFilterInputStreamReader {
        private BufferedReader br;
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
        public IPFilterTableMediator.IPRange readLine() {
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
                        return new IPFilterTableMediator.IPRange(matcher.group(1), matcher.group(2), matcher.group(3));
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

    private class IPFilterHttpListener implements HttpClient.HttpClientListener {
        private int totalRead = 0;
        private long contentLength = -1;
        private final File downloadedFile;
        private final FileOutputStream fos;

        IPFilterHttpListener(File downloadedFile) throws FileNotFoundException {
            this.downloadedFile = downloadedFile;
            try {
                fos = new FileOutputStream(downloadedFile);
            } catch (FileNotFoundException e) {
                LOG.error("IPFilterHttpListener can't create output stream -> " + e.getMessage(), e);
                throw e;
            }
        }

        @Override
        public void onError(HttpClient client, Throwable e) {
            LOG.info("onError(): " + e.getMessage());
            LOG.error(e.getMessage(), e);
            enableImportControls(true);
        }

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            totalRead += length;
            try {
                fos.write(buffer, offset, length);
            } catch (Throwable t) {
                onError(client, t);
                return;
            }
            if (contentLength != -1) {
                updateDownloadedPercentage((int) ((totalRead * 100.0f / contentLength)));
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            LOG.info("onComplete()");
            try {
                fos.flush();
                fos.close();
            } catch (Throwable t) {
                LOG.error("onComplete(): " + t.getMessage(), t);
                onError(client, t);
                return;
            }
            updateDownloadedPercentage(100);
            enableImportControls(true);
            importFromIPBlockFileAsync(downloadedFile, true);
        }

        @Override
        public void onCancel(HttpClient client) {
            enableImportControls(true);
        }

        @Override
        public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
            LOG.info("onHeaders()");
            if (headerFields != null && headerFields.containsKey("Content-Length")) {
                List<String> contentLengthHeader = headerFields.get("Content-Length");
                if (contentLengthHeader != null && !contentLengthHeader.isEmpty()) {
                    contentLength = Long.parseLong(contentLengthHeader.get(0));
                }
            }
        }
    }

    private void onRangeManuallyAdded(IPFilterTableMediator.IPRange ipRange) {
        LOG.info("onRangeManuallyAdded() - " + ipRange);
    }

    private static class AddRangeManuallyDialog extends JDialog {

        private final IPFilterPaneItem dialogListener;
        private final JTextField descriptionTextField;
        private final JTextField rangeStartTextField;
        private final JTextField rangeEndTextField;

        AddRangeManuallyDialog(IPFilterPaneItem dialogListener) {
            super(getParentDialog(dialogListener), true);
            final String addIPRangeManuallyString = I18n.tr("Add IP Range Manually");
            setTitle(addIPRangeManuallyString);
            this.dialogListener = dialogListener;
            JPanel panel = new JPanel(new MigLayout("fillx, ins 0, insets, nogrid"));
            panel.add(new JLabel(I18n.tr("Description")),"wrap");
            descriptionTextField = new JTextField();
            panel.add(descriptionTextField, "growx, wrap");

            panel.add(new JLabel("<html><strong>" + I18n.tr("Starting IP address") + "</strong></html>"),"wrap");
            rangeStartTextField = new JTextField();
            panel.add(rangeStartTextField, "w 250px, wrap");

            panel.add(new JLabel("<html><strong>" +
                    I18n.tr("Ending IP address") +
                    "</strong><br/><i>" +
                    I18n.tr("Leave blank or repeat 'Starting IP address' to block a single one") +
                    "</i></html>"), "growx ,wrap");

            rangeEndTextField = new JTextField();
            panel.add(rangeEndTextField, "w 250px, gapbottom 10px, wrap");

            fixKeyStrokes(descriptionTextField);
            fixKeyStrokes(rangeStartTextField);
            fixKeyStrokes(rangeEndTextField);

            JButton addRangeButton = new JButton(addIPRangeManuallyString);
            panel.add(addRangeButton,"growx");
            addRangeButton.addActionListener((e) -> onAddRangeButtonClicked());
            JButton cancelButton = new JButton(I18n.tr("Cancel"));
            cancelButton.addActionListener((e) -> dispose());
            panel.add(cancelButton,"growx");
            setContentPane(panel);
            setResizable(false);
            setLocationRelativeTo(getParent());
            pack();
        }

        private void onAddRangeButtonClicked() {
            if (!validateInput()) {
                return;
            }
            dispose();
            dialogListener.onRangeManuallyAdded(
                    new IPFilterTableMediator.IPRange(
                            descriptionTextField.getText(),
                            rangeStartTextField.getText(),
                            rangeEndTextField.getText()));
        }

        private boolean validateInput() {
            LOG.warn("AddRangeManuallyDialog::validateInput() NOT IMPLEMENTED");
            try {
                new IPFilterTableMediator.IPRange(
                        descriptionTextField.getText(),
                        rangeStartTextField.getText(),
                        rangeEndTextField.getText());
            } catch (IllegalArgumentException e) {
                return false;
            }
            return true;
        }

        private static JDialog getParentDialog(IPFilterPaneItem paneItem) {
            Component result = paneItem.getContainer();
            do {
                result = result.getParent();
                LOG.info("getParentDialog: getContainer -> " + result.getClass().getName());
            } while (!(result instanceof JDialog));
            return (JDialog) result;
        }
    }
}
