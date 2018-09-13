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
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.IOUtils;
import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
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

    private final Pattern P2P_LINE_PATTERN = Pattern.compile("(.*)\\:(.*)\\-(.*)");

    private final static Logger LOG = Logger.getLogger(IPFilterPaneItem.class);
    public final static String TITLE = I18n.tr("IP Filter");
    public final static String LABEL = I18n.tr("You can manually enter IPs and IP ranges to filter out, you can also import an IP block list from an URL or a file.");
    private final String IP_BLOCK_LIST_SEARCH_URL = "https://duckduckgo.com/?q=ip+filter+blocklist&t=h_&ia=noneofyourbusiness";

    private IPFilterTableMediator ipFilterTable;
    private JTextField fileUrlTextField;
    private JButton importButton;
    private IconButton fileChooserIcon;

    private boolean initialized;
    private long contentLength = -1;
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
        File f = new File(filterDataPath);
        InputStream is = null;

        if (f.exists()) {
            try {
                is = new FileInputStream(f);
                contentLength = f.length();
            } catch (IOException e) {
                is = null;
                enableImportControls(true);
                fileUrlTextField.selectAll();
            }
        } else {
            LOG.info("onImportButtonAction() trying URL");
            try {
                URI uri = URI.create(filterDataPath);
                HttpClient http = new JdkHttpClient();
                final PipedOutputStream pos = new PipedOutputStream();
                try {
                    is = new PipedInputStream(pos);
                } catch (IOException e) {
                    e.printStackTrace();
                    enableImportControls(true);
                    return;
                }

                final PipedInputStream pis = (PipedInputStream) is;
                http.setListener(new IPFilterHttpListener(pis, pos));

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
            } catch (IllegalArgumentException syntaxException) {
                LOG.error("Invalid URI");
                GUIMediator.showError(I18n.tr("Invalid URI or file path"));
                syntaxException.printStackTrace();
                enableImportControls(true);
                fileUrlTextField.selectAll();
            }
        }
        importFromStreamAsync(is);
    }

    private void importFromStreamAsync(final InputStream is) {
        BackgroundExecutorService.schedule(() -> {
            LOG.info("importFromStreamAsync(): thread invoked", true);
            try {
                if (is instanceof PipedInputStream) {
                    synchronized (is) {
                        LOG.info("importFromStreamAsync() waiting since this is a PipedInputStream", true);
                        is.wait();
                    }
                    LOG.info("importFromStreamAsync() we've been notified to keep going");
                }
            } catch (Throwable e) {
                e.printStackTrace();
                return;
            }

            InputStream safeInputStream;
            try {
                safeInputStream = getDecompressedStream(is);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            boolean isGzipped = safeInputStream instanceof GZIPInputStream;

            BufferedInputStream bis = new BufferedInputStream(safeInputStream);
            PushbackInputStream pis = new PushbackInputStream(bis, 1024);

            IPFilterFormat format = getFileFormat(pis);
            final IPFilterInputStreamReader ipFilterReader = (format == IPFilterFormat.P2P) ? new P2PIPFilterInputStreamReader(pis) : null;
            if (ipFilterReader == null) {
                LOG.error("importFromStreamAsync(): Invalid IP Filter file format, only p2p format supported");
                return;
            }
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File(CommonUtils.getUserSettingsDir(), "ip_filter.db"));
                IPFilterTableMediator.IPFilterModel dataModel = ipFilterTable.getDataModel();
                while (pis.available() > 0) {
                    IPFilterTableMediator.IPRange ipRange = ipFilterReader.readLine();
                    if (ipRange != null) {
                        ipRange.writeObjectTo(fos);
                        dataModel.add(ipRange, dataModel.getRowCount());

                        // every few imported ipRanges, let's do an UI update
                        if (dataModel.getRowCount() % 100 == 0) {
                            GUIMediator.safeInvokeLater(() -> {
                                if (!isGzipped) {
                                    updateImportedPercentage((int) ((ipFilterReader.bytesRead() * 100.0f / contentLength)));
                                }
                            });
                        }
                    }
                }
                GUIMediator.safeInvokeLater(() -> {
                    updateImportedPercentage(100);
                    ipFilterTable.refresh();
                    enableImportControls(true);
                    LOG.info("importFromStreamAsync() - done");
                });
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                IOUtils.closeQuietly(fos);
                IOUtils.closeQuietly(pis);
                enableImportControls(true);
            }
        });
    }

    private IPFilterFormat getFileFormat(InputStream is) {
        byte[] sample = new byte[1024];
        try {
            is.read(sample);
        } catch (IOException e) {
            return null;
        }
        Matcher matcher = P2P_LINE_PATTERN.matcher(new String(sample, StandardCharsets.UTF_8));
        if (matcher.find()) {
            return IPFilterFormat.P2P;
        }
        return null;
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
        File ipFilterDBFile = new File(CommonUtils.getUserSettingsDir(), "ip_filter.db");
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

    private void updateImportedPercentage(int percentage) {
        if (percentage == lastPercentage) {
            LOG.info("updateImportedPercentage() aborted, percentage hasn't changed (percentage=" + percentage + ")");
            return;
        }
        long timeSinceLastUpdate = System.currentTimeMillis() - lastImportedPercentageUpdateTimestamp;
        if (percentage < 99 && timeSinceLastUpdate < 1000) {
            LOG.info("updateImportedPercentage() aborted, too soon (timeSinceLastUpdate = " + timeSinceLastUpdate + ")");
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
            contentLength = -1;
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

    private static InputStream getDecompressedStream(InputStream input) throws IOException {
        PushbackInputStream pb = new PushbackInputStream(input, 2); //we need a pushbackstream to look ahead
        byte[] signature = new byte[2];
        int len = pb.read(signature); //read the signature
        pb.unread(signature, 0, len); //push back the signature to the stream
        if (signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b) { //check if matches standard gzip magic number
            return new GZIPInputStream(pb);
        } else {
            return pb;
        }
    }

    private interface IPFilterInputStreamReader {
        IPFilterTableMediator.IPRange readLine();

        int bytesRead();
    }

    private class P2PIPFilterInputStreamReader implements IPFilterInputStreamReader {
        private BufferedReader br;
        private InputStream is;
        private int bytesRead;

        P2PIPFilterInputStreamReader(InputStream is) {
            br = new BufferedReader(new InputStreamReader(is));
            this.is = is;
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
    }

    class IPFilterHttpListener implements HttpClient.HttpClientListener {
        private final PipedInputStream pis;
        private final PipedOutputStream pos;
        private int totalRead = 0;

        IPFilterHttpListener(PipedInputStream pis, PipedOutputStream pos) {
            this.pis = pis;
            this.pos = pos;
        }

        private void safePisNotify() {
            if (pis != null) {
                synchronized (pis) {
                    try {
                        pis.notify();
                    } catch (Throwable t) {
                        LOG.error(t.getMessage(), t, true);
                    }
                }
            }
        }

        @Override
        public void onError(HttpClient client, Throwable e) {
            safePisNotify();
            LOG.info("onError()");
            try {
                pos.close();
            } catch (IOException e2) {
                e.printStackTrace();
            }
            enableImportControls(true);
        }

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            //LOG.info("onData()");
            try {
                safePisNotify();
                pos.write(buffer, 0, buffer.length);
                pos.flush();
                totalRead += length;
                if (contentLength != -1) {
                    updateImportedPercentage((int) ((totalRead * 100.0f / contentLength)));
                }
            } catch (Throwable t) {
                client.onError((Exception) t);
                t.printStackTrace();
                enableImportControls(true);
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            LOG.info("onComplete()");
            safePisNotify();
            try {
                pos.flush();
                pos.close();
            } catch (IOException e) {
                e.printStackTrace();
                updateImportedPercentage(0);
                enableImportControls(true);
                return;
            }
            updateImportedPercentage(100);
            enableImportControls(true);
        }

        @Override
        public void onCancel(HttpClient client) {
            LOG.info("onCancel()");
            safePisNotify();
            try {
                pos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
}
