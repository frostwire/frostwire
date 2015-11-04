/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpClientListenerAdapter;
import com.limegroup.gnutella.gui.ButtonRow;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.LimeAction;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.limewire.concurrent.ThreadExecutor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class ShareTorrentDialog extends JDialog {

	private final class URLShortnerHttpClientListener extends
			HttpClientListenerAdapter {

		private final String _shortenerUri;

		public URLShortnerHttpClientListener(String uri) {
		    _shortenerUri = uri;
		}

		@Override
		public void onError(HttpClient client, Throwable e) {
			if (_shortnerListeners.size() > 1) {
				_shortnerListeners.remove(0);
				performAsyncURLShortening(_shortnerListeners.get(0));

				System.out.println(">>> URLShortnerHttpClientListener ERROR >>>");
				e.printStackTrace();
				System.out.println();
				System.out.println("Try shortening with URL: [" + _shortenerUri + "]");
				System.out.println(">>> URLShortnerHttpClientListener ERROR >>>");
			}
		}

		public String getShortenerURL() {
			return _shortenerUri;
		}
	}

	private TorrentInfo _torrent;
	private Container _container;
	private JLabel _introLabel;
	private JEditorPane _textArea;
	private JLabel _tipsLabel;
	private Action[] _actions;

	private JLabel _feedbackLabel;

	private URLShortnerHttpClientListener _bitlyShortnerListener;
	private URLShortnerHttpClientListener _tinyurlShortnerListener;

	/**
	 * Add more URL Shortner listeners to this list on
	 * initURLShortnerListeners() to have more URL Shortening services to fall
	 * back on. They will be executed in the order they were added.
	 */
	private List<URLShortnerHttpClientListener> _shortnerListeners;

	private String _link;
	private String _info_hash;
	private String _torrent_name;

	public ShareTorrentDialog(TorrentInfo torrent) {
		_torrent = torrent;
		setupUI();
	}

	private void initURLShortnerListeners() {
		_bitlyShortnerListener = new URLShortnerHttpClientListener(
				"http://api.bit.ly/v3/shorten?format=txt&login=frostwire&apiKey=R_749968a37da3260493d8aa19ee021d14&longUrl="
						+ UrlUtils.encode(getLink()));

		_tinyurlShortnerListener = new URLShortnerHttpClientListener(
				"http://tinyurl.com/api-create.php?url=" + getLink());

		_shortnerListeners = new LinkedList<ShareTorrentDialog.URLShortnerHttpClientListener>(Arrays.asList(
				_bitlyShortnerListener,_tinyurlShortnerListener));
	}

	private void updateTextArea() {
		GUIMediator.safeInvokeLater(new Runnable() {
			@Override
			public void run() {
				_textArea.setText(I18n.tr("Download") + " \"" + _torrent_name.replace("_", " ") + "\" " + I18n.tr("at") + " "
						+ getLink().trim() + " "+ I18n.tr("via FrostWire"));
			}
		});
	}


	private void setupUI() {
		setupWindow();

		initTorrentname();

		initInfoHash();

		GridBagConstraints c = new GridBagConstraints();

		// INTRO LABEL
		c.anchor = GridBagConstraints.LINE_START;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.insets = new Insets(20, 10, 10, 10);
		boolean folderTorrent = _torrent.getNumFiles() > 1;
		_introLabel = new JLabel(folderTorrent ? String.format(
				I18n.tr("Use the following text to share the \"%s\" folder"),
				_torrent_name) : String.format(I18n.tr(
				"Use the following text to share the \"%s\" file"),
				_torrent_name));
		_introLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
		_container.add(_introLabel, c);

		// TEXT AREA
		c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 0.7;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.insets = new Insets(1, 10, 1, 10);

		_textArea = new JEditorPane();
		_textArea.setEditable(false);
		updateTextArea();

		Font f = new Font("Dialog", Font.BOLD, 13);
		_textArea.setFont(f);
		_textArea.setMargin(new Insets(10, 10, 10, 10));
		_textArea.selectAll();
		_textArea.setBorder(BorderFactory.createEtchedBorder());
		_container.add(_textArea, c);

		initURLShortnerListeners();
		performAsyncURLShortening(_shortnerListeners.get(0));

		// BUTTON ROW
		initActions();

		c = new GridBagConstraints();
		c.anchor = GridBagConstraints.CENTER;
		c.gridwidth = GridBagConstraints.REMAINDER;
		//c.weightx = 1.0;
		c.insets = new Insets(10, 10, 10, 10);
		ButtonRow buttonRow = new ButtonRow(_actions, ButtonRow.X_AXIS,
				ButtonRow.RIGHT_GLUE);

		fixButtonBorders(buttonRow);
		ToolTipManager.sharedInstance().setInitialDelay(200);

		_container.add(buttonRow, c);

		// TIPS LABEL
		c = new GridBagConstraints();
		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1.0;
		c.insets = new Insets(10, 10, 10, 10);
		_tipsLabel = new JLabel("<html><p> > "+
				I18n.tr("<strong>Keep FrostWire Open</strong> until the file has been downloaded by at least one other friend.")+"</p><p>&nbsp;</p><p> >"+
				I18n.tr("<strong>The more, the merrier.</strong> The more people sharing the faster it can be downloaded by others.")+"</p><p>&nbsp;</p><p> >"+
				I18n.tr("<strong>Your files can be discovered by others.</strong> Once you share this link and you seed the files they will be available to everybody on the BitTorrent network.")+"</p></html>");
		_tipsLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
		_tipsLabel.setBorder(BorderFactory.createTitledBorder(I18n.tr("Tips")));
		_container.add(_tipsLabel, c);

		// FEEDBACK LABEL
		JPanel glass = (JPanel) getGlassPane();
		glass.setLayout(null);
		glass.setVisible(true);
		_feedbackLabel=new JLabel(I18n.tr("Feedback here to clipboard"));
		_feedbackLabel.setVisible(false);
		_feedbackLabel.setFont(new Font("Arial",Font.BOLD,14));
		glass.add(_feedbackLabel);

		_feedbackLabel.setBounds(100,100,300,20);

		GUIUtils.addHideAction((JComponent) getContentPane());
		pack();
	}

	private void fixButtonBorders(ButtonRow buttonRow) {
		if (_actions == null) {
			return;
		}

		for (int i = 0; i < _actions.length; i++) {
			JButton buttonAtIndex = buttonRow.getButtonAtIndex(i);
			buttonAtIndex.setBorderPainted(true);
		}
	}

	private void initTorrentname() {
		_torrent_name = _torrent.getName();
	}

	private void initInfoHash() {
		_info_hash = _torrent.getInfoHash().toString();
	}

	private String getLink() {
		if (_link == null) {
			_link = "http://maglnk.com/" + _info_hash + "/?" + TorrentUtil.getMagnetURLParameters(_torrent);
		}
		return _link;
	}

    private void performAsyncURLShortening(final URLShortnerHttpClientListener listener) {
        final HttpClient browser = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
        browser.setListener(listener);
        ThreadExecutor.startThread(new Runnable() {
            @Override
            public void run() {
                try {
                    _link = browser.get(listener.getShortenerURL(), 2000);
                    updateTextArea(); //happens safely on UI thread.
                } catch (Throwable t) {
                }
            }

        }, "ShareTorrentDialog-performAsyncURLShortening");
    }

	private void initActions() {
		_actions = new Action[4];

		_actions[0] = new TwitterAction();
		_actions[1] = new CopyToClipboardAction();
		_actions[2] = new CopyLinkAction();
		_actions[3] = new CopyMagnetAction();

	}

	private void setupWindow() {
		setTitle(I18n.tr("All done! Now share the link"));

		Dimension prefDimension = new Dimension(540, 380);

		setSize(prefDimension);
		setMinimumSize(prefDimension);
		setPreferredSize(prefDimension);
		setResizable(false);
		setLocationRelativeTo(null);

		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.APPLICATION_MODAL);
		GUIUtils.addHideAction((JComponent) getContentPane());

		_container = getContentPane();
		_container.setLayout(new GridBagLayout());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				ToolTipManager.sharedInstance().setInitialDelay(500);
			}
		});
	}

	/**
	 * Animates the _feedbackLabel (which lives on the Glass Pane invisibly and is shown during the animation)
	 *
	 * To animate this component we wrap the JLabel on a TimelineJLabel.
	 * If we ever need to animate more labels, we'll probably just extend JLabel in the future.
	 *
	 * The animation interpolates 2 properties. The color of the text, and the y position of the text.
	 *
	 * It looks like it dissapears by changing the color into the background color.
	 *
	 * @param title
	 * @param x
	 * @param y
	 */
	public void showFeedback(String title, double x, double y) {
		int startY = (int) (y-getLocationOnScreen().getY()-40);

		_feedbackLabel.setLocation((int) (x-getLocationOnScreen().getX()), startY);
		_feedbackLabel.setVisible(true);
		_feedbackLabel.setText(title);
	}

	private class TwitterAction extends AbstractAction {

		private static final long serialVersionUID = -2035234758115291468L;

		public TwitterAction() {
			putValue(Action.NAME, I18n.tr("Twitter it"));
			putValue(Action.SHORT_DESCRIPTION,I18n.tr("Send the message above to Twitter"));
			putValue(LimeAction.ICON_NAME,"TWITTER");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			GUIMediator
					.openURL("http://twitter.com/intent/tweet?source=FrostWire&text="
							+ UrlUtils.encode(_textArea.getText()));
		}
	}

	private class CopyToClipboardAction extends AbstractAction {

		private static final long serialVersionUID = 2130811125951128397L;

		public CopyToClipboardAction() {
			putValue(Action.NAME, I18n.tr("Copy Text"));
			putValue(Action.SHORT_DESCRIPTION,I18n.tr("Copy entire message to Clipboard"));
			putValue(LimeAction.ICON_NAME,"COPY_PASTE");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			GUIMediator.setClipboardContent(_textArea.getText());
			setTitle(I18n.tr("Message copied to clipboard."));

			JButton source = (JButton) e.getSource();
			showFeedback(getTitle(),source.getLocationOnScreen().getX(),source.getLocationOnScreen().getY());
		}
	}

	public class CopyLinkAction extends AbstractAction {

		private static final long serialVersionUID = 5396173442291772242L;

		public CopyLinkAction() {
			putValue(Action.NAME, I18n.tr("Copy Link"));
			putValue(Action.SHORT_DESCRIPTION,I18n.tr("Copy Link to Clipboard"));
			putValue(LimeAction.ICON_NAME,"LINK");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			GUIMediator.setClipboardContent(_link);
			setTitle(I18n.tr("Link copied to clipboard."));

			JButton source = (JButton) e.getSource();
			showFeedback(getTitle(),source.getLocationOnScreen().getX(),source.getLocationOnScreen().getY());

		}

	}

	private class CopyMagnetAction extends AbstractAction {

		private static final long serialVersionUID = 4972170728829407730L;

		public CopyMagnetAction() {
			putValue(Action.NAME, I18n.tr("Copy Magnet"));
			putValue(Action.SHORT_DESCRIPTION,I18n.tr("Copy Magnet URL to Clipboard"));
			putValue(LimeAction.ICON_NAME,"MAGNET");
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			GUIMediator.setClipboardContent(TorrentUtil.getMagnet(_info_hash) + "&" + TorrentUtil.getMagnetURLParameters(_torrent));
			setTitle(I18n.tr("Magnet copied to clipboard."));

			JButton source = (JButton) e.getSource();
			showFeedback(getTitle(),source.getLocationOnScreen().getX(),source.getLocationOnScreen().getY());
		}
	}


}
