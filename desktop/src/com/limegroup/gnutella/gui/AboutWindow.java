package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.service.ErrorService;
import org.limewire.util.OSUtils;
import org.limewire.util.VersionUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Contains the <tt>JDialog</tt> instance that shows "about" information for the
 * application.
 */
// 2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
final class AboutWindow {
	/**
	 * Constant handle to the <tt>JDialog</tt> that contains about information.
	 */
	private final JDialog DIALOG;

	/**
	 * Constant for the scolling pane of credits.
	 */
	private final ScrollingTextPane SCROLLING_PANE;

	private final JLabel EULA_LABEL;

	private final JLabel PRIVACY_POLICY_LABEL;

	/**
	 * Constructs the elements of the about window.
	 */
	AboutWindow() {
		DIALOG = new JDialog(GUIMediator.getAppFrame());

		if (!OSUtils.isMacOSX())
			DIALOG.setModal(true);

		DIALOG.setSize(new Dimension(600, 400));
		DIALOG.setResizable(false);
		DIALOG.setTitle(I18n.tr("About FrostWire"));
		DIALOG.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		DIALOG.addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent we) {
				SCROLLING_PANE.stopScroll();
			}

			public void windowClosing(WindowEvent we) {
				SCROLLING_PANE.stopScroll();
			}
		});

		// set up scrolling pane
		SCROLLING_PANE = createScrollingPane();
		SCROLLING_PANE.addHyperlinkListener(GUIUtils.getHyperlinkListener());

		// set up limewire version label
		JLabel client = new JLabel("FrostWire" + " "
				+ FrostWireUtils.getFrostWireVersion() + " (build " + FrostWireUtils.getBuildNumber() + ")");
		client.setHorizontalAlignment(SwingConstants.CENTER);

		client.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				super.mouseClicked(e);
				ErrorService.error(new Throwable(""),"Hi there you curious friend. Send us feedback!");
			}
		});

		// set up java version label
		JLabel java = new JLabel("Java " + VersionUtils.getJavaVersion());
		java.setHorizontalAlignment(SwingConstants.CENTER);

		// set up frostwire.com label
		JLabel url = new URLLabel("http://www.frostwire.com");
		url.setHorizontalAlignment(SwingConstants.CENTER);


		EULA_LABEL = new URLLabel("http://www.frostwire.com/eula",I18n.tr("End User License Agreement"));
		PRIVACY_POLICY_LABEL = new URLLabel("http://www.frostwire.com/privacy",I18n.tr("Privacy Policy"));

		// set up close button
		JButton button = new JButton(I18n.tr("Close"));
		DIALOG.getRootPane().setDefaultButton(button);
		button.setToolTipText(I18n.tr("Close This Window"));
		button.addActionListener(GUIUtils.getDisposeAction());

		// layout window
		JComponent pane = (JComponent) DIALOG.getContentPane();
		GUIUtils.addHideAction(pane);

		pane.setLayout(new GridBagLayout());
		pane.setBorder(BorderFactory.createEmptyBorder(GUIConstants.SEPARATOR,
				GUIConstants.SEPARATOR, GUIConstants.SEPARATOR,
				GUIConstants.SEPARATOR));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.gridwidth = 2;
		gbc.gridy = 0;

		LogoPanel logo = new LogoPanel();
		pane.add(logo, gbc);

		gbc.gridy = 1;
		pane.add(Box.createVerticalStrut(GUIConstants.SEPARATOR), gbc);

		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridy = 2;
		pane.add(client, gbc);

		gbc.gridy = 3;
		pane.add(java, gbc);

		gbc.gridy = 4;
		pane.add(url, gbc);

		gbc.gridy = 5;
		pane.add(Box.createVerticalStrut(GUIConstants.SEPARATOR), gbc);

		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridy = 6;
		pane.add(SCROLLING_PANE, gbc);

		gbc.gridy = 7;
		gbc.weighty = 0;
		gbc.fill = GridBagConstraints.NONE;
		pane.add(Box.createVerticalStrut(GUIConstants.SEPARATOR), gbc);

		JPanel legalLinksPanel = new JPanel();
		legalLinksPanel.setPreferredSize(new Dimension(300,27));
		legalLinksPanel.setMinimumSize(new Dimension(300,27));
		legalLinksPanel.add(EULA_LABEL,BorderLayout.LINE_START);
		legalLinksPanel.add(PRIVACY_POLICY_LABEL,BorderLayout.LINE_END);

		gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridwidth = 1;
		gbc.gridy = 8;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		pane.add(legalLinksPanel, gbc);

		gbc = new GridBagConstraints();
		gbc.gridy = 8;
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		gbc.anchor = GridBagConstraints.EAST;
		pane.add(button, gbc);

	}

	private void appendListOfNames(String commaSepNames, StringBuilder sb) {
		for (String name : commaSepNames.split(","))
			sb.append("<li>"+name+"</li>");
	}

	private ScrollingTextPane createScrollingPane() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");

        Color color = new JLabel().getForeground();
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        String hex = toHex(r) + toHex(g) + toHex(b);
        sb.append("<body text='#" + hex + "'>");

        //  introduction
        sb.append("<h1>" + I18n.tr("FrostWire Logo Designer") + "</h1>");
        sb.append("<ul><li>Luis Ramirez (Venezuela - <a href='http://www.elblogo.com'>ElBlogo.com</a>)</li></ul>");

        sb.append("<h1>" + I18n.tr("FrostWire Graphics Designers/Photographers") + "</h1>");
        sb.append("<ul>");
        sb.append("<li>Kirill Grouchnikov - Substance library <a href='http://www.pushing-pixels.org/'>Pushing-Pixels.org</a></li>");
        sb.append("<li>Arianys Wilson - Splash 4.18 (New York - <a href='http://nanynany.com/blog/?from=frostwire'>NanyNany.com</a>)</li>");
        sb.append("<li>Scott Kellum - Splash 4.17 (New York - <a href='http://www.scottkellum.net'>ScottKellum.net</a>)</li>");
        sb.append("<li>Shelby Allen - Splash 4.13 (New Zealand - <a href='http://www.stitzu.com'>Stitzu.com</a>)</li>");
        sb.append("<li>Cecko Hanssen - <a href='http://www.flickr.com/photos/cecko/95013472/'>Frozen Brothers</a> CC Photograph for 4.17 Splash (Tilburg, Netherlands)</li>");
        sb.append("<li>Marcelina Knitter - <a href='https://twitter.com/#!/marcelinkaaa'>@Marcelinkaaa</a></li>");
        sb.append("</ul>");

        sb.append("<h1>" + I18n.tr("Thanks to Former FrostWire Developers") + "</h1>");
        sb.append("<li>Gregorio Roper (Germany)</li>");
        sb.append("<li>Fernando Toussaint '<strong>FTA</strong>' - <a href='http://www.cybercultura.net'>Web</a></li>");
        sb.append("<br><br>");

        sb.append("<h1>" + I18n.tr("Thanks to the FrostWire Chat Community!") + "</h1>");
        sb.append(I18n.tr("Thanks to everybody that has helped us everyday in the forums and chatrooms, " +
        		"you not only help new users but you also warn the FrostWire team of any problem that " +
                "occur on our networks. Thank you all, without you this wouldn't be possible!"));
        sb.append(I18n.tr("<br><br>In Special we give thanks to the Chatroom Operators and Forum Moderators"));
        sb.append("<ul>");

        sb.append("<h1>" + I18n.tr("FrostWire Chat Operators") + "</h1>");
        String chat_operators = "Aubrey,Casper,COOTMASTER,Emily,Gummo,Hobo,Humanoid,iDan,lexie,OfficerSparker,Scott1x,THX1138,WolfWalker,Wyrdjax,Daemon,Trinity";
        appendListOfNames(chat_operators, sb);
        sb.append("</ul>");

        sb.append("<h1>" + I18n.tr("FrostWire Forum Moderators") + "</h1>");
        String forum_moderators="Aaron.Walkhouse,Calliope,cootmaster,Efrain,et voil&agrave;,nonprofessional,Only A Hobo,spuggy,stief,The_Fox";
        sb.append("<ul>");
        appendListOfNames(forum_moderators,sb);
        sb.append("</ul>");

        sb.append("<h1>" + I18n.tr("Many Former Chat Operators") + "</h1>");
        String former_operators="AlleyCat,Coelacanth,Gollum,Jewels,Jordan,Kaapeli,Malachi,Maya,Sabladowah,Sweet_Songbird,UB4T,jwb,luna_moon,nonproffessional,sug,the-jack,yummy-brummy";
        sb.append("<ul>");
        appendListOfNames(former_operators, sb);
        sb.append("</ul>");

        sb.append(I18n.tr("And also to the Support Volunteer Helpers:"));
        sb.append("<ul>");
        appendListOfNames("dutchboy,Lelu,udsteve",sb);
        sb.append("</ul>");

        // azureus/vuze devs.
        sb.append("<h1>" + I18n.tr("Thanks to the Azureus Core Developers") + "</h1>");
        String az_devs = "Olivier Chalouhi (gudy),Alon Rohter (nolar),Paul Gardner (parg),ArronM (TuxPaper),Paul Duran (fatal_2),Jonathan Ledlie(ledlie),Allan Crooks (amc1),Xyrio (muxumx),Michael Parker (shadowmatter),Aaron Grunthal (the8472)";
        sb.append("<ul>");
        appendListOfNames(az_devs, sb);
        sb.append("</ul>");

        //  developers
        sb.append("<h1>" + I18n.tr("Thanks to the LimeWire Developer Team") + "</h1>");
        sb.append("<ul>\n" +
        		"  <li>Greg Bildson</li>\n" +
        		"  <li>Sam Berlin</li>\n" +
        		"  <li>Zlatin Balevsky</li>\n" +
        		"  <li>Felix Berger</li>\n" +
        		"  <li>Mike Everett</li>\n" +
        		"  <li>Kevin Faaborg</li>\n" +
        		"  <li>Jay Jeyaratnam</li>\n" +
        		"  <li>Curtis Jones</li>\n" +
        		"  <li>Tim Julien</li>\n" +
        		"  <li>Akshay Kumar</li>\n" +
        		"  <li>Jeff Palm</li>\n" +
        		"  <li>Mike Sorvillo</li>\n" +
        		"  <li>Dan Sullivan</li>\n" +
        "</ul>");

        //  community VIPs
        sb.append(I18n.tr("Several colleagues in the Gnutella community merit special thanks. These include:"));
        sb.append("<ul>\n" +
        		"  <li>Vincent Falco -- Free Peers, Inc.</li>\n" +
        		"  <li>Gordon Mohr -- Bitzi, Inc.</li>\n" +
        		"  <li>John Marshall -- Gnucleus</li>\n" +
        		"  <li>Jason Thomas -- Swapper</li>\n" +
        		"  <li>Brander Lien -- ToadNode</li>\n" +
        		"  <li>Angelo Sotira -- www.gnutella.com</li>\n" +
        		"  <li>Marc Molinaro -- www.gnutelliums.com</li>\n" +
        		"  <li>Simon Bellwood -- www.gnutella.co.uk</li>\n" +
        		"  <li>Serguei Osokine</li>\n" +
        		"  <li>Justin Chapweske</li>\n" +
        		"  <li>Mike Green</li>\n" +
        		"  <li>Raphael Manfredi</li>\n" +
        		"  <li>Tor Klingberg</li>\n" +
        		"  <li>Mickael Prinkey</li>\n" +
        		"  <li>Sean Ediger</li>\n" +
        		"  <li>Kath Whittle</li>\n" +
        "</ul>");

        sb.append("<h1>" + I18n.tr("Thanks to the PJIRC Staff") + "</h1>");
        sb.append("<ul>");
        sb.append("<li>Plouf</li>");
        sb.append("<li>Jiquera</li>");
        sb.append("<li>Ezequiel</li>");
        sb.append("<li>Superchatbar.nl</li>");
        sb.append("<li>Thema</li>");
        sb.append("</ul>");

        sb.append("<h1>" + I18n.tr("Thanks to the Automatix Team") + "</h1>");
        sb.append("<p>" + I18n.tr("For helping distribute Frostwire to opensource communities in a very simple manner.") + "</p>");
        sb.append("<ul>");
        sb.append("<li>Arnieboy</li>");
        sb.append("<li>JimmyJazz</li>");
        sb.append("<li>Mstlyevil</li>");
        sb.append("<li>WildTangent</li>");
        sb.append("</ul>");

        sb.append("<h1>" + I18n.tr("Thanks to Ubuntu/Kubuntu Teams") + "</h1>");
        sb.append("<p>" + I18n.tr("For making the world a better place with such an excellent distro, you'll be the ones to make a difference on the desktop.")+"</p>");

        sb.append("<h1>" + I18n.tr("Thanks to the NSIS Project") + "</h1>");
        sb.append("<p>" + I18n.tr("Thanks for such an awesome installer builder system and documentation.") + "</p>");

        sb.append("<h1>" + I18n.tr("Thanks to our families") + "</h1>");
        sb.append(I18n.tr("For being patient during our many sleepless nights"));

        // bt notice
        sb.append("<small>");
        sb.append("<br><br>");
        sb.append(I18n.tr("BitTorrent, the BitTorrent Logo, and Torrent are trademarks of BitTorrent, Inc."));
        sb.append("</small>");

        sb.append("</body></html>");

        return new ScrollingTextPane(sb.toString());
    }

	/**
	 * Returns the int as a hex string.
	 */
	private String toHex(int i) {
		String hex = Integer.toHexString(i).toUpperCase();
		if (hex.length() == 1)
			return "0" + hex;
		else
			return hex;
	}

	/**
	 * Displays the "About" dialog window to the user.
	 */
	void showDialog() {
		GUIUtils.centerOnScreen(DIALOG);
		DIALOG.setVisible(true);
	}
}
