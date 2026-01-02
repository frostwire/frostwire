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

package com.limegroup.gnutella.gui.init;

import com.limegroup.gnutella.gui.GUIConstants;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconButton;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;

/**
 * This class displays a setup window that recommends users to
 * follow FrostWire on different social networks.
 *
 * @author gubatron
 * @author aldenml
 */
class SocialRecommendationsWindow extends SetupWindow {
    SocialRecommendationsWindow(SetupManager manager) {
        super(manager, I18n.tr("Join us."),
                I18n.tr("Join the FrostWire community and help us spread FrostWire to continue to have a free and uncensored Internet. Stay in touch through social media channels for quick feedback, support, ideas or just to say hello."));
    }

    protected void createWindow() {
        super.createWindow();
        JPanel mainPanel = new JPanel(new MigLayout("fillx, insets 0, gap 4px",
                "[]"));
        mainPanel.add(new IconButton("SOCIAL_MEDIA_CHECK", 150, 150), "growx, center, span, wrap");
        mainPanel.add(new JLabel(I18n.tr("<html><b>Keep in Touch!</b></html>")), "center, span, wrap, height 40px");
        int socialButtonW = 100;
        mainPanel.add(createSocialButton("SOCIAL_WIZARD_TWITTER", GUIConstants.TWITTER_FROSTWIRE_URL, socialButtonW), "alignx center");
        mainPanel.add(createSocialButton("SOCIAL_WIZARD_FACEBOOK", GUIConstants.FACEBOOK_FROSTWIRE_URL, socialButtonW), "alignx center");
        mainPanel.add(createSocialButton("SOCIAL_WIZARD_REDDIT", GUIConstants.REDDIT_FROSTWIRE_URL, socialButtonW), "alignx center");
        setSetupComponent(mainPanel);
    }

    private IconButton createSocialButton(String iconName, final String clickURL, int height) {
        IconButton button = new IconButton(iconName, height, height);
        button.addActionListener(e -> GUIMediator.openURL(clickURL));
        return button;
    }
}
