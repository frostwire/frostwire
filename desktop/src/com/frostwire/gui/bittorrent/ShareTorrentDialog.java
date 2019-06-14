/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.UserAgentGenerator;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpClientListenerAdapter;
import com.limegroup.gnutella.gui.ButtonRow;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.LimeAction;
import org.limewire.concurrent.ThreadExecutor;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @author gubatron
 * @author aldenml
 */
public class ShareTorrentDialog extends JDialog {
    private final TorrentInfo torrent;
    private Container container;
    private JEditorPane textArea;
    private Action[] actions;
    private JLabel feedbackLabel;
    private List<URLShortenerHttpClientListener> shortenerListeners;
    private String link;
    private String info_hash;
    private String torrent_name;

    public ShareTorrentDialog(JFrame frame, TorrentInfo torrent) {
        super(frame);
        this.torrent = torrent;
        setupUI();
        setLocationRelativeTo(frame);
    }

    private void initURLShortenerListeners() {
        final URLShortenerHttpClientListener tinyurlShortenerListener = new URLShortenerHttpClientListener(
                "http://tinyurl.com/api-create.php?url=" + getLink());
        // this one is different because google got fancy with POST and Json.
        final URLShortenerHttpClientListener googShortenerListener = new GoogleURLShortenerListener("https://www.googleapis.com/urlshortener/v1/url?key=AIzaSyDw6xPSKYZyOIv7rq2A0R9fDvzsrpI25I0");
        // we'll use one at random to not exhaust the monthly quotas.
        shortenerListeners = new LinkedList<>(Arrays.asList(
                googShortenerListener,
                tinyurlShortenerListener));
    }

    private void updateTextArea() {
        GUIMediator.safeInvokeLater(() -> {
            textArea.setText(I18n.tr("Download") + " \"" + torrent_name.replace("_", " ") + "\" " + I18n.tr("at") + " "
                    + getLink().trim() + " " + I18n.tr("via FrostWire"));
            textArea.selectAll();
        });
    }

