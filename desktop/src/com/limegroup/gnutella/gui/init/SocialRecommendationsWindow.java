/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.

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

package com.limegroup.gnutella.gui.init;

import com.frostwire.gui.bittorrent.TorrentSaveFolderComponent;
import com.frostwire.gui.bittorrent.TorrentSeedingSettingComponent;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconButton;
import com.limegroup.gnutella.gui.ImageManipulator;
import com.limegroup.gnutella.gui.util.Constants;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.SharingSettings;
import net.miginfocom.swing.MigLayout;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This class displays a setup window that recommends users to
 * follow FrostWire on different social networks.
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
        mainPanel.add(new JLabel(I18n.tr("<html><b>Keep in Touch!</b></html>")),"center, span, wrap, height 40px");
        int socialButtonW = 100;
        mainPanel.add(createSocialButton("SOCIAL_WIZARD_FACEBOOK", Constants.FACEBOOK_FROSTWIRE_URL, socialButtonW), "alignx center");
        mainPanel.add(createSocialButton("SOCIAL_WIZARD_REDDIT"  , Constants.REDDIT_FROSTWIRE_URL  , socialButtonW), "alignx center");
        mainPanel.add(createSocialButton("SOCIAL_WIZARD_TWITTER" , Constants.TWITTER_FROSTWIRE_URL , socialButtonW), "alignx center");

        setSetupComponent(mainPanel);
    }

    private IconButton createSocialButton(String iconName, final String clickURL, int height) {
        IconButton button = new IconButton(iconName,height,height);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                GUIMediator.openURL(clickURL);
            }
        });
        return button;
    }
}
