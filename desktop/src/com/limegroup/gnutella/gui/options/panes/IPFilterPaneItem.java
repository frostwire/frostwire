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
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconButton;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.IOUtils;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    private final String IP_BLOCK_LIST_SEARCH_URL = "https://www.google.com/search?q=ip+filter+block+list+p2p+format";

    private IPFilterTableMediator ipFilterTable;
    private JTextField fileUrlTextField;
    private IconButton fileChooserIcon;
    private JButton importButton;
    private CountDownLatch inputStreamLatch;

    private boolean initialized;

    public IPFilterPaneItem() {
        super(TITLE, LABEL);
        ipFilterTable = null;
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
            throw new RuntimeException("WTF! no ip filter then????");
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
        String filterDataPath = fileUrlTextField.getText();
        if (null == filterDataPath || "".equals(filterDataPath)) {
            return;
        }
        File f = new File(filterDataPath);
        InputStream is = null;
        inputStreamLatch = new CountDownLatch(1);
        if (f.exists()) {
            try {
                is = new FileInputStream(f);
                inputStreamLatch.countDown();
            } catch (IOException e) {
                is = null;
            }
        } else {
            try {
                URI uri = URI.create(filterDataPath);
                HttpClient http = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
                final PipedOutputStream pos = new PipedOutputStream();

                try {
                    is = new PipedInputStream(pos);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                http.setListener(new HttpClient.HttpClientListener() {

                    private int totalRead = 0;

                    @Override
                    public void onError(HttpClient client, Throwable e) {
                        try {
                            pos.close();
                        } catch (IOException e2) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onData(HttpClient client, byte[] buffer, int offset, int length) {
                        try {
                            pos.write(buffer);
                            pos.flush();
                            totalRead += length;

                            if (inputStreamLatch.getCount() == 1 &&totalRead > 2) {
                                inputStreamLatch.countDown();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onComplete(HttpClient client) {
                        try {
                            pos.flush();
                            pos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCancel(HttpClient client) {
                        try {
                            pos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {

                    }
                });

                BackgroundExecutorService.schedule(() -> {
                    try {
                        http.get(uri.toURL().toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IllegalArgumentException syntaxException) {
                LOG.error("Invalid URI");
                GUIMediator.showError(I18n.tr("Invalid URI or file path"));
            }
        }
        importFromStreamAsync(is);
    }

    private void importFromStreamAsync(final InputStream is) {
        BackgroundExecutorService.schedule(() -> {
            try {
                inputStreamLatch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            InputStream safeInputStream = null;
            try {
                safeInputStream = getDecompressedStream(is);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            BufferedInputStream bis = new BufferedInputStream(safeInputStream);
            PushbackInputStream pis = new PushbackInputStream(bis, 1024);

            IPFilterFormat format = getFileFormat(pis);
            IPFilterInputStreamReader ipFilterReader = null;
            if (format == IPFilterFormat.P2P) {
                ipFilterReader = new P2PIPFilterInputStreamReader(pis);
            }
            if (ipFilterReader == null) {
                LOG.error("importFromStreamAsync(): Invalid IP Filter file format, only p2p format supported");
                return;
            }
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(new File(CommonUtils.getUserSettingsDir(), "ip_filter.db"));
                while (pis.available() > 0) {
                    IPFilterTableMediator.IPRange ipRange = ipFilterReader.readLine();
                    if (ipRange != null) {
                        ipRange.writeObjectTo(fos);
                        ipFilterTable.getDataModel().add(ipRange);
                        if (ipFilterTable.getDataModel().getRowCount() % 5 == 0) {
                            GUIMediator.safeInvokeLater(() -> ipFilterTable.refresh());
                        }
                    }
                }
                GUIMediator.safeInvokeLater(() -> ipFilterTable.refresh());
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                IOUtils.closeQuietly(fos);
                IOUtils.closeQuietly(pis);
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

        Matcher matcher = null;
        try {
            matcher = P2P_LINE_PATTERN.matcher(new String(sample, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (matcher.find()) {
            return IPFilterFormat.P2P;
        }
        return null;
    }

    private void onFileChooserIconAction() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle(I18n.tr("Select the IP filter file (p2p format only)"));
        fileChooser.setApproveButtonText(I18n.tr("Select"));
        int result = fileChooser.showOpenDialog(getContainer());
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            fileUrlTextField.setText(selectedFile.getAbsolutePath());
        } else if (result == JFileChooser.CANCEL_OPTION) {
        } else if (result == JFileChooser.ERROR_OPTION) {
            LOG.error("Error selecting the file");
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
            FileInputStream fis = new FileInputStream(ipFilterDBFile);
            int ranges = 0;
            while (fis.available() > 0) {
                try {
                    ipFilterTable.getDataModel().add(IPFilterTableMediator.IPRange.readObjectFrom(fis));
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
    }

    private class P2PIPFilterInputStreamReader implements IPFilterInputStreamReader {
        private BufferedReader br;
        private InputStream is;
        P2PIPFilterInputStreamReader(InputStream is) {
            br = new BufferedReader(new InputStreamReader(is));
            this.is = is;
        }

        @Override
        public IPFilterTableMediator.IPRange readLine() {
            try {
                if (is.available() > 0) {
                    String line = br.readLine();
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
    }
}