    private void setupUI() {
        setupWindow();
        initTorrentName();
        initInfoHash();
        GridBagConstraints c = new GridBagConstraints();
        // INTRO LABEL
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(20, 10, 10, 10);
        boolean folderTorrent = torrent.numFiles() > 1;
        JLabel _introLabel = new JLabel(folderTorrent ? String.format(
                I18n.tr("Use the following text to share the \"%s\" folder"),
                torrent_name) : String.format(I18n.tr(
                "Use the following text to share the \"%s\" file"),
                torrent_name));
        _introLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        container.add(_introLabel, c);
        // TEXT AREA
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 0.6;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(1, 10, 1, 10);
        textArea = new JEditorPane();
        textArea.setEditable(false);
        updateTextArea();
        Font f = new Font("Dialog", Font.PLAIN, 14);
        textArea.setFont(f);
        textArea.setMargin(new Insets(10, 10, 10, 10));
        textArea.setBorder(BorderFactory.createEtchedBorder());
        textArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                textArea.selectAll();
            }
        });
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean allTextAlreadySelected = textArea.getSelectedText() != null && textArea.getSelectedText().equals(textArea.getText());
                if (allTextAlreadySelected || SwingUtilities.isRightMouseButton(e)) {
                    textArea.select(0, 0);
                    return;
                }
                textArea.selectAll();
            }
        });
        container.add(textArea, c);
        initURLShortenerListeners();
        performAsyncURLShortening(shortenerListeners.get(new Random().nextInt(shortenerListeners.size() - 1)));
        // BUTTON ROW
        initActions();
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.CENTER;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(10, 10, 10, 10);
        ButtonRow buttonRow = new ButtonRow(actions, ButtonRow.X_AXIS,
                ButtonRow.RIGHT_GLUE);
        fixButtonsFont(buttonRow);
        fixButtonBorders(buttonRow);
        ToolTipManager.sharedInstance().setInitialDelay(200);
        container.add(buttonRow, c);
        // TIPS LABEL
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(0, 10, 0, 10);
        container.add(new JLabel(I18n.tr("Tips")), c);
        // TIPS HTML BULLETS.
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1.0;
        c.insets = new Insets(10, 10, 10, 10); //component distance from others.
        JLabel _tipsLabel = new JLabel("<html><p> > " +
                I18n.tr("<strong>Keep FrostWire Open</strong> until the file has been downloaded by at least one other friend.") + "</p><p>&nbsp;</p><p> >" +
                I18n.tr("<strong>The more, the merrier.</strong> The more people sharing the faster it can be downloaded by others.") + "</p><p>&nbsp;</p><p> >" +
                I18n.tr("<strong>Your files can be discovered by others.</strong> Once you share this link and you seed the files they will be available to everybody on the BitTorrent network.") + "</p></html>");
        _tipsLabel.setFont(new Font("Dialog", Font.PLAIN, 14));
        Border tipsBorder = BorderFactory.createSoftBevelBorder(BevelBorder.LOWERED);
        tipsBorder.getBorderInsets(_tipsLabel).set(20, 20, 20, 20);
        _tipsLabel.setBorder(tipsBorder);
        container.add(_tipsLabel, c);
        // FEEDBACK LABEL
        JPanel glass = (JPanel) getGlassPane();
        glass.setLayout(null);
        glass.setVisible(true);
        feedbackLabel = new JLabel(I18n.tr("Feedback here to clipboard"));
        feedbackLabel.setVisible(false);
        feedbackLabel.setFont(new Font("Arial", Font.BOLD, 14));
        glass.add(feedbackLabel);
        feedbackLabel.setBounds(100, 100, 300, 20);
        addEscapeKeyListener();
        GUIUtils.addHideAction((JComponent) getContentPane());
        pack();
    }

    private void addEscapeKeyListener() {
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void fixButtonsFont(ButtonRow buttonRow) {
        if (actions == null) {
            return;
        }
        for (int i = 0; i < actions.length; i++) {
            JButton buttonAtIndex = buttonRow.getButtonAtIndex(i);
            buttonAtIndex.setFont(new Font("Lucida Grande", Font.BOLD, 16));
        }
    }

    private void fixButtonBorders(ButtonRow buttonRow) {
        if (actions == null) {
            return;
        }
        for (int i = 0; i < actions.length; i++) {
            JButton buttonAtIndex = buttonRow.getButtonAtIndex(i);
            buttonAtIndex.setBorderPainted(true);
        }
    }

    private void initTorrentName() {
        torrent_name = torrent.name();
    }

    private void initInfoHash() {
        info_hash = torrent.infoHash().toString();
    }

    private String getLink() {
        if (link == null) {
            link = "http://maglnk.xyz/" + info_hash + "/?" + TorrentUtil.getMagnetURLParameters(torrent);
        }
        return link;
    }

    private void performAsyncURLShortening(final URLShortenerHttpClientListener listener) {
        final HttpClient browser = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
        browser.setListener(listener);
        ThreadExecutor.startThread(listener.getHttpRequestRunnable(browser), "ShareTorrentDialog-performAsyncURLShortening");
    }

    private void initActions() {
        actions = new Action[4];
        actions[0] = new TwitterAction();
        actions[1] = new CopyToClipboardAction();
        actions[2] = new CopyLinkAction();
        actions[3] = new CopyMagnetAction();
    }

    private void setupWindow() {
        setTitle(I18n.tr("All done! Now share the link"));
        Dimension prefDimension = new Dimension(640, 390);
        setSize(prefDimension);
        setMinimumSize(prefDimension);
        setPreferredSize(prefDimension);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);
        GUIUtils.addHideAction((JComponent) getContentPane());
        container = getContentPane();
        container.setLayout(new GridBagLayout());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ToolTipManager.sharedInstance().setInitialDelay(500);
            }
        });
    }

    /**
     * Animates the feedbackLabel (which lives on the Glass Pane invisibly and is shown during the animation)
     * <p/>
     * To animate this component we wrap the JLabel on a TimelineJLabel.
     * If we ever need to animate more labels, we'll probably just extend JLabel in the future.
     * <p/>
     * The animation interpolates 2 properties. The color of the text, and the y position of the text.
     * <p/>
     * It looks like it disappears by changing the color into the background color.
     */
    private void showFeedback(String title, double x, double y) {
        int startY = (int) (y - getLocationOnScreen().getY() - 40);
        feedbackLabel.setLocation((int) (x - getLocationOnScreen().getX()), startY);
        feedbackLabel.setVisible(true);
        feedbackLabel.setText(title);
    }

    private class URLShortenerHttpClientListener extends
            HttpClientListenerAdapter {
        private final String shortenerUri;

        URLShortenerHttpClientListener(String uri) {
            shortenerUri = uri;
        }

        @Override
        public void onError(HttpClient client, Throwable e) {
            if (shortenerListeners.size() > 1) {
                shortenerListeners.remove(0);
                performAsyncURLShortening(shortenerListeners.get(0));
                System.out.println(">>> URLShortenerHttpClientListener ERROR >>>");
                e.printStackTrace();
                System.out.println();
                System.out.println("Try shortening with URL: [" + shortenerUri + "]");
                System.out.println(">>> URLShortenerHttpClientListener ERROR >>>");
            }
        }

        String getShortenerURL() {
            return shortenerUri;
        }

        Runnable getHttpRequestRunnable(final HttpClient browser) {
            return () -> {
                try {
                    System.out.println("Shortening with " + getShortenerURL());
                    link = browser.get(getShortenerURL(), 2000);
                    updateTextArea(); //happens safely on UI thread.
                } catch (Throwable ignored) {
                }
            };
        }
    }

    private class GoogleURLShortenerResponse {
        String id;
    }

    private class GoogleURLShortenerListener extends URLShortenerHttpClientListener {
        GoogleURLShortenerListener(String uri) {
            super(uri);
        }

        @Override
        protected Runnable getHttpRequestRunnable(final HttpClient browser) {
            return () -> {
                try {
                    final String jsonRequest = "{\"longUrl\": \"" + getLink() + "\"}";
                    final String jsonResponse = browser.post(getShortenerURL(), 2000, UserAgentGenerator.getUserAgent(), jsonRequest, "application/json", false);
                    GoogleURLShortenerResponse response = JsonUtils.toObject(jsonResponse, GoogleURLShortenerResponse.class);
                    link = response.id;
                    updateTextArea(); //happens safely on UI thread.
                } catch (Throwable ignored) {
                }
            };
        }
    }

    private class TwitterAction extends AbstractAction {
        TwitterAction() {
            putValue(Action.NAME, I18n.tr("Twitter it"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Send the message above to Twitter"));
            putValue(LimeAction.ICON_NAME, "TWITTER");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator
                    .openURL("http://twitter.com/intent/tweet?source=FrostWire&text="
                            + UrlUtils.encode(textArea.getText()));
        }
    }

    private class CopyToClipboardAction extends AbstractAction {
        CopyToClipboardAction() {
            putValue(Action.NAME, I18n.tr("Copy Text"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy entire message to Clipboard"));
            putValue(LimeAction.ICON_NAME, "COPY_PASTE");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.setClipboardContent(textArea.getText());
            setTitle(I18n.tr("Message copied to clipboard."));
            JButton source = (JButton) e.getSource();
            showFeedback(getTitle(), source.getLocationOnScreen().getX(), source.getLocationOnScreen().getY());
        }
    }

    class CopyLinkAction extends AbstractAction {
        CopyLinkAction() {
            putValue(Action.NAME, I18n.tr("Copy Link"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy Link to Clipboard"));
            putValue(LimeAction.ICON_NAME, "LINK");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.setClipboardContent(link);
            setTitle(I18n.tr("Link copied to clipboard."));
            JButton source = (JButton) e.getSource();
            showFeedback(getTitle(), source.getLocationOnScreen().getX(), source.getLocationOnScreen().getY());
        }
    }

    private class CopyMagnetAction extends AbstractAction {
        CopyMagnetAction() {
            putValue(Action.NAME, I18n.tr("Copy Magnet"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Copy Magnet URL to Clipboard"));
            putValue(LimeAction.ICON_NAME, "MAGNET");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.setClipboardContent(TorrentUtil.getMagnet(info_hash) + BTEngine.getInstance().magnetPeers());
            setTitle(I18n.tr("Magnet copied to clipboard."));
            JButton source = (JButton) e.getSource();
            showFeedback(getTitle(), source.getLocationOnScreen().getX(), source.getLocationOnScreen().getY());
        }
    }
}
