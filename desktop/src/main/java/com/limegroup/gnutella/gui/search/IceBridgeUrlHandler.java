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

package com.limegroup.gnutella.gui.search;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.search.relay.DhtPeerDiscoverySource;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerDiscovery;
import com.frostwire.search.relay.RemoteIndexFetcher;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.RemoteSearchResponse;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.search.relay.SearchResponseVerifier;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class IceBridgeUrlHandler {
    private static final Logger LOG = Logger.getLogger(IceBridgeUrlHandler.class);
    private static final String SCHEME = "icebridge://";
    private static final int ED25519_PUB_HEX_LEN = 64;
    private static final int SEARCH_RESPONSE_TIMEOUT_SEC = 10;
    private static final int SEARCH_RESULT_LIMIT = 25;
    private static final Clipboard CLIPBOARD = Toolkit.getDefaultToolkit().getSystemClipboard();
    private static volatile boolean registered;
    private static volatile String lastClipboardText;

    private IceBridgeUrlHandler() {
    }

    public static void handle(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        String trimmed = url.trim();
        if (!trimmed.toLowerCase().startsWith(SCHEME)) {
            showError(I18n.tr("Invalid IceBridge URL: must start with icebridge://"));
            return;
        }
        String remainder = trimmed.substring(SCHEME.length());
        int slash = remainder.indexOf('/');
        if (slash < 0) {
            showError(I18n.tr("Invalid IceBridge URL: missing command"));
            return;
        }
        String pubHex = remainder.substring(0, slash);
        String commandAndArgs = remainder.substring(slash + 1);
        if (pubHex.length() != ED25519_PUB_HEX_LEN || !isHex(pubHex)) {
            showError(I18n.tr("Invalid IceBridge URL: peer public key must be 64 hex characters (32 bytes)"));
            return;
        }
        byte[] peerPub;
        try {
            peerPub = Hex.decode(pubHex);
        } catch (Throwable t) {
            showError(I18n.tr("Invalid IceBridge URL: could not decode peer public key"));
            return;
        }
        if (peerPub == null || peerPub.length != 32) {
            showError(I18n.tr("Invalid IceBridge URL: peer public key must decode to 32 bytes"));
            return;
        }
        String command;
        String args = "";
        int cmdSlash = commandAndArgs.indexOf('/');
        if (cmdSlash < 0) {
            command = commandAndArgs;
        } else {
            command = commandAndArgs.substring(0, cmdSlash);
            args = commandAndArgs.substring(cmdSlash + 1);
        }
        command = command.toLowerCase();
        switch (command) {
            case "browse":
                handleBrowse(peerPub, pubHex);
                break;
            case "search":
                handleSearch(peerPub, pubHex, args);
                break;
            case "info":
                handleInfo(peerPub, pubHex);
                break;
            default:
                showError(I18n.tr("Unknown IceBridge command: {0}", command));
        }
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        if (OSUtils.isMacOSX()) {
            LOG.info("icebridge:// OS registration on macOS requires a plist entry or native code; "
                    + "add CFBundleURLTypes with CFBundleURLSchemes=icebridge to the app bundle Info.plist.");
        } else if (OSUtils.isWindows()) {
            LOG.info("icebridge:// OS registration on Windows requires HKEY_CLASSES_ROOT\\icebridge "
                    + "registry entry pointing to the FrostWire executable; not performed automatically.");
        } else {
            LOG.info("icebridge:// OS registration on this platform requires manual desktop entry configuration.");
        }
        try {
            GUIMediator.safeInvokeLater(() -> {
                Frame frame = GUIMediator.getAppFrame();
                if (frame instanceof Window) {
                    ((Window) frame).addWindowListener(new ClipboardMonitor());
                }
            });
        } catch (Throwable t) {
            LOG.warn("Failed to install IceBridge clipboard monitor", t);
        }
        LOG.info("IceBridge URL handler registered (clipboard monitor active)");
    }

    private static void handleBrowse(byte[] peerPub, String pubHex) {
        JDialog loading = showLoading(I18n.tr("Browsing peer catalog..."));
        SwingWorker<List<RemoteIndexFetcher.RemoteTorrentEntry>, Void> worker =
                new SwingWorker<>() {
                    @Override
                    protected List<RemoteIndexFetcher.RemoteTorrentEntry> doInBackground() {
                        try {
                            BTEngine btEngine = BTEngine.getInstance();
                            RemoteIndexFetcher.DhtIndexSource source =
                                    new RemoteIndexFetcher.DhtIndexSource(btEngine);
                            RemoteIndexFetcher fetcher = new RemoteIndexFetcher(source);
                            return fetcher.fetchCatalog(peerPub);
                        } catch (Throwable t) {
                            LOG.warn("IceBridge browse failed for peer " + pubHex, t);
                            return null;
                        }
                    }

                    @Override
                    protected void done() {
                        disposeLoading(loading);
                        try {
                            List<RemoteIndexFetcher.RemoteTorrentEntry> entries = get();
                            if (entries == null) {
                                showError(I18n.tr("Failed to fetch catalog from DHT for peer {0}", pubHex.substring(0, 12) + "..."));
                                return;
                            }
                            if (entries.isEmpty()) {
                                JOptionPane.showMessageDialog(GUIMediator.getAppFrame(),
                                        I18n.tr("Peer {0} has no shared torrents catalog published.", pubHex.substring(0, 12) + "..."),
                                        I18n.tr("IceBridge Browse"),
                                        JOptionPane.INFORMATION_MESSAGE);
                                return;
                            }
                            StringBuilder sb = new StringBuilder();
                            sb.append("<html><table width='100%'>");
                            sb.append("<tr><td><b>").append(I18n.tr("Name"))
                                    .append("</b></td><td><b>").append(I18n.tr("Size"))
                                    .append("</b></td><td><b>").append(I18n.tr("Files"))
                                    .append("</b></td></tr>");
                            for (RemoteIndexFetcher.RemoteTorrentEntry e : entries) {
                                sb.append("<tr><td>").append(escapeHtml(e.name()))
                                        .append("</td><td>").append(humanSize(e.sizeBytes()))
                                        .append("</td><td>").append(e.fileCount())
                                        .append("</td></tr>");
                            }
                            sb.append("</table></html>");
                            JOptionPane.showMessageDialog(GUIMediator.getAppFrame(),
                                    sb.toString(),
                                    I18n.tr("IceBridge Browse - {0} torrents", entries.size()),
                                    JOptionPane.INFORMATION_MESSAGE);
                        } catch (Exception e) {
                            LOG.warn("IceBridge browse result processing failed", e);
                            showError(I18n.tr("Error processing browse results: {0}", e.getMessage()));
                        }
                    }
                };
        worker.execute();
    }

    private static void handleSearch(byte[] peerPub, String pubHex, String encodedKeywords) {
        String keywords;
        try {
            keywords = URLDecoder.decode(encodedKeywords, StandardCharsets.UTF_8.name());
        } catch (Throwable t) {
            keywords = encodedKeywords;
        }
        if (keywords == null || keywords.trim().isEmpty()) {
            showError(I18n.tr("IceBridge search requires keywords in the URL path"));
            return;
        }
        SearchEngine distributed = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
        if (distributed == null || !distributed.isReady()) {
            showError(I18n.tr("Distributed search engine is not ready. Please wait for IceBridge to start."));
            return;
        }
        DistributedSearchTransport transport = distributed.getSearchTransport();
        IdentityKeys identity = distributed.identityKeys();
        if (transport == null || identity == null) {
            showError(I18n.tr("Distributed search transport or identity is not available."));
            return;
        }
        JDialog loading = showLoading(I18n.tr("Searching peer {0}...", pubHex.substring(0, 12) + "..."));
        final byte[] pubFinal = peerPub;
        final String kwFinal = keywords.trim();
        SwingWorker<List<RemoteSearchResponse.Row>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RemoteSearchResponse.Row> doInBackground() {
                try {
                    RemoteSearchRequest request = buildSignedRequest(kwFinal, identity);
                    byte[] payload = SearchPayloadCodec.encodeRequest(request);
                    AtomicReference<List<RemoteSearchResponse.Row>> resultRef = new AtomicReference<>();
                    CountDownLatch latch = new CountDownLatch(1);
                    final byte[] nonceHex = request.nonce();
                    DistributedSearchTransport.PayloadListener listener =
                            (sourcePub, responsePayload, receivedMs) -> {
                                try {
                                    if (!java.util.Arrays.equals(sourcePub, pubFinal)) {
                                        return;
                                    }
                                    RemoteSearchResponse response = SearchPayloadCodec.decodeResponse(responsePayload);
                                    if (response == null) {
                                        return;
                                    }
                                    if (!java.util.Arrays.equals(response.nonce(), nonceHex)) {
                                        return;
                                    }
                                    if (SearchResponseVerifier.verify(response, request, pubFinal)) {
                                        resultRef.set(response.rows());
                                    }
                                    latch.countDown();
                                } catch (Throwable t) {
                                    LOG.debug("IceBridge search response listener error", t);
                                }
                            };
                    transport.addListener(listener);
                    try {
                        boolean sent = transport.send(pubFinal, payload);
                        if (!sent) {
                            return null;
                        }
                        latch.await(SEARCH_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS);
                    } finally {
                        transport.removeListener(listener);
                    }
                    return resultRef.get();
                } catch (Throwable t) {
                    LOG.warn("IceBridge search failed for peer " + pubHex, t);
                    return null;
                }
            }

            @Override
            protected void done() {
                disposeLoading(loading);
                try {
                    List<RemoteSearchResponse.Row> rows = get();
                    if (rows == null) {
                        showError(I18n.tr("Failed to send search request to peer {0}. The peer may be offline.", pubHex.substring(0, 12) + "..."));
                        return;
                    }
                    if (rows.isEmpty()) {
                        JOptionPane.showMessageDialog(GUIMediator.getAppFrame(),
                                I18n.tr("No results from peer {0} for \"{1}\".", pubHex.substring(0, 12) + "...", kwFinal),
                                I18n.tr("IceBridge Search"),
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("<html><table width='100%'>");
                    sb.append("<tr><td><b>").append(I18n.tr("Name"))
                            .append("</b></td><td><b>").append(I18n.tr("Size"))
                            .append("</b></td><td><b>").append(I18n.tr("Files"))
                            .append("</b></td></tr>");
                    for (RemoteSearchResponse.Row row : rows) {
                        sb.append("<tr><td>").append(escapeHtml(row.name))
                                .append("</td><td>").append(humanSize(row.sizeBytes))
                                .append("</td><td>").append(row.fileCount)
                                .append("</td></tr>");
                    }
                    sb.append("</table></html>");
                    JOptionPane.showMessageDialog(GUIMediator.getAppFrame(),
                            sb.toString(),
                            I18n.tr("IceBridge Search - {0} results", rows.size()),
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    LOG.warn("IceBridge search result processing failed", e);
                    showError(I18n.tr("Error processing search results: {0}", e.getMessage()));
                }
            }
        };
        worker.execute();
    }

    private static void handleInfo(byte[] peerPub, String pubHex) {
        JDialog loading = showLoading(I18n.tr("Fetching peer info..."));
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    PeerDirectory directory = DistributedSearchEngineWire.getPeerDirectory();
                    if (directory != null) {
                        java.util.Optional<PeerDirectory.PeerInfo> info = directory.get(peerPub);
                        if (info.isPresent()) {
                            PeerDirectory.PeerInfo p = info.get();
                            StringBuilder sb = new StringBuilder();
                            sb.append("<html>");
                            sb.append("<b>").append(I18n.tr("Public Key")).append(":</b> ").append(pubHex).append("<br>");
                            sb.append("<b>").append(I18n.tr("Hostname")).append(":</b> ").append(escapeHtml(p.hostname())).append("<br>");
                            sb.append("<b>").append(I18n.tr("Port")).append(":</b> ").append(p.utpPort()).append("<br>");
                            sb.append("<b>").append(I18n.tr("Verified")).append(":</b> ").append(p.isVerified() ? I18n.tr("Yes") : I18n.tr("No")).append("<br>");
                            sb.append("<b>").append(I18n.tr("Endorsers")).append(":</b> ").append(p.endorserCount()).append("<br>");
                            sb.append("<b>").append(I18n.tr("Spam")).append(":</b> ").append(p.isSpam() ? I18n.tr("Yes") : I18n.tr("No")).append("<br>");
                            sb.append("<b>").append(I18n.tr("Last Updated")).append(":</b> ").append(new java.util.Date(p.lastUpdatedMs())).append("<br>");
                            sb.append("<b>").append(I18n.tr("Trust Score")).append(":</b> ").append(String.format("%.2f", directory.trustScore(peerPub)));
                            sb.append("</html>");
                            return sb.toString();
                        }
                    }
                    BTEngine btEngine = BTEngine.getInstance();
                    DhtPeerDiscoverySource source = new DhtPeerDiscoverySource(btEngine);
                    PeerDirectory tempDir = new PeerDirectory(new com.frostwire.search.relay.PeerKarmaCache(
                            new com.frostwire.search.relay.RemoteKarmaChainFetcher(
                                    new com.frostwire.search.relay.DhtKarmaChainSource(btEngine))));
                    PeerDiscovery discovery = new PeerDiscovery(source, tempDir);
                    IdentityRecord record = discovery.fetchIdentityRecord(peerPub);
                    if (record == null) {
                        return null;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("<html>");
                    sb.append("<b>").append(I18n.tr("Public Key")).append(":</b> ").append(pubHex).append("<br>");
                    sb.append("<b>").append(I18n.tr("Node ID")).append(":</b> ").append(Hex.encode(record.nodeId())).append("<br>");
                    sb.append("<b>").append(I18n.tr("uTP Port")).append(":</b> ").append(record.utpPort()).append("<br>");
                    sb.append("<b>").append(I18n.tr("First Seen")).append(":</b> ").append(new java.util.Date(record.firstSeen() * 1000L)).append("<br>");
                    sb.append("<b>").append(I18n.tr("Last Seen")).append(":</b> ").append(new java.util.Date(record.lastSeen() * 1000L)).append("<br>");
                    sb.append("<b>").append(I18n.tr("Signature Valid")).append(":</b> ").append(record.verifySignature() ? I18n.tr("Yes") : I18n.tr("No"));
                    sb.append("<br><i>").append(I18n.tr("(Fetched from DHT; peer not in local directory)")).append("</i>");
                    sb.append("</html>");
                    return sb.toString();
                } catch (Throwable t) {
                    LOG.warn("IceBridge info failed for peer " + pubHex, t);
                    return null;
                }
            }

            @Override
            protected void done() {
                disposeLoading(loading);
                try {
                    String info = get();
                    if (info == null) {
                        JOptionPane.showMessageDialog(GUIMediator.getAppFrame(),
                                I18n.tr("Peer {0} is not known and no identity record was found in the DHT.", pubHex.substring(0, 12) + "..."),
                                I18n.tr("IceBridge Peer Info"),
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    JOptionPane.showMessageDialog(GUIMediator.getAppFrame(),
                            info,
                            I18n.tr("IceBridge Peer Info"),
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    LOG.warn("IceBridge info result processing failed", e);
                    showError(I18n.tr("Error fetching peer info: {0}", e.getMessage()));
                }
            }
        };
        worker.execute();
    }

    private static RemoteSearchRequest buildSignedRequest(String keywords, IdentityKeys identity) throws Exception {
        byte[] nonce = new byte[32];
        SecureRandom.getInstanceStrong().nextBytes(nonce);
        long timestamp = System.currentTimeMillis() / 1000L;
        byte[] ownPub = identity.ed25519PubRaw();
        RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(SEARCH_RESULT_LIMIT)
                .nonce(nonce)
                .ttl(1)
                .requesterPub(ownPub)
                .path(new byte[][]{ownPub})
                .timestamp(timestamp)
                .signature(new byte[64])
                .build();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();
        return RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(SEARCH_RESULT_LIMIT)
                .nonce(nonce)
                .ttl(1)
                .requesterPub(ownPub)
                .path(new byte[][]{ownPub})
                .timestamp(timestamp)
                .signature(sig)
                .build();
    }

    private static JDialog showLoading(String message) {
        final JDialog[] dialogHolder = new JDialog[1];
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                JDialog dialog = new JDialog(GUIMediator.getAppFrame(), I18n.tr("IceBridge"), false);
                JPanel panel = new JPanel(new BorderLayout(10, 10));
                panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20));
                JProgressBar bar = new JProgressBar();
                bar.setIndeterminate(true);
                panel.add(new JLabel(message), BorderLayout.NORTH);
                panel.add(bar, BorderLayout.CENTER);
                dialog.setContentPane(panel);
                dialog.pack();
                dialog.setLocationRelativeTo(GUIMediator.getAppFrame());
                dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                dialogHolder[0] = dialog;
                dialog.setVisible(true);
            } catch (Throwable t) {
                LOG.warn("Failed to show loading dialog", t);
            }
        });
        return dialogHolder[0];
    }

    private static void disposeLoading(JDialog dialog) {
        if (dialog == null) {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                dialog.setVisible(false);
                dialog.dispose();
            } catch (Throwable ignored) {
            }
        });
    }

    private static void showError(String message) {
        GUIMediator.safeInvokeLater(() -> JOptionPane.showMessageDialog(GUIMediator.getAppFrame(),
                message,
                I18n.tr("IceBridge Error"),
                JOptionPane.ERROR_MESSAGE));
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        do {
            v /= 1024.0;
            i++;
        } while (v >= 1024 && i < units.length - 1);
        return String.format("%.1f %s", v, units[i]);
    }

    private static final class ClipboardMonitor extends WindowAdapter {
        @Override
        public void windowActivated(WindowEvent e) {
            try {
                java.awt.datatransfer.Transferable content = CLIPBOARD.getContents(null);
                if (content == null || !content.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                    return;
                }
                String text = (String) content.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                if (text == null) {
                    return;
                }
                text = text.trim();
                if (text.isEmpty()) {
                    return;
                }
                String prev = lastClipboardText;
                if (prev != null && prev.equals(text)) {
                    return;
                }
                lastClipboardText = text;
                if (text.toLowerCase().startsWith(SCHEME)) {
                    try {
                        CLIPBOARD.setContents(new StringSelection(""), null);
                    } catch (Throwable ignored) {
                    }
                    handle(text);
                }
            } catch (Throwable t) {
                LOG.debug("IceBridge clipboard monitor error", t);
            }
        }
    }
}
