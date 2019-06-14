/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.menu;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.TipOfTheDayMediator;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.OpenLinkAction;
import com.limegroup.gnutella.settings.UISettings;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Handles all of the contents of the help menu in the menu bar.  This
 * includes such items as the link to the "Using LimeWire" page of the
 * web site as well as links to the forum, faq, "tell a friend", etc.
 */
final class HelpMenu extends AbstractMenu {
    private final ShowSendFeedbackDialogAction showSendFeedbackDialogAction;

    /**
     * Creates a new <tt>HelpMenu</tt>, using the <tt>key</tt>
     * argument for setting the locale-specific title and
     * accessibility text.
     */
    HelpMenu() {
        super(I18n.tr("&Help"));
        addMenuItem(new OpenLinkAction("http://www.frostwire-preview.com/?type=yt&displayName=How%20to%20search%20and%20download%20files%20with%20FrostWire%20Wynwood%20(5.6)%20-%20YouTube&source=YouTube+-+FrostWireVideos&detailsUrl=http://www.youtube.com/watch?v=A0p_DpOjpO8", I18n.tr("How to use FrostWire (Video)"), I18n.tr("How to use FrostWire (Video)")));
        addMenuItem(new OpenLinkAction("http://www.frostwire.com/android", I18n.tr("FrostWire for Android"), I18n.tr("Get FrostWire on your Android phone, tablet or google tv, all free.")));
        addMenuItem(new OpenLinkAction("https://www.quora.com/What-is-seeding-on-FrostWire", I18n.tr("What is \"Seeding\"?"), I18n.tr("Learn about BitTorrent Seeding")));
        addMenuItem(new OpenLinkAction("http://www.frostwire.com/vpn", I18n.tr("What is a VPN?"), I18n.tr("Learn about how to protect your internet connection and your privacy online")));
        addSeparator();
        addMenuItem(new OpenLinkAction("http://www.frostwire.com/give?from=desktop-help", I18n.tr("Support FrostWire"), I18n.tr("Support FrostWire")));
        JMenu cryptoCurrenciesMenu = new JMenu(I18n.tr("Support with CryptoCurrencies"));
        cryptoCurrenciesMenu.add(new OpenLinkAction("http://www.frostwire.com/bitcoin", I18n.tr("Bitcoin")));
        cryptoCurrenciesMenu.add(new OpenLinkAction("http://www.frostwire.com/bitcoin-cash", I18n.tr("Bitcoin Cash")));
        cryptoCurrenciesMenu.add(new OpenLinkAction("http://www.frostwire.com/ethereum", I18n.tr("Ethereum")));
        cryptoCurrenciesMenu.add(new OpenLinkAction("http://www.frostwire.com/dash", I18n.tr("Dash")));
        cryptoCurrenciesMenu.add(new OpenLinkAction("http://www.frostwire.com/litecoin", I18n.tr("LiteCoin")));
        cryptoCurrenciesMenu.add(new OpenLinkAction("http://www.frostwire.com/zcash", I18n.tr("ZCash")));
        getMenu().add(cryptoCurrenciesMenu);
        addMenuItem(new OpenLinkAction("http://www.frostclick.com/wp/?from=frostwire" + com.limegroup.gnutella.util.FrostWireUtils.getFrostWireVersion(),
                "FrostClick.com", I18n.tr("Free Legal Downloads")));
        addSeparator();
        addMenuItem(new OpenLinkAction("http://www.facebook.com/pages/FrostWire/110265295669948", I18n.tr("Follow us on Facebook"), I18n.tr("Come and say hi to the community on Facebook")));
        addMenuItem(new OpenLinkAction("https://instagram.com/frostwire", I18n.tr("Follow us on Instagram"), I18n.tr("Follow us on Instagram")));
        addMenuItem(new OpenLinkAction("http://twitter.com/#!/frostwire", I18n.tr("Follow us on Twitter"), I18n.tr("Follow us on Twitter")));
        addMenuItem(new OpenLinkAction("http://forum.frostwire.com/?from=desktop-help", I18n.tr("Foru&m"), I18n.tr("Access the FrostWire Users\' Forum")));
        addSeparator();
        addMenuItem(new OpenLinkAction("http://frostwire.wordpress.com/2012/02/14/dont-get-scammed-frostwire-is-free/", I18n.tr("&Did you pay for FrostWire?"),
                I18n.tr("Did you pay for FrostWire? FrostWire is Free as in Free Beer. Avoid Scams.")));
        addMenuItem(new ShowTipOfTheDayAction());
        if (!OSUtils.isMacOSX()) {
            addSeparator();
            addMenuItem(new ShowAboutDialogAction());
        }
        showSendFeedbackDialogAction = new ShowSendFeedbackDialogAction();
        addMenuItem(showSendFeedbackDialogAction);
    }

    @Override
    protected void refresh() {
        showSendFeedbackDialogAction.refresh();
    }

    /**
     * Displays the TOTD window.
     */
    private static class ShowTipOfTheDayAction extends AbstractAction {
        /**
         *
         */
        private static final long serialVersionUID = -4964160055694967725L;

        ShowTipOfTheDayAction() {
            super(I18n.tr("Tip of the &Day"));
            putValue(LONG_DESCRIPTION, I18n.tr("Show the Tip of the Day Window"));
        }

        public void actionPerformed(ActionEvent e) {
            TipOfTheDayMediator.instance().displayTipWindow();
        }
    }

    /**
     * Shows the about window with more information about the application.
     */
    private static class ShowAboutDialogAction extends AbstractAction {
        /**
         *
         */
        private static final long serialVersionUID = 2425666944873627828L;

        ShowAboutDialogAction() {
            super(I18n.tr("&About FrostWire"));
            putValue(LONG_DESCRIPTION, I18n.tr("Information about FrostWire"));
        }

        public void actionPerformed(ActionEvent e) {
            GUIMediator.showAboutWindow();
        }
    }

    private static class ShowSendFeedbackDialogAction extends AbstractAction {
        private final String SEND_FEEDBACK_STRING = I18n.tr("Send Feedback");

        ShowSendFeedbackDialogAction() {
            super(I18n.tr("Send Feedback"));
            putValue(LONG_DESCRIPTION, I18n.tr("Show Send Feedback Window"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.showSendFeedbackDialog();
        }

        void refresh() {
            long lastFeedbackSentTimestamp = UISettings.LAST_FEEDBACK_SENT_TIMESTAMP.getValue();
            if (lastFeedbackSentTimestamp == 0) {
                return;
            }
            long elapsedTime = System.currentTimeMillis() - lastFeedbackSentTimestamp;
            int FIVE_MINUTES_IN_MILLISECONDS = 5 * 60 * 1000;
            setEnabled(elapsedTime > FIVE_MINUTES_IN_MILLISECONDS);
            if (!isEnabled()) {
                String timeToWait;
                long timeLeft = FIVE_MINUTES_IN_MILLISECONDS - elapsedTime;
                if (timeLeft < 60000) {
                    int seconds = (int) timeLeft / 1000;
                    timeToWait = seconds + " " + ((seconds > 1) ? I18n.tr("seconds") : I18n.tr("second"));
                } else {
                    int minutes = (int) timeLeft / 60000;
                    timeToWait = minutes + " " + ((minutes > 1) ? I18n.tr("minutes") : I18n.tr("minute"));
                }
                putValue(NAME, SEND_FEEDBACK_STRING + " (" + I18n.tr("Please wait") + " " + timeToWait + ")");
            } else {
                putValue(NAME, SEND_FEEDBACK_STRING);
            }
        }
    }
}
