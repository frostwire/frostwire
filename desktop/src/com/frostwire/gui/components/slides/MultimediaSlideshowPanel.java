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

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

/**
 * Contains all the SlideshowPanels.
 *
 * @author gubatron
 * @author aldenml
 */
public class MultimediaSlideshowPanel extends JPanel implements SlideshowPanel {
    private static final Logger LOG = Logger.getLogger(MultimediaSlideshowPanel.class);
    private SlideshowListener listener;
    private List<Slide> slides;
    private List<Slide> fallbackSlides;
    private JPanel container;
    private boolean useControls;
    private Timer timer;

    public MultimediaSlideshowPanel(List<Slide> slides) {
        setupUI();
        setup(slides);
    }

    public MultimediaSlideshowPanel(final String url, List<Slide> defaultSlides) {
        fallbackSlides = defaultSlides;
        setupUI();
        new Thread(() -> load(url)).start();
    }

    @Override
    public void setListener(SlideshowListener listener) {
        this.listener = listener;
    }

    @Override
    public int getCurrentSlideIndex() {
        Component[] components = getComponents();
        for (Component c : components) {
            if (c.isVisible() && c instanceof SlidePanel) {
                return ((SlidePanel) c).getIndex();
            }
        }
        return -1;
    }

    @Override
    public void switchToSlide(int slideIndex) {
        if (slideIndex >= 0 && slideIndex < getNumSlides() && getLayout() instanceof CardLayout) {
            ((CardLayout) getLayout()).show(this, String.valueOf(slideIndex));
        }
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    public int getNumSlides() {
        if (slides == null) {
            return 0;
        } else {
            return slides.size();
        }
    }

    private void setupUI() {
        setLayout(new CardLayout());
    }

    private void setup(List<Slide> slides) {
        this.slides = filter(slides);
        GUIMediator.safeInvokeLater(() -> {
            if (MultimediaSlideshowPanel.this.slides != null) {
                List<Slide> slides1 = MultimediaSlideshowPanel.this.slides;
                try {
                    int i = 0;
                    for (Slide s : slides1) {
                        add(new SlidePanel(s, i), String.valueOf(i));
                        i++;
                    }
                    if (container != null && useControls) {
                        container.add(new SlideshowPanelControls(MultimediaSlideshowPanel.this), BorderLayout.PAGE_END);
                    }
                    if (!slides1.isEmpty()) {
                        timer = new Timer("SlideShow Timer");
                        timer.schedule(new SlideSwitcher(), slides1.get(0).duration);
                    }
                } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        });
    }

    private void load(final String url) {
        try {
            HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
            String jsonString = client.get(url);
            if (jsonString != null) {
                final SlideList slideList = JsonUtils.toObject(jsonString, SlideList.class);
                try {
                    setup(slideList.slides);
                } catch (Exception e) {
                    LOG.info("Failed load of Slide Show:" + url, e);
                    setup(fallbackSlides);
                    // nothing happens
                }
            } else {
                setup(fallbackSlides);
            }
        } catch (Exception e) {
            LOG.info("Failed load of Slide Show:" + url, e);
            setup(fallbackSlides);
            // nothing happens
        }
    }

    /*
     * Examples of when this returns true
     * given == lang in app
     * es_ve == es_ve
     * es == es_ve
     * * == es_ve
     */
    private boolean isMessageEligibleForMyLang(String lang) {
        if (lang == null || lang.equals("*"))
            return true;
        String langinapp = ApplicationSettings.getLanguage().toLowerCase();
        if (langinapp.length() > 2) {
            langinapp = langinapp.substring(0, 2);
        }
        return lang.toLowerCase().contains(langinapp);
    }

    private boolean isMessageEligibleForMyOs(String os) {
        if (os == null)
            return true;
        boolean im_mac_msg_for_me = os.contains("mac") && OSUtils.isMacOSX();
        boolean im_windows_msg_for_me = os.contains("windows") && OSUtils.isWindows();
        boolean im_linux_msg_for_me = os.contains("linux") && OSUtils.isLinux();
        return im_mac_msg_for_me || im_windows_msg_for_me || im_linux_msg_for_me;
    }

    private boolean isMessageEligibleForMyVersion(String versions) {
        if (versions == null || versions.equals("*")) {
            return true;
        }
        String frostWireVersion = FrostWireUtils.getFrostWireVersion();
        for (String pattern : versions.split(",")) {
            if (Pattern.matches(pattern, frostWireVersion)) {
                return true; // for-loop-break?
            }
        }
        return false;
    }

    private List<Slide> filter(List<Slide> slides) {
        List<Slide> result = new ArrayList<>(slides.size());
        for (Slide slide : slides) {
            if (isMessageEligibleForMyLang(slide.language) && isMessageEligibleForMyOs(slide.os) && isMessageEligibleForMyVersion(slide.includedVersions)) {
                result.add(slide);
            }
        }
        return result;
    }

    @Override
    public void setupContainerAndControls(JPanel container, boolean useControls) {
        this.container = container;
        this.useControls = useControls;
    }

    private SlidePanel getCurrentSlidePanel() {
        Component[] components = getComponents();
        for (Component c : components) {
            if (c.isVisible() && c instanceof SlidePanel) {
                return ((SlidePanel) c);
            }
        }
        return null;
    }

    class SlideSwitcher extends TimerTask {
        @Override
        public void run() {
            SlidePanel currentSlidePanel = getCurrentSlidePanel();
            if (currentSlidePanel == null || !currentSlidePanel.isOverlayVisible()) {
                if (getLayout() instanceof CardLayout) {
                    ((CardLayout) getLayout()).next(MultimediaSlideshowPanel.this);
                    if (listener != null) {
                        listener.onSlideChanged();
                    }
                }
            }
            if (currentSlidePanel != null) {
                timer.schedule(new SlideSwitcher(), currentSlidePanel.getSlide().duration);
            }
        }
    }
}
