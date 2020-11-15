/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.components.slides;

import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconButton;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import com.limegroup.gnutella.gui.actions.LimeAction;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author gubatron
 * @author aldenml
 */
final class SlideControlsOverlay extends JPanel {
    private static final Color BACKGROUND = new Color(45, 52, 58); // 2d343a
    private static final float BACKGROUND_ALPHA = 0.7f;
    private static final Color TEXT_FOREGROUND = new Color(255, 255, 255);
    private static final int BASE_TEXT_FONT_SIZE_DELTA = 3;
    private static final int TITLE_TEXT_FONT_SIZE_DELTA = BASE_TEXT_FONT_SIZE_DELTA + 3;
    private static final int SOCIAL_BAR_HEIGHT = 55;
    private final SlidePanelController controller;
    private final Composite overlayComposite;

    SlideControlsOverlay(SlidePanelController controller) {
        this.controller = controller;
        this.overlayComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BACKGROUND_ALPHA);
        setupUI();
    }

    private void setupUI() {
        setOpaque(false);
        setLayout(new MigLayout("fill")); //rows
        setBackground(BACKGROUND);
        setupTitle();
        setupPaymentOptions();
        setupButtons();
        setupSocialBar();
    }

    private void setupTitle() {
        if (controller.getSlide().title != null) {
            JLabel labelTitle = new JLabel(controller.getSlide().title);
            labelTitle.setForeground(TEXT_FOREGROUND);
            labelTitle.setFont(deriveFont(true, TITLE_TEXT_FONT_SIZE_DELTA));
            add(labelTitle, "alignx left, aligny baseline, pushx");
        }
    }

    private void setupPaymentOptions() {
        Slide slide = controller.getSlide();
        if (slide != null && slide.paymentOptions != null) {
            add(createPaymentButton(slide), "aligny center, alignx right, wrap");
        } else {
            add(new JPanel(), "wrap");
        }
    }

    private OverlayIconButton createPaymentButton(Slide slide) {
        final OverlayIconButton paymentButton = new OverlayIconButton(new PaymentAction(slide.paymentOptions, slide.title), true, false);
        Font origFont = paymentButton.getFont();
        Font newFont = origFont.deriveFont(origFont.getSize2D() - 3f);
        paymentButton.setFont(newFont);
        paymentButton.setForeground(new Color(0x5cb4e0));
        paymentButton.setBackground(Color.WHITE);
        UIDefaults nimbusOverrides = (UIDefaults) paymentButton.getClientProperty("Nimbus.Overrides");
        nimbusOverrides.put("Button.contentMargins", new Insets(5, 7, 5, 7));
        paymentButton.putClientProperty("Nimbus.Overrides", nimbusOverrides);
        paymentButton.updateUI();
        paymentButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                paymentButton.setForeground(new Color(0x243c4e));
                paymentButton.setBackground(new Color(0x5cb4e0));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                paymentButton.setForeground(new Color(0x5cb4e0));
                paymentButton.setBackground(Color.WHITE);
            }
        });
        paymentButton.setText(I18n.tr("Leave a tip"));
        return paymentButton;
    }

    private void setupButtons() {
        final Slide slide = controller.getSlide();
        JPanel centerButtonsPanel = new JPanel(new MigLayout("fill"));
        if (slide.hasFlag(Slide.SHOW_PREVIEW_BUTTONS_ON_THE_LEFT)) {
            addPreviewButtons(centerButtonsPanel, slide);
            addDownloadInstallButton(centerButtonsPanel, slide);
        } else {
            addDownloadInstallButton(centerButtonsPanel, slide);
            addPreviewButtons(centerButtonsPanel, slide);
        }
        add(centerButtonsPanel, "gaptop 35px, gapbottom 25px, growy, pushy, pushx, spanx 3, aligny center, alignx center, wrap");
    }

    private void setupSocialBar() {
        Slide slide = controller.getSlide();
        JPanel container = new JPanel(new MigLayout("fillx, ins 0"));
        JLabel labelAuthor = new JLabel(slide.author + " " + I18n.tr("on"));
        //labelAuthor.putClientProperty(SubstanceTextUtilities.ENFORCE_FG_COLOR, Boolean.TRUE);
        labelAuthor.setForeground(TEXT_FOREGROUND);
        labelAuthor.setFont(deriveFont(false, BASE_TEXT_FONT_SIZE_DELTA));
        container.add(labelAuthor, "aligny baseline");
        if (slide.facebook != null) {
            container.add(new OverlayIconButton(new SocialAction("Facebook", slide.facebook)), "");
        }
        if (slide.twitter != null) {
            container.add(new OverlayIconButton(new SocialAction("Twitter", slide.twitter)), "");
        }
        if (slide.youtube != null) {
            container.add(new OverlayIconButton(new SocialAction("YouTube", slide.youtube)), "");
        }
        if (slide.instagram != null) {
            container.add(new OverlayIconButton(new SocialAction("Instagram", slide.instagram)), "");
        }
        add(container, "span 2, alignx center, pushx");
    }

    private Font deriveFont(boolean isBold, int fontSizeDelta) {
        Font font = getFont();
        if (isBold) {
            font = font.deriveFont(Font.BOLD);
        }
        return font.deriveFont(font.getSize2D() + fontSizeDelta);
    }

    private void addPreviewButtons(JPanel container, final Slide slide) {
        if (slide.hasFlag(Slide.SHOW_VIDEO_PREVIEW_BUTTON)) {
            //add video preview button
            container.add(new OverlayIconButton(new PreviewVideoAction(controller)));
        }
        if (slide.hasFlag(Slide.SHOW_AUDIO_PREVIEW_BUTTON)) {
            //add audio preview button
            container.add(new OverlayIconButton(new PreviewAudioAction(controller)));
        }
    }

    private void addDownloadInstallButton(JPanel container, final Slide slide) {
        if (slide.method == Slide.SLIDE_DOWNLOAD_METHOD_HTTP || slide.method == Slide.SLIDE_DOWNLOAD_METHOD_TORRENT) {
            if (slide.hasFlag(Slide.POST_DOWNLOAD_EXECUTE)) {
                //add install button
                container.add(new OverlayIconButton(new InstallAction(controller)));// "cell column row width height"
            } else {
                //add download button
                container.add(new OverlayIconButton(new DownloadAction(controller)));
            }
        }
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Color background = getBackground();
        Composite c = g2d.getComposite();
        g2d.setComposite(overlayComposite);
        g2d.setColor(background);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setComposite(c);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, getHeight() - SOCIAL_BAR_HEIGHT, getWidth(), SOCIAL_BAR_HEIGHT);
        g2d.setColor(background);
        super.paint(g);
    }

    private static final class InstallAction extends AbstractAction {
        private final SlidePanelController controller;

        InstallAction(SlidePanelController controller) {
            this.controller = controller;
            putValue(Action.NAME, I18n.tr("Install"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Install"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Install") + " " + controller.getSlide().title);
            putValue(LimeAction.ICON_NAME, "SLIDE_CONTROLS_OVERLAY_DOWNLOAD");
            putValue(LimeAction.ICON_NAME_ROLLOVER, "SLIDE_CONTROLS_OVERLAY_DOWNLOAD_ROLLOVER");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            controller.installSlide();
        }
    }

    private static final class DownloadAction extends AbstractAction {
        private final SlidePanelController controller;

        DownloadAction(SlidePanelController controller) {
            this.controller = controller;
            putValue(Action.NAME, I18n.tr("Download"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Download"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Download") + " " + controller.getSlide().title);
            putValue(LimeAction.ICON_NAME, "SLIDE_CONTROLS_OVERLAY_DOWNLOAD");
            putValue(LimeAction.ICON_NAME_ROLLOVER, "SLIDE_CONTROLS_OVERLAY_DOWNLOAD_ROLLOVER");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            controller.downloadSlide();
        }
    }

    private static final class PreviewVideoAction extends AbstractAction {
        private final SlidePanelController controller;

        PreviewVideoAction(SlidePanelController controller) {
            this.controller = controller;
            putValue(Action.NAME, I18n.tr("Video Preview"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Video Preview"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Play Video preview of") + " " + controller.getSlide().title);
            putValue(LimeAction.ICON_NAME, "SLIDE_CONTROLS_OVERLAY_PREVIEW");
            putValue(LimeAction.ICON_NAME_ROLLOVER, "SLIDE_CONTROLS_OVERLAY_PREVIEW_ROLLOVER");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            controller.previewVideo();
        }
    }

    private static final class PreviewAudioAction extends AbstractAction {
        private final SlidePanelController controller;

        PreviewAudioAction(SlidePanelController controller) {
            this.controller = controller;
            putValue(Action.NAME, I18n.tr("Audio Preview"));
            putValue(LimeAction.SHORT_NAME, I18n.tr("Audio Preview"));
            putValue(Action.SHORT_DESCRIPTION, I18n.tr("Play Audio preview of") + " " + controller.getSlide().title);
            putValue(LimeAction.ICON_NAME, "SLIDE_CONTROLS_OVERLAY_PREVIEW");
            putValue(LimeAction.ICON_NAME_ROLLOVER, "SLIDE_CONTROLS_OVERLAY_PREVIEW_ROLLOVER");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            controller.previewAudio();
        }
    }

    private static final class OverlayIconButton extends IconButton {
        OverlayIconButton(Action action, boolean useHorizontalText, boolean transparentBackground) {
            this(action);
            setHorizontalText(useHorizontalText);
            setUseTransparentBackground(transparentBackground);
        }

        OverlayIconButton(Action action) {
            super(action);
            setForeground(TEXT_FOREGROUND);
            Font f = getFont().deriveFont(getFont().getSize2D() + BASE_TEXT_FONT_SIZE_DELTA);
            UIDefaults defaults = new UIDefaults();
            defaults.put("Button.font", new FontUIResource(f));
            putClientProperty("Nimbus.Overrides.InheritDefaults", Boolean.TRUE);
            putClientProperty("Nimbus.Overrides", defaults);
        }
    }

    private static final class SocialAction extends AbstractAction {
        private final String url;

        SocialAction(String networkName, String url) {
            this(networkName, url, networkName.toUpperCase());
        }

        SocialAction(String networkName, String url, String imageName) {
            this.url = url;
            putValue(Action.SHORT_DESCRIPTION, networkName);
            putValue(LimeAction.ICON_NAME, "SLIDE_CONTROLS_OVERLAY_" + imageName);
            putValue(LimeAction.ICON_NAME_ROLLOVER, "SLIDE_CONTROLS_OVERLAY_" + imageName + "_ROLLOVER");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.openURL(url);
        }
    }

    private static final class PaymentAction extends AbstractAction {
        private final String paymentOptionsUrl;

        PaymentAction(final PaymentOptions paymentOptions, String workTitle) {
            putValue(LimeAction.ICON_NAME, "SLIDE_CONTROLS_OVERLAY_TIP_JAR");
            putValue(LimeAction.ICON_NAME_ROLLOVER, "SLIDE_CONTROLS_OVERLAY_TIP_JAR_ROLLOVER");
            putValue(Action.SHORT_DESCRIPTION, String.format(I18n.tr("Support %s with a tip, donation or voluntary payment"), workTitle));
            String paymentOptionsJSON = UrlUtils.encode(JsonUtils.toJson(paymentOptions).replaceAll("\n", ""));
            paymentOptionsUrl = String.format(
                    "http://www.frostwire.com/tips/?method=%s&po=%s&title=%s",
                    getDefaultPaymentMethod(paymentOptions),
                    paymentOptionsJSON,
                    UrlUtils.encode(workTitle));
        }

        private String getDefaultPaymentMethod(PaymentOptions paymentOptions) {
            String paymentMethod = "";
            if (!StringUtils.isNullOrEmpty(paymentOptions.bitcoin)) {
                paymentMethod = PaymentOptions.PaymentMethod.BITCOIN.toString();
            } else if (!StringUtils.isNullOrEmpty(paymentOptions.paypalUrl)) {
                paymentMethod = PaymentOptions.PaymentMethod.PAYPAL.toString();
            }
            return paymentMethod;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GUIMediator.openURL(paymentOptionsUrl);
        }
    }
}
