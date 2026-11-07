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
import com.limegroup.gnutella.gui.ResourceManager;
import java.util.List;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;

/**
 * This class displays a setup window that recommends users to follow FrostWire on different social
 * networks.
 *
 * @author gubatron
 * @author aldenml
 */
public class SocialRecommendationsWindow extends SetupWindow {
  SocialRecommendationsWindow(SetupManager manager) {
    super(
        manager,
        I18n.tr("Join us."),
        I18n.tr(
            "Join the FrostWire community and help us spread FrostWire to continue to have a free and uncensored Internet. Stay in touch through social media channels for quick feedback, support, ideas or just to say hello."));
  }

  private static final String CHECK_ICON = "SOCIAL_MEDIA_CHECK";
  private static final String TWITTER_ICON = "SOCIAL_WIZARD_TWITTER";
  private static final String FACEBOOK_ICON = "SOCIAL_WIZARD_FACEBOOK";
  private static final String REDDIT_ICON = "SOCIAL_WIZARD_REDDIT";

  /** The button icons this page shows. */
  public static final List<String> ICONS =
      List.of(CHECK_ICON, TWITTER_ICON, FACEBOOK_ICON, REDDIT_ICON);

  /**
   * Reads and decodes this page's icons from the jar into the theme-image cache. Building the page
   * on the EDT did that there (Icebase #2c96471ade1e, frostwire/frostwire#1491), so {@link
   * SetupManager#decidePages()} calls this on the startup thread when the page will show.
   */
  public static void preloadIcons() {
    ResourceManager.preloadButtonThemeImages(ICONS);
  }

  protected void createWindow() {
    super.createWindow();
    JPanel mainPanel = new JPanel(new MigLayout("fillx, insets 0, gap 4px", "[]"));
    mainPanel.add(new IconButton(CHECK_ICON, 150, 150), "growx, center, span, wrap");
    mainPanel.add(
        new JLabel(I18n.tr("<html><b>Keep in Touch!</b></html>")),
        "center, span, wrap, height 40px");
    int socialButtonW = 100;
    mainPanel.add(
        createSocialButton(TWITTER_ICON, GUIConstants.TWITTER_FROSTWIRE_URL, socialButtonW),
        "alignx center");
    mainPanel.add(
        createSocialButton(FACEBOOK_ICON, GUIConstants.FACEBOOK_FROSTWIRE_URL, socialButtonW),
        "alignx center");
    mainPanel.add(
        createSocialButton(REDDIT_ICON, GUIConstants.REDDIT_FROSTWIRE_URL, socialButtonW),
        "alignx center");
    setSetupComponent(mainPanel);
  }

  private IconButton createSocialButton(String iconName, final String clickURL, int height) {
    IconButton button = new IconButton(iconName, height, height);
    button.addActionListener(e -> GUIMediator.openURL(clickURL));
    return button;
  }
}
